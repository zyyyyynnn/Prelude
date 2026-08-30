import { apiRequest } from '@/shared/api/client'
import type { AttachmentItem } from './types'

export function uploadAttachment(file: File, signal?: AbortSignal) {
  const body = new FormData()
  body.append('file', file)
  return apiRequest<AttachmentItem>('/attachments', { method: 'POST', body, signal })
}

export const deleteAttachment = (id: number) =>
  apiRequest<void>(`/attachments/${id}`, { method: 'DELETE' })
