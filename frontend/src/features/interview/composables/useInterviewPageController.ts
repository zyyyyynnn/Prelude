import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { storeToRefs } from 'pinia'
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/features/auth'
import { fetchResumes, uploadResume, type ResumeItem } from '@/features/resume'
import { exportInterviewReportToPdf, parseInterviewReport, renderMarkdown } from '@/features/report'
import { getErrorMessage } from '@/shared/lib/errors'
import { withMinDelay } from '@/shared/lib/utils'
import { useConfirmDialog } from '@/shared/ui/confirm-dialog/useConfirmDialog'
import { usePageNotice } from '@/shared/ui/sonner/usePageNotice'
import { finishInterview, startInterview } from '../api/interview'
import { fetchPositions } from '../api/positions'
import type { PositionTemplate } from '../model/types'
import { useInterviewSessionStore } from '../stores/sessionStore'
import { useInterviewTextStream } from './useInterviewTextStream'
import { useReportListener } from './useReportListener'
import { useVoiceInterviewSocket } from './useVoiceInterviewSocket'

export function useInterviewPageController() {
  const router = useRouter()
  const authStore = useAuthStore()
  const { showNotice } = usePageNotice()
  const confirmDialog = useConfirmDialog()
  const sessionStore = useInterviewSessionStore()
  const { sessions, activeSessionId, replay, reportMarkdown, sessionLoading } =
    storeToRefs(sessionStore)
  const { refreshSessionList, loadSession, getNewAbortSignal, abortActiveStream } = sessionStore

  sessionStore.hydratePreferences()

  const loading = ref(false)
  const creating = ref(false)
  const uploading = ref(false)
  const sending = ref(false)
  const finishing = ref(false)
  const showingReport = ref(false)
  const streamTimeoutId = ref<ReturnType<typeof setTimeout> | null>(null)
  const reconnectingStatus = ref('')
  const resumes = ref<ResumeItem[]>([])
  const positions = ref<PositionTemplate[]>([])
  const selectedResumeId = ref<number | null>(null)
  const selectedPositionId = ref<number | null>(null)
  const uploadDisplayName = ref('未选择任何文件')
  const answer = ref('')

  const messages = computed(() => replay.value?.messages ?? [])
  const jdText = computed(() => replay.value?.jdText ?? '')
  const currentStage = computed(() => replay.value?.currentStage)
  const activeSession = computed(() =>
    sessions.value.find((session) => session.sessionId === activeSessionId.value),
  )
  const targetPosition = computed(
    () => activeSession.value?.targetPosition || activeSession.value?.positionName || '',
  )
  const sessionStatus = computed(() => replay.value?.status || activeSession.value?.status)
  const llmProvider = computed(() => activeSession.value?.llmProvider || 'deepseek')
  const llmModel = computed(() => activeSession.value?.llmModel || 'default')
  const isFinished = computed(
    () => activeSession.value?.status === 'finished' || replay.value?.status === 'finished',
  )
  const isGenerating = computed(
    () => activeSession.value?.status === 'generating' || replay.value?.status === 'generating',
  )
  const hasReport = computed(() => !!reportMarkdown.value || !!replay.value?.summaryReport)
  const parsedReport = computed(() =>
    parseInterviewReport(reportMarkdown.value || replay.value?.summaryReport || ''),
  )
  const renderedReport = computed(() =>
    parsedReport.value.kind === 'markdown' ? renderMarkdown(parsedReport.value.markdown) : '',
  )

  const {
    appendMessage,
    ensureAssistantPlaceholder,
    appendAssistantDelta,
    streamReply,
    cleanupTextStream,
  } = useInterviewTextStream({
    activeSessionId,
    replay,
    reportMarkdown,
    reconnectingStatus,
    streamTimeoutId,
    authToken: () => authStore.token,
    getNewAbortSignal,
    abortActiveStream,
    loadSession,
    refreshSessionList,
    showNotice,
    showReport: () => {
      showingReport.value = true
    },
    async onAuthExpired() {
      authStore.clearSession()
      await router.replace('/login?reason=expired')
    },
  })

  const { startListeningReport, stopListeningReport } = useReportListener({
    activeSessionId,
    isGenerating,
    replay,
    sessions,
    reportMarkdown,
    showingReport,
    authToken: () => authStore.token,
    loadSession,
    showNotice,
  })

  const isVoiceMode = ref(false)
  const {
    voiceStatus,
    incomingAudioChunk,
    closeVoiceSocket,
    handleAudioChunk,
    handleStartRecording,
    handleStopRecording,
    handlePlayStatus,
  } = useVoiceInterviewSocket({
    activeSessionId,
    isVoiceMode,
    replay,
    authToken: () => authStore.token,
    showNotice,
    appendMessage,
    ensureAssistantPlaceholder,
    appendAssistantDelta,
  })

  function setResumeDefaults(items: ResumeItem[]) {
    if (!selectedResumeId.value || !items.some((item) => item.id === selectedResumeId.value)) {
      selectedResumeId.value = items[0]?.id ?? null
    }
    uploadDisplayName.value =
      items.find((item) => item.id === selectedResumeId.value)?.fileName || '未选择任何文件'
  }

  function setPositionDefaults(items: PositionTemplate[]) {
    if (!selectedPositionId.value || !items.some((item) => item.id === selectedPositionId.value)) {
      selectedPositionId.value = items[0]?.id ?? null
    }
  }

  async function loadDashboard() {
    loading.value = true
    try {
      const [resumeList, positionList] = await Promise.all([
        fetchResumes(),
        fetchPositions(),
        refreshSessionList(),
      ])
      resumes.value = resumeList
      positions.value = positionList
      setResumeDefaults(resumeList)
      setPositionDefaults(positionList)

      if (
        activeSessionId.value &&
        sessions.value.some((item) => item.sessionId === activeSessionId.value)
      ) {
        await loadSession(activeSessionId.value, true)
        await restoreInterruptedStream()
      }
    } catch (error) {
      showNotice(getErrorMessage(error), 'error')
    } finally {
      loading.value = false
    }
  }

  async function restoreInterruptedStream() {
    const snapshot = sessionStorage.getItem('interview-stream-snapshot')
    if (!snapshot) return
    try {
      const parsed = JSON.parse(snapshot)
      if (parsed.sessionId !== activeSessionId.value) return
      const shouldResume = await confirmDialog.confirm({
        title: '恢复 AI 回复',
        message: '检测到上次未完成的 AI 回复，是否恢复？',
        confirmText: '恢复',
        cancelText: '忽略',
      })
      if (shouldResume && replay.value) {
        const target = replay.value.messages.find((message) => message.id === parsed.messageId)
        if (target) {
          target.content = parsed.content
        } else {
          replay.value.messages.push({
            id: parsed.messageId,
            role: 'assistant',
            content: parsed.content,
            createdAt: new Date(parsed.timestamp).toISOString(),
          })
        }
      }
    } catch {
      // Invalid snapshots are discarded below.
    } finally {
      sessionStorage.removeItem('interview-stream-snapshot')
    }
  }

  async function handleUpload(file: File) {
    uploading.value = true
    try {
      const result = await withMinDelay(uploadResume(file))
      const updated = await fetchResumes()
      resumes.value = updated
      selectedResumeId.value = result.resumeId
      uploadDisplayName.value =
        updated.find((item) => item.id === result.resumeId)?.fileName || file.name
      showNotice('简历已上传', 'success')
    } catch (error) {
      showNotice(getErrorMessage(error), 'error')
    } finally {
      uploading.value = false
    }
  }

  async function createNewInterview(jdText = '', requestedModel?: string) {
    if (!selectedResumeId.value || !selectedPositionId.value) {
      showNotice('请选择简历和岗位', 'warning')
      return
    }
    if (creating.value || loading.value) return

    creating.value = true
    try {
      const result = await withMinDelay(
        startInterview({
          resumeId: selectedResumeId.value,
          positionId: selectedPositionId.value,
          jdText: jdText || undefined,
          llmModel: requestedModel || undefined,
        }),
      )
      await refreshSessionList()
      await loadSession(result.sessionId, true)
      answer.value = ''
      showingReport.value = false
      showNotice('面试已创建', 'success')
    } catch (error) {
      showNotice(getErrorMessage(error), 'error')
    } finally {
      creating.value = false
    }
  }

  async function handleSend() {
    const content = answer.value.trim()
    if (!content) return
    sending.value = true
    try {
      if (await streamReply(content, false)) answer.value = ''
    } finally {
      sending.value = false
    }
  }

  async function handleFinish() {
    if (!activeSessionId.value) return
    if (currentStage.value !== 'closing' || isFinished.value) {
      showNotice('仅在处于收尾阶段且会话未结束时，才能生成报告', 'warning')
      return
    }

    abortActiveStream()
    if (streamTimeoutId.value !== null) {
      clearTimeout(streamTimeoutId.value)
      streamTimeoutId.value = null
    }
    sending.value = false
    reconnectingStatus.value = ''
    finishing.value = true
    try {
      const result = await withMinDelay(finishInterview(activeSessionId.value))
      const target = sessions.value.find((item) => item.sessionId === activeSessionId.value)
      if (target) target.status = result.status || 'generating'
      if (replay.value) replay.value.status = result.status || 'generating'
      showNotice('已开始生成报告，请稍候', 'success')
    } catch (error) {
      showNotice(getErrorMessage(error), 'error')
    } finally {
      finishing.value = false
    }
  }

  const exportingPdf = ref(false)

  async function handleExportPdf(element: HTMLElement | null) {
    if (!element) return
    exportingPdf.value = true
    try {
      const title = targetPosition.value ? `${targetPosition.value}-面试评估报告` : '面试评估报告'
      await exportInterviewReportToPdf(element, `${title}.pdf`)
      showNotice('PDF 导出成功', 'success')
    } catch (error) {
      showNotice('PDF 导出失败，请重试', 'error')
      console.error(error)
    } finally {
      exportingPdf.value = false
    }
  }

  watch(activeSessionId, (newId, oldId) => {
    if (newId !== oldId) {
      showingReport.value = false
      answer.value = ''
    }
  })
  watch(replay, (value) => {
    if (!value) return
    if (value.resumeId) selectedResumeId.value = value.resumeId
    if (value.positionId) selectedPositionId.value = value.positionId
  })
  watch(
    () => replay.value?.summaryReport,
    (value) => {
      if (value && !reportMarkdown.value) reportMarkdown.value = value
      if (value) showingReport.value = true
    },
  )
  watch(
    isGenerating,
    (generating) => {
      if (generating && activeSessionId.value) void startListeningReport(activeSessionId.value)
      else stopListeningReport()
    },
    { immediate: true },
  )
  watch(activeSessionId, (newId) => {
    stopListeningReport()
    if (isGenerating.value && newId) void startListeningReport(newId)
  })

  onMounted(() => void loadDashboard())
  onBeforeUnmount(() => {
    cleanupTextStream()
    abortActiveStream()
    closeVoiceSocket()
    stopListeningReport()
  })

  return {
    activeSessionId,
    sessionLoading,
    loading,
    resumes,
    positions,
    selectedResumeId,
    selectedPositionId,
    answer,
    uploading,
    uploadDisplayName,
    sending,
    creating,
    finishing,
    showingReport,
    messages,
    jdText,
    reconnectingStatus,
    currentStage,
    targetPosition,
    sessionStatus,
    hasReport,
    isFinished,
    isGenerating,
    llmProvider,
    llmModel,
    parsedReport,
    renderedReport,
    isVoiceMode,
    voiceStatus,
    incomingAudioChunk,
    exportingPdf,
    handleUpload,
    createNewInterview,
    handleSend,
    handleFinish,
    handleExportPdf,
    handleAudioChunk,
    handleStartRecording,
    handleStopRecording,
    handlePlayStatus,
  }
}
