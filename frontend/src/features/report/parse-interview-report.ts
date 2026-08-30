import type {
  ParsedInterviewReport,
  ReportStageName,
  StructuredQuestionReview,
  StructuredStagePerformance,
} from './types'

const stageNames = new Set<ReportStageName>(['warmup', 'technical', 'deep_dive', 'closing'])

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value)
}

function requiredText(value: unknown) {
  return typeof value === 'string' && value.trim() ? value.trim() : null
}

function strings(value: unknown): string[] {
  if (!Array.isArray(value)) return []
  return value
    .filter((item): item is string => typeof item === 'string')
    .map((item) => item.trim())
    .filter(Boolean)
}

function score(value: unknown) {
  return typeof value === 'number' && Number.isFinite(value) && value >= 1 && value <= 10
    ? Math.round(value * 10) / 10
    : null
}

function stageName(value: unknown): ReportStageName | null {
  return typeof value === 'string' && stageNames.has(value as ReportStageName)
    ? (value as ReportStageName)
    : null
}

function stagePerformances(value: unknown): StructuredStagePerformance[] {
  if (!Array.isArray(value)) return []
  return value.filter(isRecord).flatMap((item) => {
    const name = stageName(item.stageName)
    const summary = requiredText(item.summary)
    if (!name || !summary) return []
    return [
      {
        stageName: name,
        score: score(item.score),
        summary,
        positiveSignals: strings(item.positiveSignals),
        negativeSignals: strings(item.negativeSignals),
        improvementSuggestions: strings(item.improvementSuggestions),
      },
    ]
  })
}

function questionReviews(value: unknown): StructuredQuestionReview[] {
  if (!Array.isArray(value)) return []
  return value.filter(isRecord).flatMap((item) => {
    const name = stageName(item.stageName)
    const question = requiredText(item.question)
    const answerSummary = requiredText(item.answerSummary)
    const scoringReason = requiredText(item.scoringReason)
    const improvementSuggestion = requiredText(item.improvementSuggestion)
    if (!name || !question || !answerSummary || !scoringReason || !improvementSuggestion) return []
    return [
      {
        stageName: name,
        question,
        answerSummary,
        score: score(item.score),
        scoringReason,
        improvementSuggestion,
      },
    ]
  })
}

export function parseInterviewReport(source: string): ParsedInterviewReport {
  const raw = source?.trim() || ''
  if (!raw.startsWith('{')) return { kind: 'plain', text: raw }

  try {
    const parsed: unknown = JSON.parse(raw)
    if (!isRecord(parsed) || !isRecord(parsed.summary) || !isRecord(parsed.scores)) {
      return { kind: 'plain', text: raw }
    }
    const summary = parsed.summary
    const dimensions = parsed.scores
    const fitAssessment = requiredText(summary.fitAssessment)
    const actionRecommendation = requiredText(summary.actionRecommendation)
    const overallRisk = requiredText(summary.overallRisk)
    const technical = score(dimensions.technical)
    const expression = score(dimensions.expression)
    const logic = score(dimensions.logic)
    const overall = score(dimensions.overall)
    const finalAdvice = requiredText(parsed.finalAdvice)
    if (
      !fitAssessment ||
      !actionRecommendation ||
      !overallRisk ||
      technical == null ||
      expression == null ||
      logic == null ||
      overall == null ||
      !finalAdvice
    ) {
      return { kind: 'plain', text: raw }
    }

    const plan = isRecord(parsed.trainingPlan) ? parsed.trainingPlan : {}
    return {
      kind: 'structured',
      report: {
        summary: { fitAssessment, actionRecommendation, overallRisk },
        scores: { technical, expression, logic, overall },
        stagePerformances: stagePerformances(parsed.stagePerformances),
        questionReviews: questionReviews(parsed.questionReviews),
        strengths: strings(parsed.strengths),
        weaknesses: strings(parsed.weaknesses),
        trainingPlan: {
          threeDay: strings(plan.threeDay),
          sevenDay: strings(plan.sevenDay),
          nextInterviewFocus: strings(plan.nextInterviewFocus),
        },
        finalAdvice,
      },
    }
  } catch {
    return { kind: 'plain', text: raw }
  }
}
