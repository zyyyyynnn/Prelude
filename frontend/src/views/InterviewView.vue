<script setup lang="ts">
import { computed, onMounted, onBeforeUnmount, ref, watch, nextTick } from 'vue'
import { useRouter } from 'vue-router'
import { fetchPositions } from '../api/auth'
import type { PositionTemplate, ResumeItem, InterviewMessageRecord } from '../api/contracts'
import { startInterview, streamInterviewChat, finishInterview } from '../api/interview'
import { usePageNotice } from '../composables/usePageNotice'
import { fetchResumes, uploadResume } from '../api/resume'
import { useAuthStore } from '../stores/auth'
import { renderMarkdown } from '../utils/markdown'
import LlmSettingsDialog from '../components/workspace/LlmSettingsDialog.vue'
import { useInterviewWorkspace } from '../composables/useInterviewWorkspace'
import WorkspaceHeader from '../components/workspace/WorkspaceHeader.vue'
import MessageThread from '../components/workspace/MessageThread.vue'
import InterviewComposer from '../components/workspace/InterviewComposer.vue'
import { exportToPdf } from '../utils/pdf'

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
const showLlmSettings = ref(false)

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
const isGenerating = computed(() => activeSession.value?.status === 'generating' || replay.value?.status === 'generating')

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

async function createNewInterview(jdText = '') {
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
      jdText: jdText || undefined,
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
          } else if (event.eventName === 'judge') {
            const data = JSON.parse(event.data)
            if (replay.value) {
              const userMsgs = replay.value.messages.filter(m => m.role === 'user')
              if (userMsgs.length > 0) {
                const lastUserMsg = userMsgs[userMsgs.length - 1]
                lastUserMsg.score = data.score
                lastUserMsg.hint = data.hint
              }
            }
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



const listenController = ref<AbortController | null>(null)

function stopListening() {
  if (listenController.value) {
    listenController.value.abort()
    listenController.value = null
  }
}

function parseSseEvent(rawEvent: string) {
  const lines = rawEvent.split('\n')
  const eventName = lines.find((line) => line.startsWith('event:'))?.slice(6).trim() || 'message'
  const data = lines
    .filter((line) => line.startsWith('data:'))
    .map((line) => line.slice(5).trimStart())
    .join('\n')

  return { eventName, data }
}

async function startListeningReport(sessionId: number) {
  stopListening()
  const controller = new AbortController()
  listenController.value = controller
  
  const token = authStore.token
  const apiBaseUrl = import.meta.env.VITE_API_BASE_URL || '/api'
  
  try {
    const response = await fetch(`${apiBaseUrl}/interview/${sessionId}/listen`, {
      headers: {
        Authorization: `Bearer ${token}`,
      },
      signal: controller.signal,
    })
    
    if (!response.ok || !response.body) {
      throw new Error('监听接口连接失败')
    }
    
    const reader = response.body.getReader()
    const decoder = new TextDecoder()
    let buffer = ''
    
    while (true) {
      const { done, value } = await reader.read()
      if (done) break
      
      buffer += decoder.decode(value, { stream: true }).replace(/\r/g, '')
      let boundary = buffer.indexOf('\n\n')
      while (boundary !== -1) {
        const rawEvent = buffer.slice(0, boundary).trim()
        buffer = buffer.slice(boundary + 2)
        if (rawEvent) {
          const parsed = parseSseEvent(rawEvent)
          if (parsed.eventName === 'report_ready') {
            reportMarkdown.value = parsed.data
            if (replay.value) {
              replay.value.summaryReport = parsed.data
              replay.value.status = 'finished'
            }
            const sIdx = sessions.value.findIndex(s => s.sessionId === sessionId)
            if (sIdx !== -1) {
              sessions.value[sIdx].summaryReport = parsed.data
              sessions.value[sIdx].status = 'finished'
            }
            showingReport.value = true
            showNotice('评估报告已生成并就绪', 'success')
            stopListening()
            await loadSession(sessionId, true)
          } else if (parsed.eventName === 'fallback') {
            showNotice(parsed.data || '已为您自动切换至备用通道', 'warning')
          } else if (parsed.eventName === 'error') {
            showNotice(parsed.data || '报告生成失败', 'error')
            if (replay.value) {
              replay.value.status = 'ongoing'
            }
            const sIdx = sessions.value.findIndex(s => s.sessionId === sessionId)
            if (sIdx !== -1) {
              sessions.value[sIdx].status = 'ongoing'
            }
            stopListening()
          }
        }
        boundary = buffer.indexOf('\n\n')
      }
    }
  } catch (error: any) {
    if (error.name === 'AbortError') return
    console.error('Listen stream error:', error)
    if (activeSessionId.value === sessionId && isGenerating.value) {
      setTimeout(() => {
        if (activeSessionId.value === sessionId && isGenerating.value) {
          void startListeningReport(sessionId)
        }
      }, 3000)
    }
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
    stopListening()
  }
}, { immediate: true })

