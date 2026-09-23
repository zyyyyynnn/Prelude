import type { InterviewMessageRecord, InterviewSessionDetailResponse } from '../types'

export const MAX_CONTEXT_MESSAGES = 20

/** The one cache write the stream needs; React Query satisfies it at the call site. */
export type SessionReportCache = {
  setQueryData(
    key: ['interview-session', number],
    updater: (
      old: InterviewSessionDetailResponse | undefined,
    ) => InterviewSessionDetailResponse | undefined,
  ): void
}

export function handleInterviewStreamEvent(
  event: { name: string; data: string },
  assistantId: number,
  sessionId: number,
  cache: SessionReportCache,
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
    cache.setQueryData(['interview-session', sessionId], (old) =>
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
  if (name === 'error') {
    // Fail the stream promise so the mutation's error path discards the optimistic turn.
    throw new Error(data)
  }
}
