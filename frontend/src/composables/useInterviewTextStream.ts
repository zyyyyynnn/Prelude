import type { Ref } from 'vue'
import type { InterviewMessageRecord } from '../api/contracts'
import { streamInterviewChat } from '../api/interview'
import { getErrorMessage } from '../utils/errors'

type ReplayState = {
  messages: InterviewMessageRecord[]
  summaryReport?: string
}

type StreamEvent = {
  eventName: string
  data: string
}

type UseInterviewTextStreamOptions = {
  activeSessionId: Ref<number | null>
  replay: Ref<ReplayState | null>
  reportMarkdown: Ref<string>
  reconnectingStatus: Ref<string>
  streamTimeoutId: Ref<ReturnType<typeof setTimeout> | null>
  authToken: () => string
  getNewAbortSignal: () => AbortSignal
  abortActiveStream: () => void
  loadSession: (sessionId: number, silent?: boolean) => Promise<void>
  refreshSessionList: () => Promise<void>
  showNotice: (message: string, type?: 'success' | 'error' | 'warning' | 'info') => void
  onAuthExpired: () => Promise<void>
  showReport: () => void
}

const MAX_CONTEXT_MESSAGES = 20

function trimContextMessages(messages: InterviewMessageRecord[]) {
  if (!messages) return []
  if (messages.length <= MAX_CONTEXT_MESSAGES) return messages
  const systemMsg = messages.find((m) => m.role === 'system')
  const restMsgs = messages.filter((m) => m.role !== 'system')
  const trimmed = restMsgs.slice(-MAX_CONTEXT_MESSAGES)
  return systemMsg ? [systemMsg, ...trimmed] : trimmed
}

function createThrottle<T extends (...args: any[]) => void>(fn: T, delay: number) {
  let lastTime = 0
  return (...args: Parameters<T>) => {
    const now = Date.now()
    if (now - lastTime >= delay) {
      fn(...args)
      lastTime = now
    }
  }
}

