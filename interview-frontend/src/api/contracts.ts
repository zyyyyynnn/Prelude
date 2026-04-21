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
  createdAt?: string
  sessionCount?: number
  inUse?: boolean
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
  currentStage?: InterviewStageName
}

export type InterviewFinishResponse = {
  sessionId?: number
  summaryReport: string
  status?: string
}

export type InterviewStageName = 'warmup' | 'technical' | 'deep_dive' | 'closing'

export type InterviewStageRecord = {
  stageName: InterviewStageName
  startedAt?: string
  endedAt?: string | null
}

export type InterviewMessageRole = 'system' | 'user' | 'assistant'

export type InterviewMessageRecord = {
  id: number
  role: InterviewMessageRole
  content: string
  seqNum?: number
  createdAt?: string
}

export type InterviewSessionItem = {
  sessionId: number
  targetPosition?: string
  positionName?: string
  status?: string
  currentStage?: InterviewStageName
  llmProvider?: string
  llmModel?: string
  createdAt?: string
  summaryReport?: string
}

export type InterviewReplayResponse = {
  sessionId: number
  targetPosition?: string
  status?: string
  currentStage?: InterviewStageName
  summaryReport?: string
  stages: InterviewStageRecord[]
  messages: InterviewMessageRecord[]
}

export type InterviewChatRequest = {
  content: string
}

export type InterviewStageChangePayload = {
  stageName: InterviewStageName
}

export type InterviewStageChangeResponse = {
  stageName: InterviewStageName
  startedAt?: string
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

export type UserProfilePayload = {
  email?: string
  oldPassword?: string
  newPassword?: string
}

export type UserProfileResponse = {
  username?: string
  email?: string
}

export type AnalyticsRadarResponse = {
  technical: number
  expression: number
  logic: number
  sessionCount: number
}

export type AnalyticsTrendPoint = {
  sessionId: number
  createdAt: string
  technical: number
  expression: number
  logic: number
}

export type AnalyticsWeaknessItem = {
  category: string
  count: number
  descriptions: string[]
}

export function unwrapResult<T>(result: ApiResult<T>): T {
  if (result.code !== 200) {
    throw new Error(result.message || '请求失败')
  }

  return result.data
}
