import { useCallback, useEffect, useEffectEvent, useRef, useState } from 'react'
import type { VoiceStatus } from '@/shared/ui/prompt-bar'
import type { InterviewMessageRecord } from './types'

interface VoicePayload {
  type: string
  status?: string
  text?: string
  chunk?: string
  data?: string
  message?: string
  score?: number
  hint?: string
}

function resolveVoiceStatus(status?: string): VoiceStatus {
  if (status === 'speech_end') return 'listening'
  if (status?.includes('processing')) return 'processing'
  return 'listening'
}

function playVoiceAudio(base64Data: string, onEnded: () => void) {
  const audio = new Audio(`data:audio/wav;base64,${base64Data}`)
  audio.onended = onEnded
  void audio.play().catch(onEnded)
}

function dispatchVoicePayload(
  payload: VoicePayload,
  ctx: {
    setStatus: (status: VoiceStatus) => void
    reportMessage: (message: InterviewMessageRecord, append?: boolean) => void
    reportTranscript: (text: string) => void
    refreshSession: () => void
    terminalError: (message: string) => void
    assistantId: React.MutableRefObject<number | null>
  },
) {
  if (payload.type === 'status') {
    ctx.setStatus(resolveVoiceStatus(payload.status))
  } else if (payload.type === 'user_text' && payload.text) {
    // The candidate's own words land in the composer, not the thread: what the
    // microphone heard is a draft until they send it.
    ctx.reportTranscript(payload.text)
  } else if (payload.type === 'text' && payload.chunk) {
    if (!ctx.assistantId.current) ctx.assistantId.current = Date.now() + 1
    ctx.reportMessage(
      {
        id: ctx.assistantId.current,
        role: 'assistant',
        content: payload.chunk,
        createdAt: new Date().toISOString(),
      },
      true,
    )
  } else if (payload.type === 'audio' && payload.data) {
    ctx.setStatus('speaking')
    playVoiceAudio(payload.data, () => ctx.setStatus('listening'))
  } else if (payload.type === 'judge') {
    ctx.assistantId.current = null
    ctx.refreshSession()
  } else if (payload.type === 'error') {
    ctx.terminalError(payload.message || '语音服务异常')
  }
}

function createMediaRecorder(
  media: MediaStream,
  onData: (data: ArrayBuffer) => void,
): MediaRecorder {
  const next = new MediaRecorder(media, { mimeType: 'audio/webm' })
  next.ondataavailable = async (event) => {
    if (event.data.size) {
      onData(await event.data.arrayBuffer())
    }
  }
  next.onstop = () => media.getTracks().forEach((track) => track.stop())
  return next
}

export function useVoiceInterview({
  enabled,
  sessionId,
  onMessage,
  onTranscript,
  onRefresh,
  onError,
  onTerminalError,
}: {
  enabled: boolean
  sessionId: number
  onMessage: (message: InterviewMessageRecord, append?: boolean) => void
  onTranscript: (text: string) => void
  onRefresh: () => void
  onError: (message: string) => void
  onTerminalError: () => void
}) {
  const [status, setStatus] = useState<VoiceStatus>('idle')
  const [recording, setRecording] = useState(false)
  // State, not a ref: the level meter renders against the live stream.
  const [media, setMedia] = useState<MediaStream | null>(null)
  const socket = useRef<WebSocket | null>(null)
  const recorder = useRef<MediaRecorder | null>(null)
  const stream = useRef<MediaStream | null>(null)
  const assistantId = useRef<number | null>(null)
  // closing: intentional teardown. terminal: fatal error already reported — suppress double exit.
  const closing = useRef(false)
  const terminal = useRef(false)
  const reportMessage = useEffectEvent(onMessage)
  const reportTranscript = useEffectEvent(onTranscript)
  const refreshSession = useEffectEvent(onRefresh)
  const reportError = useEffectEvent(onError)
  const exitVoice = useEffectEvent(onTerminalError)

  const close = useCallback(() => {
    closing.current = true
    if (recorder.current?.state === 'recording') recorder.current.stop()
    stream.current?.getTracks().forEach((track) => track.stop())
    socket.current?.close()
    recorder.current = null
    stream.current = null
    socket.current = null
    setMedia(null)
    setRecording(false)
    setStatus('idle')
  }, [])

  useEffect(() => {
    if (!enabled) return
    closing.current = false
    terminal.current = false
    const terminalError = (message: string) => {
      if (terminal.current) return
      terminal.current = true
      close()
      exitVoice()
      reportError(message)
    }
    const protocol = location.protocol === 'https:' ? 'wss' : 'ws'
    const ws = new WebSocket(`${protocol}://${location.host}/api/ws`)
    ws.binaryType = 'arraybuffer'
    ws.onopen = () => {
      ws.send(JSON.stringify({ type: 'start', sessionId }))
      setStatus('listening')
    }
    ws.onmessage = (event) => {
      try {
        const payload = JSON.parse(String(event.data)) as VoicePayload
        dispatchVoicePayload(payload, {
          setStatus,
          reportMessage,
          reportTranscript,
          refreshSession,
          terminalError,
          assistantId,
        })
      } catch {
        terminalError('语音服务返回了无法识别的数据')
      }
    }
    ws.onerror = () => terminalError('语音连接异常，已切回文字模式')
    ws.onclose = () => {
      if (!closing.current) terminalError('语音连接已断开，已切回文字模式')
      else setStatus('idle')
    }
    socket.current = ws
    return close
  }, [close, enabled, sessionId])

  async function startRecording() {
    if (recording || recorder.current?.state === 'recording') return
    if (!navigator.mediaDevices?.getUserMedia) {
      onError('当前浏览器不支持语音录制')
      return
    }
    const activeSocket = socket.current
    try {
      const media = await navigator.mediaDevices.getUserMedia({ audio: true })
      if (closing.current || socket.current !== activeSocket) {
        media.getTracks().forEach((track) => track.stop())
        return
      }
      setMedia(media)
      const next = createMediaRecorder(media, (buffer) => {
        if (socket.current?.readyState === WebSocket.OPEN) {
          socket.current.send(buffer)
        }
      })
      next.start(250)
      stream.current = media
      recorder.current = next
      setRecording(true)
      setStatus('listening')
    } catch {
      if (!closing.current && socket.current === activeSocket)
        onError('无法访问麦克风，请检查浏览器权限')
    }
  }

  function stopRecording() {
    if (!recording && recorder.current?.state !== 'recording') return
    recorder.current?.stop()
    socket.current?.send(JSON.stringify({ type: 'stop' }))
    setRecording(false)
  }

  async function toggleRecording() {
    if (recording) stopRecording()
    else await startRecording()
  }
  return {
    status,
    recording,
    media,
    startRecording,
    stopRecording,
    toggleRecording,
    close,
  }
}
