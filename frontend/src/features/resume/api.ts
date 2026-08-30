import { apiRequest } from '@/shared/api/client'
import type { ResumeItem, ResumeUploadResponse } from './types'

export const fetchResumes = (signal?: AbortSignal) =>
  apiRequest<ResumeItem[]>('/resume/list', { signal })
export function uploadResume(file: File, signal?: AbortSignal) {
  const form = new FormData()
  form.append('file', file)
  return apiRequest<ResumeUploadResponse>('/resume/upload', { method: 'POST', body: form, signal })
}
export const deleteResume = (id: number) => apiRequest<void>(`/resume/${id}`, { method: 'DELETE' })
