import { http, unwrapResult, type ApiResult } from '@/shared/api'
import type { ResumeItem, ResumeUploadResponse } from '../model/types'

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
