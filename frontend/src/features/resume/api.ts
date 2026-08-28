import { apiRequest } from '@/shared/api/client'
import type {
  ResumeImprovement,
  ResumeImprovementDecision,
  ResumeItem,
  ResumeUploadResponse,
} from './types'

export const fetchResumes = (signal?: AbortSignal) =>
  apiRequest<ResumeItem[]>('/resume/list', { signal })
export function uploadResume(file: File, signal?: AbortSignal) {
  const form = new FormData()
  form.append('file', file)
  return apiRequest<ResumeUploadResponse>('/resume/upload', { method: 'POST', body: form, signal })
}
export const deleteResume = (id: number) => apiRequest<void>(`/resume/${id}`, { method: 'DELETE' })
export const fetchResumeImprovements = (resumeId: number, sessionId?: number) =>
  apiRequest<ResumeImprovement[]>(`/resume/${resumeId}/improvements`, { query: { sessionId } })
export const acceptResumeImprovement = (id: number) =>
  apiRequest<ResumeImprovementDecision>(`/resume/improvements/${id}/accept`, { method: 'POST' })
export const rejectResumeImprovement = (id: number) =>
  apiRequest<ResumeImprovement>(`/resume/improvements/${id}/reject`, { method: 'POST' })
