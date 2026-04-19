import { http } from './http'
import type {
  ApiResult,
  LoginResponse,
  PositionTemplate,
  ResumeItem,
  ResumeUploadResponse,
  InterviewStartResponse,
} from './contracts'
import { unwrapResult } from './contracts'

export async function login(username: string, password: string) {
  const response = await http.post<ApiResult<LoginResponse>>('/auth/login', {
    username,
    password,
  })
  return unwrapResult(response.data)
}

export async function register(username: string, password: string) {
  const response = await http.post<ApiResult<void>>('/auth/register', {
    username,
    password,
  })
  return unwrapResult(response.data)
}

export async function fetchResumes() {
  const response = await http.get<ApiResult<ResumeItem[]>>('/resume/list')
  return unwrapResult(response.data)
}

export async function fetchPositions() {
  const response = await http.get<ApiResult<PositionTemplate[]>>('/position/list')
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

export async function startInterview(payload: { resumeId: number; positionId: number }) {
  const response = await http.post<ApiResult<InterviewStartResponse>>('/interview/start', payload)
  return unwrapResult(response.data)
}

export async function finishInterview(sessionId: number) {
  const response = await http.post<ApiResult<{ summaryReport: string }>>(
    `/interview/${sessionId}/finish`,
  )
  return unwrapResult(response.data)
}