export function useInterviewTextStream(options: UseInterviewTextStreamOptions) {
  let chunkBuffer = ''
  let chunkTargetId: number | null = null
  let chunkRafId: number | null = null

  const saveStreamSnapshot = createThrottle((sessionId: number, messageId: number, content: string) => {
    sessionStorage.setItem('interview-stream-snapshot', JSON.stringify({
      sessionId,
      messageId,
      content,
      timestamp: Date.now(),
    }))
  }, 3000)

  function appendMessage(message: InterviewMessageRecord) {
    if (!options.replay.value) return
    options.replay.value.messages = [...options.replay.value.messages, message]
  }

  function removeMessageById(id: number | null) {
    if (!options.replay.value || id == null) return
    options.replay.value.messages = options.replay.value.messages.filter((message) => message.id !== id)
  }

  function ensureAssistantPlaceholder(id: number) {
    if (!options.replay.value || options.replay.value.messages.some((message) => message.id === id)) return
    appendMessage({
      id,
      role: 'assistant',
      content: '',
      createdAt: new Date().toISOString(),
    })
  }

  function flushChunkBuffer() {
    if (!chunkBuffer || !chunkTargetId || !options.replay.value) {
      chunkBuffer = ''
      chunkTargetId = null
      chunkRafId = null
      return
    }

    const list = [...options.replay.value.messages]
    const target = list.find((m) => m.id === chunkTargetId)
    if (!target || target.role !== 'assistant') {
      list.push({
        id: chunkTargetId,
        role: 'assistant',
        content: chunkBuffer,
        createdAt: new Date().toISOString(),
      })
    } else {
      target.content += chunkBuffer
    }
    options.replay.value.messages = list
    chunkBuffer = ''
    chunkRafId = null
  }

  function appendAssistantDelta(id: number, delta: string) {
    chunkBuffer += delta
    chunkTargetId = id
    if (chunkRafId === null) {
      chunkRafId = requestAnimationFrame(flushChunkBuffer)
    }
  }

  function clearAssistantPlaceholder(id: number) {
    if (!options.replay.value) return
    const list = [...options.replay.value.messages]
    const target = list.find((message) => message.id === id)
    if (target && target.role === 'assistant') {
      target.content = ''
      options.replay.value.messages = list
    }
  }

  function handleStreamEvent(event: StreamEvent, assistantMessageId: number) {
    if (event.eventName === 'status') {
      if (event.data === 'checking') {
        options.reconnectingStatus.value = '连接异常，正在核对会话状态...'
      } else if (event.data.startsWith('reconnecting_')) {
        const attempt = event.data.split('_')[1]
        options.reconnectingStatus.value = `连接已断开，正在尝试第 ${attempt} 次重连...`
        clearAssistantPlaceholder(assistantMessageId)
      }
    } else if (event.eventName === 'sync') {
      const serverMsgs = JSON.parse(event.data)
      if (options.replay.value) {
        options.replay.value.messages = serverMsgs
      }
      options.reconnectingStatus.value = ''
    } else if (event.eventName === 'report_ready') {
      options.reportMarkdown.value = event.data
      if (options.replay.value) {
        options.replay.value.summaryReport = event.data
      }
      options.showReport()
    } else if (event.eventName === 'judge') {
      const data = JSON.parse(event.data)
      if (options.replay.value) {
        const userMsgs = options.replay.value.messages.filter((m) => m.role === 'user')
        if (userMsgs.length > 0) {
          const lastUserMsg = userMsgs[userMsgs.length - 1]
          lastUserMsg.score = data.score
          lastUserMsg.hint = data.hint
        }
      }
    }
  }

  async function streamReply(content: string, autoStart = false) {
    if (!options.activeSessionId.value) {
      options.showNotice('请先创建或选择一场面试', 'warning')
      return false
    }
    if (!options.replay.value) {
      await options.loadSession(options.activeSessionId.value, true)
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

    const signal = options.getNewAbortSignal()
    options.streamTimeoutId.value = setTimeout(() => {
      options.abortActiveStream()
      options.showNotice('网络或模型响应超时，已强制断开，请重试', 'error')
    }, 120000)

    try {
      options.reconnectingStatus.value = ''
      await streamInterviewChat(
        options.authToken(),
        options.activeSessionId.value,
        { content, messages: trimContextMessages(options.replay.value?.messages || []) },
        autoStart,
        {
          onChunk(chunk) {
            appendAssistantDelta(assistantMessageId, chunk)
            const target = options.replay.value?.messages.find((m) => m.id === assistantMessageId)
            if (target && options.activeSessionId.value) {
              saveStreamSnapshot(options.activeSessionId.value, assistantMessageId, target.content + chunkBuffer)
            }
          },
          onEvent(event) {
            handleStreamEvent(event, assistantMessageId)
          },
        },
        signal,
      )
      options.reconnectingStatus.value = ''
      await options.refreshSessionList()
      await options.loadSession(options.activeSessionId.value, true)
      return true
    } catch (error) {
      options.reconnectingStatus.value = ''
      if (error instanceof Error && error.name === 'AbortError') {
        return false
      }
      removeMessageById(assistantMessageId)
      removeMessageById(optimisticUserId)
      const message = getErrorMessage(error)
      if (message.includes('登录已失效')) {
        await options.onAuthExpired()
        return false
      }
      options.showNotice(message, 'error')
      return false
    } finally {
      sessionStorage.removeItem('interview-stream-snapshot')
      cleanupTextStream()
    }
  }

  function cleanupTextStream() {
    if (chunkRafId !== null) {
      cancelAnimationFrame(chunkRafId)
      flushChunkBuffer()
    }
    if (options.streamTimeoutId.value !== null) {
      clearTimeout(options.streamTimeoutId.value)
      options.streamTimeoutId.value = null
    }
  }

  return {
    appendMessage,
    ensureAssistantPlaceholder,
    appendAssistantDelta,
    streamReply,
    cleanupTextStream,
  }
}
