<script setup lang="ts">
import { computed, onMounted, onBeforeUnmount, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { fetchPositions } from '../api/auth'
import type { PositionTemplate, ResumeItem } from '../api/contracts'
import { startInterview, finishInterview } from '../api/interview'
import { getErrorMessage } from '../utils/errors'
import { usePageNotice } from '../composables/usePageNotice'
import { fetchResumes, uploadResume } from '../api/resume'
import { useAuthStore } from '../stores/auth'
import { renderMarkdown } from '../utils/markdown'
import { useInterviewWorkspace } from '../composables/useInterviewWorkspace'
import { useInterviewTextStream } from '../composables/useInterviewTextStream'
import { useReportListener } from '../composables/useReportListener'
import { useVoiceInterviewSocket } from '../composables/useVoiceInterviewSocket'
import WorkspaceHeader from '../components/workspace/WorkspaceHeader.vue'
import MessageThread from '../components/workspace/MessageThread.vue'
import InterviewComposer from '../components/workspace/InterviewComposer.vue'
import { exportToPdf } from '../utils/pdf'
import { withMinDelay } from '../lib/utils'
import { useConfirmDialog } from '../composables/useConfirmDialog'
import RoseThree from '../components/ui/loader/RoseThree.vue'

const router = useRouter()
const authStore = useAuthStore()
const { showNotice } = usePageNotice()
const confirmDialog = useConfirmDialog()

const emit = defineEmits<{ 
  // 该事件会向上冒泡，最终由 App.vue 的 handleOpenSettings 接管处理
  (e: 'open-global-settings', tab?: 'profile' | 'theme' | 'llm'): void
}>()

const {
  sessions,
  activeSessionId,
  replay,
  reportMarkdown,
  sessionLoading,
  refreshSessionList,
  loadSession,
  getNewAbortSignal,
  abortActiveStream
} = useInterviewWorkspace()

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
const currentStage = computed(() => replay.value?.currentStage)
const activeSession = computed(() => sessions.value.find(s => s.sessionId === activeSessionId.value))
const targetPosition = computed(() => activeSession.value?.targetPosition || activeSession.value?.positionName || '')
const sessionStatus = computed(() => replay.value?.status || activeSession.value?.status)
const llmProvider = computed(() => activeSession.value?.llmProvider || 'deepseek')
const llmModel = computed(() => activeSession.value?.llmModel || 'default')
const isFinished = computed(() => activeSession.value?.status === 'finished' || replay.value?.status === 'finished')
const isGenerating = computed(() => activeSession.value?.status === 'generating' || replay.value?.status === 'generating')

const hasReport = computed(() => !!reportMarkdown.value || !!replay.value?.summaryReport)
const renderedReport = computed(() => renderMarkdown(reportMarkdown.value || replay.value?.summaryReport || ''))

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

const {
  startListeningReport,
  stopListeningReport,
} = useReportListener({
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
  uploadDisplayName.value = items.find((item) => item.id === selectedResumeId.value)?.fileName || '未选择任何文件'
}

function setPositionDefaults(items: PositionTemplate[]) {
  if (!selectedPositionId.value || !items.some((item) => item.id === selectedPositionId.value)) {
    selectedPositionId.value = items[0]?.id ?? null
  }
}

async function loadDashboard() {
  loading.value = true
  try {
    const [resumeList, positionList] = await Promise.all([fetchResumes(), fetchPositions(), refreshSessionList()])
    resumes.value = resumeList
    positions.value = positionList
    setResumeDefaults(resumeList)
    setPositionDefaults(positionList)

    if (activeSessionId.value && sessions.value.some((item) => item.sessionId === activeSessionId.value)) {
      await loadSession(activeSessionId.value, true)

      const snapshot = sessionStorage.getItem('interview-stream-snapshot')
      if (snapshot) {
        try {
          const parsed = JSON.parse(snapshot)
          if (parsed.sessionId === activeSessionId.value) {
            const doResume = await confirmDialog.confirm({
              title: '恢复 AI 回复',
              message: '检测到上次未完成的 AI 回复，是否恢复？',
              confirmText: '恢复',
              cancelText: '忽略',
            })
            if (doResume && replay.value) {
              const target = replay.value.messages.find(m => m.id === parsed.messageId)
              if (target) {
                target.content = parsed.content
              } else {
                replay.value.messages.push({
                  id: parsed.messageId,
                  role: 'assistant',
                  content: parsed.content,
                  createdAt: new Date(parsed.timestamp).toISOString()
                })
              }
            }
            sessionStorage.removeItem('interview-stream-snapshot')
          }
        } catch (e) {
          sessionStorage.removeItem('interview-stream-snapshot')
        }
      }
    }
  } catch (error) {
    showNotice(getErrorMessage(error), 'error')
  } finally {
    loading.value = false
  }
}

async function handleUpload(file: File) {
  uploading.value = true
  try {
    const result = await withMinDelay(uploadResume(file))
    const updated = await fetchResumes()
    resumes.value = updated
    selectedResumeId.value = result.resumeId
    uploadDisplayName.value = updated.find((item) => item.id === result.resumeId)?.fileName || file.name
    showNotice('简历已上传', 'success')
  } catch (error) {
    showNotice(getErrorMessage(error), 'error')
  } finally {
    uploading.value = false
  }
}

async function createNewInterview(jdText = '', llmModel?: string) {
  if (!selectedResumeId.value || !selectedPositionId.value) {
    showNotice('请选择简历和岗位', 'warning')
    return
  }
  if (creating.value || loading.value) {
    return
  }

  creating.value = true
  try {
    const result = await withMinDelay(startInterview({
      resumeId: selectedResumeId.value,
      positionId: selectedPositionId.value,
      jdText: jdText || undefined,
      llmModel: llmModel || undefined,
    }))
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
    const success = await streamReply(content, false)
    if (success) {
      answer.value = ''
    }
  } finally {
    // 统一清理：无论成功/失败/中止，sending 仅在此处清除
    sending.value = false
  }
}



async function handleFinish() {
  if (!activeSessionId.value) return
  if (currentStage.value !== 'closing' || isFinished.value) {
    showNotice('仅在处于收尾阶段且会话未结束时，才能生成报告', 'warning')
    return
  }
  // 1. 强制中止正在进行的 chat SSE 流，消除并发竞争
  abortActiveStream()
  // 2. 清除 watchdog 定时器，防止残留 timeout 干扰
  if (streamTimeoutId.value !== null) {
    clearTimeout(streamTimeoutId.value)
    streamTimeoutId.value = null
  }
  // 3. 重置 sending 状态（流已被中止，发送标记失效）
  sending.value = false
  reconnectingStatus.value = ''

  finishing.value = true
  try {
    const result = await withMinDelay(finishInterview(activeSessionId.value))
    const target = sessions.value.find((item) => item.sessionId === activeSessionId.value)
    if (target) {
      target.status = result.status || 'generating'
    }
    if (replay.value) {
      replay.value.status = result.status || 'generating'
    }
    showNotice('已开始生成报告，请稍候', 'success')
  } catch (error) {
    showNotice(getErrorMessage(error), 'error')
  } finally {
    finishing.value = false
  }
}

watch(activeSessionId, (newId, oldId) => {
  if (newId !== oldId) {
    showingReport.value = false
    answer.value = ''
  }
})

watch(replay, (newVal) => {
  if (newVal) {
    if (newVal.resumeId) selectedResumeId.value = newVal.resumeId
    if (newVal.positionId) selectedPositionId.value = newVal.positionId
  }
})

watch(() => replay.value?.summaryReport, (val) => {
  if (val && !reportMarkdown.value) {
    reportMarkdown.value = val
  }
  if (val) {
    showingReport.value = true
  }
})

watch(isGenerating, (generating) => {
  if (generating && activeSessionId.value) {
    void startListeningReport(activeSessionId.value)
  } else {
    stopListeningReport()
  }
}, { immediate: true })

watch(activeSessionId, (newId) => {
  stopListeningReport()
  if (isGenerating.value && newId) {
    void startListeningReport(newId)
  }
})

const reportRef = ref<HTMLElement | null>(null)
const exportingPdf = ref(false)

async function handleExportPdf() {
  if (!reportRef.value) return
  exportingPdf.value = true
  try {
    const title = targetPosition.value ? `${targetPosition.value}-面试评估报告` : '面试评估报告'
    await exportToPdf(reportRef.value, `${title}.pdf`)
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

onMounted(() => {
  void loadDashboard()
})

onBeforeUnmount(() => {
  cleanupTextStream()
  abortActiveStream()
  closeVoiceSocket()
  stopListeningReport()
})
</script>

<template>
  <div class="interview-workspace">
    <!-- Empty State -->
    <div v-if="!activeSessionId && !sessionLoading" class="workspace-empty">
      <div class="workspace-empty__content">
        <h1 class="workspace-empty__title">准备开始一场沉浸式模拟面试</h1>
        <InterviewComposer 
          :is-centered="true"
          :resumes="resumes"
          :positions="positions"
          :selected-resume-id="selectedResumeId"
          :selected-position-id="selectedPositionId"
          v-model="answer"
          :uploading="uploading"
          :upload-display-name="uploadDisplayName"
          :sending="sending"
          :creating="creating"
          @update:selected-resume-id="id => selectedResumeId = id"
          @update:selected-position-id="id => selectedPositionId = id"
          @upload="handleUpload"
          @start="createNewInterview"
          @send="handleSend"
          @open-global-settings="(tab) => emit('open-global-settings', tab)"
        />
      </div>
    </div>

    <!-- Active Session View -->
    <div v-else-if="activeSessionId" class="workspace-active">
      <WorkspaceHeader
        :active-session-id="activeSessionId"
        :target-position="targetPosition"
        :current-stage="currentStage"
        :session-status="sessionStatus"
        :sending="sending"
        :finishing="finishing"
        :has-report="hasReport"
        :showing-report="showingReport"
        :is-finished="isFinished"
        :exporting="exportingPdf"
        @finish="handleFinish"
        @toggle-report="showingReport = $event"
        @export-pdf="handleExportPdf"
      />

      <div class="workspace-active__main">
        <div v-if="isGenerating" class="workspace-generating scrollable">
          <div class="generating-card">
            <RoseThree class="generating-rose" :speed-multiplier="0.9" />
            <h3 class="generating-title">AI 评估报告生成中...</h3>
            <p class="generating-subtitle">我们正在整理您的答题表现，并调用 LLM-as-Judge 进行深度诊断，请稍候（约需 10-15 秒）</p>
            <div class="generating-progress">
              <div class="progress-bar-ind"></div>
            </div>
          </div>
        </div>

        <div v-else-if="showingReport" class="workspace-report scrollable">
          <div class="report-content">
            <div class="markdown-surface markdown-surface--paper" ref="reportRef">
              <div class="markdown-body" v-html="renderedReport" />
            </div>
          </div>
        </div>
        
        <template v-else>
          <MessageThread :messages="messages" :reconnecting-status="reconnectingStatus" />
          
          <div class="workspace-composer-fixed">
            <InterviewComposer 
              :disabled="isFinished"
              :is-centered="false"
              :active-session-id="activeSessionId"
              :resumes="resumes"
              :positions="positions"
              :selected-resume-id="selectedResumeId"
              :selected-position-id="selectedPositionId"
              :llm-provider="llmProvider"
              :llm-model="llmModel"
              :jd-text="replay?.jdText"
              v-model="answer"
              :uploading="uploading"
              :upload-display-name="uploadDisplayName"
              :sending="sending"
              :creating="creating"
              :is-voice-mode="isVoiceMode"
              :voice-status="voiceStatus"
              :incoming-audio="incomingAudioChunk"
              @update:is-voice-mode="v => isVoiceMode = v"
              @update:selected-resume-id="id => selectedResumeId = id"
              @update:selected-position-id="id => selectedPositionId = id"
              @upload="handleUpload"
              @start="createNewInterview"
              @send="handleSend"
              @voice-audio-chunk="handleAudioChunk"
              @voice-start-recording="handleStartRecording"
              @voice-stop-recording="handleStopRecording"
              @voice-play-status="handlePlayStatus"
              @open-global-settings="(tab) => emit('open-global-settings', tab)"
            />
          </div>
        </template>
      </div>
    </div>
    
    <div v-else-if="sessionLoading" class="workspace-loading">
      加载中...
    </div>
  </div>
</template>

<style scoped>
.interview-workspace {
  flex: 1;
  display: flex;
  flex-direction: column;
  height: 100%;
  min-height: 0;
}
.workspace-empty {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: var(--spacing-2xl);
}
.workspace-empty__content {
  width: 100%;
  max-width: 800px;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: var(--spacing-2xl);
}
.workspace-empty__title {
  font-family: var(--font-serif);
  font-size: 32px;
  font-weight: 500;
  color: var(--color-text-primary);
  margin: 0;
  text-align: center;
}
.workspace-active {
  flex: 1;
  display: flex;
  flex-direction: column;
  height: 100%;
  overflow: hidden;
  background: var(--color-bg);
  min-height: 0;
}
.workspace-active__main {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  position: relative;
  min-height: 0;
}
.workspace-report {
  flex: 1;
  display: flex;
  padding: 64px var(--spacing-2xl);
  overflow-y: auto;
  scrollbar-gutter: stable;
  align-items: flex-start;
  justify-content: center;
  min-height: 0;
}
.report-content {
  flex: 1;
  max-width: 800px;
}
.markdown-surface--paper {
  background: var(--color-surface);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  padding: var(--spacing-2xl);
  box-shadow: var(--shadow-whisper);
}
.workspace-composer-fixed {
  position: absolute;
  bottom: 0;
  left: 0;
  right: 0;
  padding: var(--spacing-md) var(--spacing-2xl) var(--spacing-lg);
  background: transparent;
  z-index: 10;
  pointer-events: none;
}
.workspace-composer-fixed > * {
  pointer-events: auto;
}
.workspace-loading {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--color-text-tertiary);
}
.voice-mode-container {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: var(--spacing-sm);
  width: 100%;
}
.voice-close-btn {
  font-size: var(--font-size-sm);
  padding: 0 var(--spacing-md);
  height: var(--ui-height-sm);
  border-radius: var(--radius-md);
  border: 1px solid var(--color-border);
  background: var(--color-surface);
  color: var(--color-text-secondary);
  cursor: pointer;
  transition: var(--motion-transition-surface);
}
.voice-close-btn:hover {
  background-color: var(--color-surface-hover);
  color: var(--color-text-primary);
}

.workspace-generating {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: var(--spacing-xl);
  background: var(--color-surface);
}
.generating-card {
  display: flex;
  flex-direction: column;
  align-items: center;
  max-width: 480px;
  padding: var(--spacing-2xl);
  background: var(--color-surface-hover);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-lg);
  box-shadow: 0 8px 30px color-mix(in srgb, var(--color-text-primary) 5%, transparent);
  text-align: center;
}
.generating-rose {
  width: calc(var(--ui-height-base) * 2);
  height: calc(var(--ui-height-base) * 2);
  color: var(--rose-three-color);
  margin-bottom: var(--spacing-lg);
}
.generating-title {
  font-family: var(--font-serif);
  font-size: 20px;
  font-weight: 500;
  color: var(--color-text-primary);
  margin-bottom: var(--spacing-xs);
}
.generating-subtitle {
  font-size: var(--font-size-sm);
  color: var(--color-text-secondary);
  margin-bottom: var(--spacing-lg);
  line-height: 1.6;
}
.generating-progress {
  width: 100%;
  height: 4px;
  background: var(--color-border);
  border-radius: 2px;
  overflow: hidden;
  position: relative;
}
.progress-bar-ind {
  width: 50%;
  height: 100%;
  background: var(--color-brand);
  border-radius: 2px;
  position: absolute;
  left: -50%;
  animation: progress-ind-anim var(--motion-duration-slow) infinite var(--motion-ease-standard);
}
@keyframes progress-ind-anim {
  0% { left: -50%; }
  100% { left: 100%; }
}
</style>
