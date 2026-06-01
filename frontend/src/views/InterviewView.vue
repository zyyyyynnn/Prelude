<script setup lang="ts">
import { computed, onMounted, onBeforeUnmount, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { fetchPositions } from '../api/auth'
import type { PositionTemplate, ResumeItem, InterviewMessageRecord } from '../api/contracts'
import { startInterview, streamInterviewChat, finishInterview } from '../api/interview'
import { usePageNotice } from '../composables/usePageNotice'
import { fetchResumes, uploadResume } from '../api/resume'
import { useAuthStore } from '../stores/auth'
import { renderMarkdown } from '../utils/markdown'
import { useInterviewWorkspace } from '../composables/useInterviewWorkspace'
import WorkspaceHeader from '../components/workspace/WorkspaceHeader.vue'
import MessageThread from '../components/workspace/MessageThread.vue'
import InterviewComposer from '../components/workspace/InterviewComposer.vue'

const router = useRouter()
const authStore = useAuthStore()
const { showNotice } = usePageNotice()

const {
  sessions,
  activeSessionId,
  replay,
  reportMarkdown,
  sessionLoading,
  primarySessionList,
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
const llmProvider = computed(() => activeSession.value?.llmProvider || 'deepseek')
const llmModel = computed(() => activeSession.value?.llmModel || 'default')
const isFinished = computed(() => activeSession.value?.status === 'finished' || replay.value?.status === 'finished')

const hasReport = computed(() => !!reportMarkdown.value || !!replay.value?.summaryReport)
const renderedReport = computed(() => renderMarkdown(reportMarkdown.value || replay.value?.summaryReport || ''))



function getErrorMessage(error: unknown) {
  return error instanceof Error ? error.message : '请求失败'
}

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
    } else if (primarySessionList.value[0]) {
      await loadSession(primarySessionList.value[0].sessionId, true)
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
    const result = await uploadResume(file)
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

async function createNewInterview() {
  if (!selectedResumeId.value || !selectedPositionId.value) {
    showNotice('请选择简历和岗位', 'warning')
    return
  }
  if (creating.value || loading.value) {
    return
  }

  creating.value = true
  try {
    const result = await startInterview({
      resumeId: selectedResumeId.value,
      positionId: selectedPositionId.value,
    })
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

function appendMessage(message: InterviewMessageRecord) {
  if (!replay.value) return
  replay.value.messages = [...replay.value.messages, message]
}

function removeMessageById(id: number | null) {
  if (!replay.value || id == null) return
  replay.value.messages = replay.value.messages.filter((message) => message.id !== id)
}

function ensureAssistantPlaceholder(id: number) {
  if (!replay.value || replay.value.messages.some((message) => message.id === id)) return
  appendMessage({
    id,
    role: 'assistant',
    content: '',
    createdAt: new Date().toISOString(),
  })
}

function appendAssistantDelta(id: number, delta: string) {
  if (!replay.value) return
  const list = [...replay.value.messages]
  const target = list.find((message) => message.id === id)

  if (!target || target.role !== 'assistant') {
    list.push({
      id,
      role: 'assistant',
      content: delta,
      createdAt: new Date().toISOString(),
    })
  } else {
    target.content += delta
  }

  replay.value.messages = list
}

function clearAssistantPlaceholder(id: number) {
  if (!replay.value) return
  const list = [...replay.value.messages]
  const target = list.find((message) => message.id === id)
  if (target && target.role === 'assistant') {
    target.content = ''
    replay.value.messages = list
  }
}

async function streamReply(content: string, autoStart = false) {
  if (!activeSessionId.value) {
    showNotice('请先创建或选择一场面试', 'warning')
    return false
  }
  if (!replay.value) {
    await loadSession(activeSessionId.value, true)
  }

  const optimisticUserId = autoStart ? null : Date.now()
  const assistantMessageId = Date.now() + 1

  if (!autoStart) {
    appendMessage({
      id: optimisticUserId!,
      role: 'user',
      content,
      createdAt: new Date().toISOString(),
    })
  }
  ensureAssistantPlaceholder(assistantMessageId)

  const signal = getNewAbortSignal()

  streamTimeoutId.value = setTimeout(() => {
    abortActiveStream()
    showNotice('网络或模型响应超时，已强制断开，请重试', 'error')
    sending.value = false
  }, 120000)

  try {
    reconnectingStatus.value = ''
    await streamInterviewChat(
      authStore.token,
      activeSessionId.value,
      { content },
      autoStart,
      {
        onChunk(chunk) {
          appendAssistantDelta(assistantMessageId, chunk)
        },
        onEvent(event) {
          if (event.eventName === 'status') {
            if (event.data === 'checking') {
              reconnectingStatus.value = '连接异常，正在核对会话状态...'
            } else if (event.data.startsWith('reconnecting_')) {
              const attempt = event.data.split('_')[1]
              reconnectingStatus.value = `连接已断开，正在尝试第 ${attempt} 次重连...`
              clearAssistantPlaceholder(assistantMessageId)
            }
          } else if (event.eventName === 'sync') {
            const serverMsgs = JSON.parse(event.data)
            if (replay.value) {
              replay.value.messages = serverMsgs
            }
            reconnectingStatus.value = ''
          } else if (event.eventName === 'report_ready') {
            reportMarkdown.value = event.data
            if (replay.value) {
              replay.value.summaryReport = event.data
            }
            showingReport.value = true
          }
        }
      },
      signal,
    )
    reconnectingStatus.value = ''
    await refreshSessionList()
    await loadSession(activeSessionId.value, true)
    return true
  } catch (error) {
    reconnectingStatus.value = ''
    if (error instanceof Error && error.name === 'AbortError') {
      return false
    }
    removeMessageById(assistantMessageId)
    removeMessageById(optimisticUserId)
    const message = getErrorMessage(error)
    if (message.includes('登录已失效')) {
      authStore.clearSession()
      await router.replace('/login?reason=expired')
      return false
    }
    showNotice(message, 'error')
    return false
  } finally {
    if (streamTimeoutId.value !== null) {
      clearTimeout(streamTimeoutId.value)
      streamTimeoutId.value = null
    }
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
    sending.value = false
  }
}



async function handleFinish() {
  if (!activeSessionId.value) return
  if (currentStage.value !== 'closing' || isFinished.value) {
    showNotice('仅在处于收尾阶段且会话未结束时，才能生成报告', 'warning')
    return
  }
  finishing.value = true
  try {
    const result = await finishInterview(activeSessionId.value)
    reportMarkdown.value = result.summaryReport || ''
    await refreshSessionList()
    const target = sessions.value.find((item) => item.sessionId === activeSessionId.value)
    if (target) {
      target.summaryReport = result.summaryReport
      target.status = result.status || 'finished'
    }
    if (replay.value) {
      replay.value.summaryReport = result.summaryReport || ''
    }
    if (activeSessionId.value) {
      await loadSession(activeSessionId.value, true)
    }
    showNotice('报告已生成', 'success')
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

watch(() => replay.value?.summaryReport, (val) => {
  if (val && !reportMarkdown.value) {
    reportMarkdown.value = val
  }
  if (val) {
    showingReport.value = true
  }
})

onMounted(() => {
  void loadDashboard()
})

onBeforeUnmount(() => {
  if (streamTimeoutId.value !== null) {
    clearTimeout(streamTimeoutId.value)
    streamTimeoutId.value = null
  }
  abortActiveStream()
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
        />
      </div>
    </div>

    <!-- Active Session View -->
    <div v-else-if="activeSessionId" class="workspace-active">
      <WorkspaceHeader
        :active-session-id="activeSessionId"
        :target-position="targetPosition"
        :current-stage="currentStage"
        :sending="sending"
        :finishing="finishing"
        :has-report="hasReport"
        :showing-report="showingReport"
        :is-finished="isFinished"
        @finish="handleFinish"
        @toggle-report="showingReport = $event"
      />

      <div class="workspace-active__main">
        <div v-if="showingReport" class="workspace-report scrollable">
          <div class="report-content">
            <div class="markdown-surface markdown-surface--paper">
              <div class="markdown-body" v-html="renderedReport" />
            </div>
          </div>
        </div>
        
        <template v-else>
          <MessageThread :messages="messages" :reconnecting-status="reconnectingStatus" />
          
          <div class="workspace-composer-fixed">
            <InterviewComposer 
              :is-centered="false"
              :active-session-id="activeSessionId"
              :resumes="resumes"
              :positions="positions"
              :selected-resume-id="selectedResumeId"
              :selected-position-id="selectedPositionId"
              :llm-provider="llmProvider"
              :llm-model="llmModel"
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
  gap: 40px;
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
  box-shadow: 0 12px 24px color-mix(in srgb, var(--color-brand) 8%, transparent), 0 2px 6px color-mix(in srgb, var(--color-brand) 4%, transparent);
}
.workspace-composer-fixed {
  position: absolute;
  bottom: 0;
  left: 0;
  right: 0;
  padding: 16px var(--spacing-2xl) 24px;
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
</style>
