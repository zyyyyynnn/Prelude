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
