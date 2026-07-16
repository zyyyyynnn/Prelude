import { http, unwrapResult, type ApiResult } from '@/shared/api'
import type {
  ResumeDocument,
  ResumeDocumentView,
  ResumeImprovement,
  ResumeImprovementDecision,
  ResumeItem,
  ResumeUploadResponse,
} from '../model/types'

export async function fetchResumes() {
  const response = await http.get<ApiResult<ResumeItem[]>>('/resume/list')
  return unwrapResult(response.data)
}

export async function uploadResume(file: File, signal?: AbortSignal) {
  const formData = new FormData()
  formData.append('file', file)
  const response = await http.post<ApiResult<ResumeUploadResponse>>('/resume/upload', formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
    signal,
  })
  return unwrapResult(response.data)
}

export async function deleteResume(resumeId: number) {
  const response = await http.delete<ApiResult<void>>(`/resume/${resumeId}`)
  return unwrapResult(response.data)
}

export async function fetchResumeDocument(resumeId: number) {
  const response = await http.get<ApiResult<ResumeDocumentView>>(`/resume/${resumeId}/document`)
  return unwrapResult(response.data)
}

export async function updateResumeDocument(
  resumeId: number,
  expectedVersion: number,
  document: ResumeDocument,
) {
  const response = await http.put<ApiResult<ResumeDocumentView>>(`/resume/${resumeId}/document`, {
    expectedVersion,
    document,
  })
  return unwrapResult(response.data)
}

export async function fetchResumeImprovements(resumeId: number, sessionId?: number) {
  const response = await http.get<ApiResult<ResumeImprovement[]>>(
    `/resume/${resumeId}/improvements`,
    { params: sessionId ? { sessionId } : undefined },
  )
  return unwrapResult(response.data)
}

export async function acceptResumeImprovement(improvementId: number) {
  const response = await http.post<ApiResult<ResumeImprovementDecision>>(
    `/resume/improvements/${improvementId}/accept`,
  )
  return unwrapResult(response.data)
}

export async function rejectResumeImprovement(improvementId: number) {
  const response = await http.post<ApiResult<ResumeImprovement>>(
    `/resume/improvements/${improvementId}/reject`,
  )
  return unwrapResult(response.data)
}
