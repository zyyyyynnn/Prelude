import { useQueryClient } from '@tanstack/react-query'
import type { InterviewMessageRecord, InterviewSessionDetailResponse } from '../types'
import { applyMessageUpdate } from './message-updates'

export { applyMessageUpdate }
export const MAX_CONTEXT_MESSAGES = 20

export function handleInterviewStreamEvent(
  event: { name: string; data: string },
  assistantId: number,
  sessionId: number,
  client: ReturnType<typeof useQueryClient>,
  callbacks: {
    updateMessage: (msg: InterviewMessageRecord, append?: boolean) => void
    setMessages: (
      updater: (prev: InterviewMessageRecord[] | null) => InterviewMessageRecord[] | null,
    ) => void
    setShowReport: (show: boolean) => void
    onError: (msg: string) => void
  },
) {
  const { name, data } = event
  if (name === 'message') {
    callbacks.updateMessage({ id: assistantId, role: 'assistant', content: data }, true)
    return
  }
  if (name === 'report_ready') {
    client.setQueryData<InterviewSessionDetailResponse>(['interview-session', sessionId], (old) =>
      old ? { ...old, summaryReport: data, status: 'finished' } : old,
    )
    callbacks.setShowReport(true)
    return
  }
  if (name === 'judge') {
    try {
      const result = JSON.parse(data) as { score?: number; hint?: string }
      callbacks.setMessages((list) => {
        const next = [...(list ?? [])]
        const index = next.findLastIndex((item) => item.role === 'user')
        if (index >= 0) next[index] = { ...next[index], score: result.score, hint: result.hint }
        return next
      })
    } catch {
      callbacks.onError('评分数据无法解析')
    }
    return
  }
  if (name === 'error') throw new Error(data)
}
