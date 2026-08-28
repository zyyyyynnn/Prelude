import { useCallback, useEffect, useEffectEvent, useRef, useState } from 'react'
import type { InterviewMessageRecord } from './types'

type VoiceStatus = 'idle' | 'listening' | 'processing' | 'speaking'

export function useVoiceInterview({
  enabled,
  sessionId,
  onMessage,
  onRefresh,
  onError,
  onTerminalError,
}: {
  enabled: boolean
  sessionId: number
  onMessage: (message: InterviewMessageRecord, append?: boolean) => void
  onRefresh: () => void
  onError: (message: string) => void
  onTerminalError: () => void
}) {
  const [status, setStatus] = useState<VoiceStatus>('idle')
  const [recording, setRecording] = useState(false)
  const socket = useRef<WebSocket | null>(null)
  const recorder = useRef<MediaRecorder | null>(null)
  const stream = useRef<MediaStream | null>(null)
  const assistantId = useRef<number | null>(null)
  const closing = useRef(false)
  const terminal = useRef(false)
  const reportMessage = useEffectEvent(onMessage)
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
        const payload = JSON.parse(String(event.data)) as {
          type: string
          status?: string
          text?: string
          chunk?: string
          data?: string
          message?: string
          score?: number
          hint?: string
        }
        if (payload.type === 'status')
          setStatus(
            payload.status === 'speech_end'
              ? 'listening'
              : payload.status?.includes('processing')
                ? 'processing'
                : 'listening',
          )
        if (payload.type === 'user_text' && payload.text)
          reportMessage({
            id: Date.now(),
            role: 'user',
            content: payload.text,
            createdAt: new Date().toISOString(),
          })
        if (payload.type === 'text' && payload.chunk) {
          if (!assistantId.current) assistantId.current = Date.now() + 1
          reportMessage(
            {
              id: assistantId.current,
              role: 'assistant',
              content: payload.chunk,
              createdAt: new Date().toISOString(),
            },
            true,
          )
        }
        if (payload.type === 'audio' && payload.data) {
          setStatus('speaking')
          const audio = new Audio(`data:audio/wav;base64,${payload.data}`)
          audio.onended = () => setStatus('listening')
          void audio.play().catch(() => setStatus('listening'))
        }
        if (payload.type === 'judge') {
          assistantId.current = null
          refreshSession()
        }
        if (payload.type === 'error') terminalError(payload.message || '语音服务异常')
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
    try {
      const media = await navigator.mediaDevices.getUserMedia({ audio: true })
      const next = new MediaRecorder(media, { mimeType: 'audio/webm' })
      next.ondataavailable = async (event) => {
        if (event.data.size && socket.current?.readyState === WebSocket.OPEN)
          socket.current.send(await event.data.arrayBuffer())
      }
      next.onstop = () => media.getTracks().forEach((track) => track.stop())
      next.start(250)
      stream.current = media
      recorder.current = next
      setRecording(true)
      setStatus('listening')
    } catch {
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
    startRecording,
    stopRecording,
    toggleRecording,
    close,
  }
}
