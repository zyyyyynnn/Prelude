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

export type ResumeProfile = {
  fullName: string
  email: string
  phone: string
  targetRole: string
}

export type ResumeSkill = { name: string; level: string }
export type ResumeExperience = {
  company: string
  title: string
  start: string
  end: string
  bullets: string[]
}
export type ResumeProject = {
  name: string
  role: string
  techStack: string[]
  bullets: string[]
  outcome: string
}
export type ResumeEducation = { school: string; degree: string; end: string }

export type ResumeDocument = {
  schemaVersion: 1
  locale: string
  profile: ResumeProfile | null
  summary: string
  skills: ResumeSkill[]
  experiences: ResumeExperience[]
  projects: ResumeProject[]
  education: ResumeEducation[]
  extras: string[]
}

export type ResumeDocumentView = {
  resumeId: number
  fileName: string
  documentVersion: number
  sourceType: string
  document: ResumeDocument
}

export type ResumeImprovementStatus = 'pending' | 'accepted' | 'rejected'

export type ResumeImprovement = {
  id: number
  resumeId: number
  sessionId: number
  targetPath: string
  currentText: string
  proposedText: string
  rationale: string
  evidence: string
  baseDocumentVersion: number
  status: ResumeImprovementStatus
  appliedDocumentVersion?: number | null
}

export type ResumeImprovementDecision = {
  improvement: ResumeImprovement
  resume: ResumeDocumentView
}
