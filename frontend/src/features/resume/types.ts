import type { ToolTraceFile, ToolTraceIcon } from '@/shared/ui'

export type ResumeItem = {
  id: number
  fileName: string
  createdAt?: string
  sessionCount?: number
  inUse?: boolean
}

export type ResumeUploadResponse = {
  resumeId: number
  skills?: string[]
  projects?: unknown[]
}

export type ResumeToolStep = {
  id: number
  icon: ToolTraceIcon
  text: string
  state?: 'default' | 'error'
  badge?: string
  badgeTone?: 'add' | 'error' | 'default'
  chips?: string[]
  files?: ToolTraceFile[]
  detail?: string[]
}

export type ResumeToolGroup = {
  id: number
  summary: string
  status: 'running' | 'done'
  steps: ResumeToolStep[]
}

export type ResumeAssistantMessage = {
  id: number
  turnId: number
  content: string
  createdAt: string
  toolCalls?: ResumeToolGroup | null
}

export type ResumeTurn = {
  id: number
  instruction: string
  status: 'queued' | 'running' | 'done'
  createdAt: string
  startedAt?: string | null
  completedAt?: string | null
  messages: ResumeAssistantMessage[]
}

export type ResumeConversation = {
  id: number
  title: string
  resumeId?: number | null
  updatedAt: string
  pinned: boolean
  /** Derived from the conversation's turns: `active` while work is queued, running or not
   *  yet started, `finished` once every turn is done. Mirrors the interview session list's
   *  ongoing/finished split. */
  status: string
}
