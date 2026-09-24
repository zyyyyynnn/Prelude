import type { ReasoningLevel } from '@/features/settings'

export type InterviewAttachment = {
  id: number
  fileName: string
  mediaType: string
  size: number
  image: boolean
}

export type InterviewStageName = 'warmup' | 'technical' | 'deep_dive' | 'closing'

export type InterviewStartPayload = {
  resumeId: number
  positionId: number
  jdText?: string
  requestedModel?: string
  attachmentIds?: number[]
}

export type InterviewStartResponse = {
  sessionId: number
  currentStage?: InterviewStageName
}

export type InterviewFinishResponse = {
  sessionId?: number
  summaryReport: string
  status?: InterviewSessionStatus
  jobId?: string
}

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

export type InterviewSessionStatus = 'ongoing' | 'generating' | 'finished'

export type InterviewSessionItem = {
  sessionId: number
  targetPosition?: string
  positionName?: string
  status?: InterviewSessionStatus
  currentStage?: InterviewStageName
  createdAt?: string
  summaryReport?: string
  pinned?: boolean
}

export type InterviewSessionDetailResponse = {
  sessionId: number
  targetPosition?: string
  status?: InterviewSessionStatus
  currentStage?: InterviewStageName
  model?: string
  reasoningLevel?: string
  summaryReport?: string
  stages: InterviewStageRecord[]
  messages: InterviewMessageRecord[]
  resumeId?: number
  positionId?: number
  jdText?: string
  attachments: InterviewAttachment[]
}

export type InterviewChatRequest = {
  content: string
  messages?: InterviewMessageRecord[]
}

export type InterviewModelCapability = {
  model: string
  reasoning?: boolean
  supportedReasoningLevels?: ReasoningLevel[]
}

export type InterviewModelConfig = {
  model: string
  provider: string
  reasoningLevel: ReasoningLevel
  capability: InterviewModelCapability
}

export type InterviewModelProvider = {
  providerKey: string
  displayName?: string
  models: InterviewModelCapability[]
}
