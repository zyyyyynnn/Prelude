export type ReportStageName = 'warmup' | 'technical' | 'deep_dive' | 'closing'

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
  stageName: ReportStageName
  score?: number | null
  summary: string
  positiveSignals: string[]
  negativeSignals: string[]
  improvementSuggestions: string[]
}

export type StructuredQuestionReview = {
  stageName: ReportStageName
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

export type ParsedInterviewReport =
  | { kind: 'structured'; report: StructuredInterviewReport }
  | { kind: 'markdown'; markdown: string }
