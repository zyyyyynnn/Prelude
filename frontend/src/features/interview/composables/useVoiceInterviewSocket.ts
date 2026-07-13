import { nextTick, ref, watch, type Ref } from 'vue'
import type { InterviewMessageRecord } from '@/api/contracts'

type ReplayState = {
  messages: InterviewMessageRecord[]
}

type UseVoiceInterviewSocketOptions = {
  activeSessionId: Ref<number | null>
  isVoiceMode: Ref<boolean>
  replay: Ref<ReplayState | null>
  authToken: () => string
  showNotice: (message: string, type?: 'success' | 'error' | 'warning' | 'info') => void
  appendMessage: (message: InterviewMessageRecord) => void
  ensureAssistantPlaceholder: (id: number) => void
  appendAssistantDelta: (id: number, delta: string) => void
  onSessionChanged?: () => void
}

type VoiceStatus = 'idle' | 'listening' | 'stt_processing' | 'tts_processing' | 'speaking'

export function useVoiceInterviewSocket(options: UseVoiceInterviewSocketOptions) {
  const voiceStatus = ref<VoiceStatus>('idle')
  const incomingAudioChunk = ref('')
  const currentVoiceAssistantMsgId = ref<number | null>(null)
  let voiceSocket: WebSocket | null = null

  function closeVoiceSocket() {
    if (voiceSocket) {
      voiceSocket.close()
      voiceSocket = null
    }
  }

  function initVoiceWebSocket() {
    closeVoiceSocket()
    if (!options.isVoiceMode.value || !options.activeSessionId.value) {
      return
    }

    const loc = window.location
    const proto = loc.protocol === 'https:' ? 'wss:' : 'ws:'
    const wsUrl = `${proto}//${loc.host}/api/ws?token=${options.authToken()}`
    voiceSocket = new WebSocket(wsUrl)

    voiceSocket.onopen = () => {
      voiceSocket?.send(JSON.stringify({
        type: 'start',
        sessionId: options.activeSessionId.value,
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
          options.appendMessage({
            id: Date.now(),
            role: 'user',
            content: msg.text,
            createdAt: new Date().toISOString(),
          })
        } else if (msg.type === 'text') {
          if (currentVoiceAssistantMsgId.value === null) {
            currentVoiceAssistantMsgId.value = Date.now() + 2
            options.ensureAssistantPlaceholder(currentVoiceAssistantMsgId.value)
          }
          options.appendAssistantDelta(currentVoiceAssistantMsgId.value, msg.chunk)
        } else if (msg.type === 'audio') {
          incomingAudioChunk.value = msg.data
          nextTick(() => {
            incomingAudioChunk.value = ''
          })
        } else if (msg.type === 'judge') {
          if (options.replay.value) {
            const userMsgs = options.replay.value.messages.filter((m) => m.role === 'user')
            if (userMsgs.length > 0) {
              const last = userMsgs[userMsgs.length - 1]
              last.score = msg.score
              last.hint = msg.hint
            }
          }
        } else if (msg.type === 'error') {
          options.showNotice(msg.message, 'warning')
          options.isVoiceMode.value = false
        }
      } catch (error) {
        console.error('Failed to parse voice socket message:', error)
      }
    }

    voiceSocket.onclose = () => {
      if (options.isVoiceMode.value) {
        voiceStatus.value = 'idle'
      }
    }

    voiceSocket.onerror = () => {
      if (options.isVoiceMode.value) {
        options.showNotice('语音交互连接异常，已自动为您切回文字模式', 'warning')
        options.isVoiceMode.value = false
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
      voiceSocket.send(JSON.stringify({ type: 'start', sessionId: options.activeSessionId.value }))
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
    } else if (voiceStatus.value === 'speaking') {
      voiceStatus.value = 'listening'
    }
  }

  watch(options.isVoiceMode, (newVal) => {
    initVoiceWebSocket()
    if (!newVal) {
      closeVoiceSocket()
    }
  })

  watch(options.activeSessionId, (newId, oldId) => {
    if (newId !== oldId) {
      options.onSessionChanged?.()
      options.isVoiceMode.value = false
      closeVoiceSocket()
    }
  })

  return {
    voiceStatus,
    incomingAudioChunk,
    initVoiceWebSocket,
    closeVoiceSocket,
    handleAudioChunk,
    handleStartRecording,
    handleStopRecording,
    handlePlayStatus,
  }
}
