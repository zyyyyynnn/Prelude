import { http } from './http'
import type { ApiResult, ResumeItem, ResumeUploadResponse } from './contracts'
import { unwrapResult } from './contracts'

export async function fetchResumes() {
  const response = await http.get<ApiResult<ResumeItem[]>>('/resume/list')
  return unwrapResult(response.data)
}

export async function uploadResume(file: File) {
  const formData = new FormData()
  formData.append('file', file)
  const response = await http.post<ApiResult<ResumeUploadResponse>>('/resume/upload', formData, {
    headers: {
      'Content-Type': 'multipart/form-data',
    },
  })
  return unwrapResult(response.data)
}

export async function deleteResume(resumeId: number) {
  const response = await http.delete<ApiResult<void>>(`/resume/${resumeId}`)
  return unwrapResult(response.data)
}