watch(activeSessionId, (newId) => {
  stopListening()
  if (isGenerating.value && newId) {
    void startListeningReport(newId)
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

// Voice-to-Voice Websocket Interactive States
const isVoiceMode = ref(false)
const voiceStatus = ref<'idle' | 'listening' | 'stt_processing' | 'tts_processing' | 'speaking'>('idle')
const incomingAudioChunk = ref('')
let voiceSocket: WebSocket | null = null
const currentVoiceAssistantMsgId = ref<number | null>(null)

function initVoiceWebSocket() {
  if (voiceSocket) {
    voiceSocket.close()
    voiceSocket = null
  }
  if (!isVoiceMode.value || !activeSessionId.value) {
    return
  }

  const loc = window.location
  const proto = loc.protocol === 'https:' ? 'wss:' : 'ws:'
  // WebSocket endpoint maps to /api/ws and handles authentication
  const wsUrl = `${proto}//${loc.host}/api/ws?token=${authStore.token}`

  voiceSocket = new WebSocket(wsUrl)

  voiceSocket.onopen = () => {
    console.log('Voice WebSocket connection opened')
    voiceSocket?.send(JSON.stringify({
      type: 'start',
      sessionId: activeSessionId.value
    }))
    voiceStatus.value = 'listening'
  }

  voiceSocket.onmessage = (event) => {
    try {
      const msg = JSON.parse(event.data)
      if (msg.type === 'status') {
        if (msg.status === 'stt_processing') {
          voiceStatus.value = 'stt_processing'
        } else if (msg.status === 'tts_processing') {
          voiceStatus.value = 'tts_processing'
        } else if (msg.status === 'speech_end') {
          voiceStatus.value = 'listening'
          currentVoiceAssistantMsgId.value = null
        }
      } else if (msg.type === 'user_text') {
        // Optimistic append of users transcribed reply to the thread
        appendMessage({
          id: Date.now(),
          role: 'user',
          content: msg.text,
          createdAt: new Date().toISOString()
        })
      } else if (msg.type === 'text') {
        // Streaming subtitles from LLM assistant output
        if (currentVoiceAssistantMsgId.value === null) {
          currentVoiceAssistantMsgId.value = Date.now() + 2
          ensureAssistantPlaceholder(currentVoiceAssistantMsgId.value)
        }
        appendAssistantDelta(currentVoiceAssistantMsgId.value, msg.chunk)
      } else if (msg.type === 'audio') {
        // Forward synthesized speech stream to playback queue
        incomingAudioChunk.value = msg.data
        nextTick(() => {
          incomingAudioChunk.value = ''
        })
      } else if (msg.type === 'judge') {
        // Feed live score badge
        if (replay.value) {
          const userMsgs = replay.value.messages.filter((m) => m.role === 'user')
          if (userMsgs.length > 0) {
            const last = userMsgs[userMsgs.length - 1]
            last.score = msg.score
            last.hint = msg.hint
          }
        }
      } else if (msg.type === 'error') {
        // Audio connection failure, fallback cleanly
        showNotice(msg.message, 'warning')
        isVoiceMode.value = false
      }
    } catch (e) {
      console.error('Failed to parse voice socket message:', e)
    }
  }

  voiceSocket.onclose = () => {
    console.log('Voice WebSocket connection closed')
    if (isVoiceMode.value) {
      voiceStatus.value = 'idle'
    }
  }

  voiceSocket.onerror = (err) => {
    console.error('Voice WebSocket connection error:', err)
    if (isVoiceMode.value) {
      showNotice('语音交互连接异常，已自动为您切回文字模式', 'warning')
      isVoiceMode.value = false
    }
  }
}

function handleAudioChunk(arrayBuffer: ArrayBuffer) {
  if (voiceSocket && voiceSocket.readyState === WebSocket.OPEN) {
    voiceSocket.send(new Uint8Array(arrayBuffer))
  }
}

function handleStartRecording() {
  if (voiceSocket && voiceSocket.readyState === WebSocket.OPEN) {
    voiceSocket.send(JSON.stringify({ type: 'start', sessionId: activeSessionId.value }))
    voiceStatus.value = 'listening'
  }
}

function handleStopRecording() {
  if (voiceSocket && voiceSocket.readyState === WebSocket.OPEN) {
    voiceSocket.send(JSON.stringify({ type: 'stop' }))
  }
}

function handlePlayStatus(status: 'playing' | 'idle') {
  if (status === 'playing') {
    voiceStatus.value = 'speaking'
  } else {
    if (voiceStatus.value === 'speaking') {
      voiceStatus.value = 'listening'
    }
  }
}

watch(isVoiceMode, (newVal) => {
  initVoiceWebSocket()
  if (!newVal && voiceSocket) {
    voiceSocket.close()
    voiceSocket = null
  }
})

watch(activeSessionId, (newId, oldId) => {
  if (newId !== oldId) {
    showingReport.value = false
    answer.value = ''
    isVoiceMode.value = false
    if (voiceSocket) {
      voiceSocket.close()
      voiceSocket = null
    }
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
  if (voiceSocket) {
    voiceSocket.close()
    voiceSocket = null
  }
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
          @open-llm-settings="showLlmSettings = true"
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
        :exporting="exportingPdf"
        @finish="handleFinish"
        @toggle-report="showingReport = $event"
        @export-pdf="handleExportPdf"
      />

      <div class="workspace-active__main">
        <div v-if="isGenerating" class="workspace-generating scrollable">
          <div class="generating-card">
            <div class="sandglass-icon">⏳</div>
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
              @open-llm-settings="showLlmSettings = true"
            />
          </div>
        </template>
      </div>
    </div>
    
    <div v-else-if="sessionLoading" class="workspace-loading">
      加载中...
    </div>
  </div>
  <LlmSettingsDialog v-model:visible="showLlmSettings" />
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
  box-shadow: 0 12px 24px color-mix(in srgb, var(--color-brand) 8%, transparent), 0 2px 6px color-mix(in srgb, var(--color-brand) 4%, transparent);
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
  font-size: 13px;
  padding: 0 var(--spacing-md);
  height: var(--ui-height-sm);
  border-radius: var(--radius-md);
  border: 1px solid var(--color-border);
  background: var(--color-surface);
  color: var(--color-text-secondary);
  cursor: pointer;
  transition: all 0.2s ease;
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
  box-shadow: 0 8px 30px rgba(0, 0, 0, 0.05);
  text-align: center;
}
.sandglass-icon {
  font-size: 48px;
  margin-bottom: var(--spacing-lg);
  animation: flip-sandglass 2s infinite ease-in-out;
}
@keyframes flip-sandglass {
  0% { transform: rotate(0deg); }
  45% { transform: rotate(0deg); }
  55% { transform: rotate(180deg); }
  100% { transform: rotate(180deg); }
}
.generating-title {
  font-size: 20px;
  font-weight: 600;
  color: var(--color-text-primary);
  margin-bottom: var(--spacing-xs);
}
.generating-subtitle {
  font-size: 14px;
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
  animation: progress-ind-anim 1.5s infinite linear;
}
@keyframes progress-ind-anim {
  0% { left: -50%; }
  100% { left: 100%; }
}
</style>
