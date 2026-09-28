import { apiRequest } from '@/shared/api/client'
import type { ResumeConversation, ResumeItem, ResumeTurn, ResumeUploadResponse } from './types'

export const fetchResumes = (signal?: AbortSignal) =>
  apiRequest<ResumeItem[]>('/resume/list', { signal })

export function uploadResume(file: File, signal?: AbortSignal) {
  const form = new FormData()
  form.append('file', file)
  return apiRequest<ResumeUploadResponse>('/resume/upload', { method: 'POST', body: form, signal })
}

export const deleteResume = (id: number) => apiRequest<void>(`/resume/${id}`, { method: 'DELETE' })

export const fetchResumeConversations = (signal?: AbortSignal) =>
  apiRequest<ResumeConversation[]>('/resume/workspace/conversations', { signal })

export const createResumeConversation = (resumeId?: number) =>
  apiRequest<ResumeConversation>('/resume/workspace/conversations', {
    method: 'POST',
    body: JSON.stringify({ resumeId: resumeId ?? null }),
  })

export const fetchResumeTurns = (conversationId: number, signal?: AbortSignal) =>
  apiRequest<ResumeTurn[]>(`/resume/workspace/conversations/${conversationId}/turns`, {
    signal,
  })

export const submitResumeInstruction = (conversationId: number, instruction: string) =>
  apiRequest<ResumeTurn>(`/resume/workspace/conversations/${conversationId}/turns`, {
    method: 'POST',
    body: JSON.stringify({ instruction }),
  })

export const pinResumeConversation = (conversationId: number, pinned: boolean) =>
  apiRequest<void>(`/resume/workspace/conversations/${conversationId}/pin`, {
    method: 'PUT',
    body: JSON.stringify({ pinned }),
  })

export const deleteResumeConversation = (conversationId: number) =>
  apiRequest<void>(`/resume/workspace/conversations/${conversationId}`, { method: 'DELETE' })
