export type ApiResult<T> = {
  code: number
  message: string
  data: T
}

export type LoginResponse = {
  token: string
}

export type ResumeItem = {
  id: number
  fileName: string
}

export type PositionTemplate = {
  id: number
  name: string
}

export type ResumeUploadResponse = {
  resumeId: number
  skills?: string[]
  projects?: unknown[]
}

export type InterviewStartResponse = {
  sessionId: number
}

export type InterviewFinishResponse = {
  summaryReport: string
}

export type LlmProviderModel = string | {
  key?: string
  model?: string
  id?: string
  name?: string
  displayName?: string
}

export type LlmProviderRecord = {
  providerKey?: string
  providerName?: string
  displayName?: string
  name?: string
  models?: LlmProviderModel[]
  availableModels?: LlmProviderModel[]
}

export type LlmProviderOption = {
  providerKey: string
  displayName: string
  models: string[]
}

export type LlmConfigPayload = {
  providerKey: string
  model: string
  apiKey: string
}

export type LlmConfigResponse = {
  providerKey?: string
  model?: string
  apiKeyMasked?: string
  providerName?: string
  displayName?: string
}

export type InterviewChatRequest = {
  content: string
}

export function unwrapResult<T>(result: ApiResult<T>): T {
  if (result.code !== 200) {
    throw new Error(result.message || '请求失败')
  }

  return result.data
}
