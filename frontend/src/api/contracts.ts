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

export type InterviewStartPayload = {
  resumeId: number
  positionId: number
  jdText?: string
  llmModel?: string
}

export type StructuredReportSummary = {
  fitAssessment: string
  actionRecommendation: string
  overallRisk: string
}

export type StructuredReportScores = {
  technical: number
  expression: number
  logic: number
  overall: number
}

export type StructuredStagePerformance = {
  stageName: InterviewStageName
  score?: number | null
  summary: string
  positiveSignals: string[]
  negativeSignals: string[]
  improvementSuggestions: string[]
}

export type StructuredQuestionReview = {
  stageName: InterviewStageName
  question: string
  answerSummary: string
  score?: number | null
  scoringReason: string
  improvementSuggestion: string
}

export type StructuredTrainingPlan = {
  threeDay: string[]
  sevenDay: string[]
  nextInterviewFocus: string[]
}

export type StructuredInterviewReport = {
  summary: StructuredReportSummary
  scores: StructuredReportScores
  stagePerformances: StructuredStagePerformance[]
  questionReviews: StructuredQuestionReview[]
  strengths: string[]
  weaknesses: string[]
  trainingPlan: StructuredTrainingPlan
  finalAdvice: string
  markdownFallback: string
}

export type InterviewFinishResponse = {
  sessionId?: number
  summaryReport: string
  status?: string
  jobId?: string
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
  score?: number
  hint?: string
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

export type InterviewSessionDetailResponse = {
  sessionId: number
  targetPosition?: string
  status?: string
  currentStage?: InterviewStageName
  summaryReport?: string
  stages: InterviewStageRecord[]
  messages: InterviewMessageRecord[]
  resumeId?: number
  positionId?: number
  jdText?: string
}

export type InterviewChatRequest = {
  content: string
  messages?: InterviewMessageRecord[]
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
  baseUrl?: string
  model: string
  apiKey?: string
  maxTokens?: number
  thinkingDepth?: string
}

export type LlmConfigResponse = {
  providerKey?: string
  baseUrl?: string
  model?: string
  hasApiKey?: boolean
  apiKeyMasked?: string
  providerName?: string
  displayName?: string
  maxTokens?: number
  thinkingDepth?: string
}

export type LlmModelDiscoveryPayload = {
  baseUrl: string
  apiKey?: string
}

export type LlmModelDiscoveryResponse = {
  providerKey: string
  baseUrl: string
  models: string[]
}

export type LlmConfigTestPayload = {
  providerKey?: string
  baseUrl?: string
  model?: string
  apiKey?: string
  maxTokens?: number
  thinkingDepth?: string
}

export type LlmConfigTestResponse = {
  providerKey: string
  model: string
  ok: boolean
  message: string
}

export type UserProfilePayload = {
  username?: string
  email?: string
  oldPassword?: string
  newPassword?: string
  themePreference?: ThemePreference
}

export type UserProfileResponse = {
  username?: string
  email?: string
  avatarUrl?: string
  themePreference?: ThemePreference
}

export type ThemePreference = 'light' | 'dark' | 'system'

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
