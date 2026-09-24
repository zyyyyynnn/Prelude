import type { InterviewMessageRecord } from '../types'

export const MAX_CONTEXT_MESSAGES = 20

/** Optimistic user + assistant placeholders before the stream opens. */
export function buildOptimisticTurn(
  visible: InterviewMessageRecord[],
  content: string,
  autoStart: boolean,
  now = Date.now(),
): { messages: InterviewMessageRecord[]; assistantId: number; context: InterviewMessageRecord[] } {
  const optimisticId = now
  const assistantId = now + 1
  const base = [...visible]
  if (!autoStart) {
    base.push({ id: optimisticId, role: 'user', content, createdAt: new Date(now).toISOString() })
  }
  base.push({
    id: assistantId,
    role: 'assistant',
    content: '',
    createdAt: new Date(now).toISOString(),
  })
  const context = base.filter((item) => item.id !== assistantId).slice(-MAX_CONTEXT_MESSAGES)
  return { messages: base, assistantId, context }
}

/** An empty, unfinished session starts one turn and only one. */
export function shouldAutoStart(
  session: { messages: InterviewMessageRecord[]; status?: string } | undefined,
  sessionId: number,
  autoStartedSessionId: number | null,
): boolean {
  if (!session || session.messages.length || autoStartedSessionId === sessionId) return false
  return session.status !== 'finished'
}
