/**
 * Pure message-list merge used by the interview stream. Kept free of React Query so the
 * merge rules can be judged by node without a browser.
 */
export type InterviewMessageRecord = {
  id: number
  role: 'system' | 'user' | 'assistant'
  content: string
  seqNum?: number
  createdAt?: string
  score?: number
  hint?: string
}

export function applyMessageUpdate(
  existing: InterviewMessageRecord[] | null,
  fallback: InterviewMessageRecord[] | undefined,
  message: InterviewMessageRecord,
  append: boolean,
): InterviewMessageRecord[] {
  const list = [...(existing ?? fallback ?? [])]
  const index = list.findIndex((item) => item.id === message.id)
  if (index < 0) {
    list.push(message)
  } else {
    list[index] = {
      ...list[index],
      ...message,
      content: append ? list[index].content + message.content : message.content,
    }
  }
  return list
}
