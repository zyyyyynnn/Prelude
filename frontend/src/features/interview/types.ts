import type { AttachmentItem } from '@/features/assets'

export type InterviewStageName = 'warmup' | 'technical' | 'deep_dive' | 'closing'

export type InterviewStartPayload = {
  resumeId: number
  positionId: number
  jdText?: string
  llmModel?: string
  attachmentIds?: number[]
}

export type InterviewStartResponse = {
  sessionId: number
  currentStage?: InterviewStageName
}

export type InterviewFinishResponse = {
  sessionId?: number
  summaryReport: string
  status?: string
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

export type InterviewSessionItem = {
  sessionId: number
  targetPosition?: string
  positionName?: string
  status?: string
  currentStage?: InterviewStageName
  llmProvider?: string
  llmModel?: string
  llmThinkingDepth?: string
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
  llmThinkingDepth?: string
  attachments: AttachmentItem[]
}

export type InterviewChatRequest = {
  content: string
  messages?: InterviewMessageRecord[]
}
