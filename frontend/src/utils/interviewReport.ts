import type {
  InterviewStageName,
  StructuredInterviewReport,
  StructuredQuestionReview,
  StructuredStagePerformance,
} from '../api/contracts'

export type ParsedInterviewReport =
  | { kind: 'structured'; report: StructuredInterviewReport }
  | { kind: 'markdown'; markdown: string }

const stageNames: InterviewStageName[] = ['warmup', 'technical', 'deep_dive', 'closing']

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value)
}

function text(value: unknown, fallback: string) {
  return typeof value === 'string' && value.trim() ? value.trim() : fallback
}

function strings(value: unknown): string[] {
  if (!Array.isArray(value)) return []
  return value
    .filter((item): item is string => typeof item === 'string')
    .map((item) => item.trim())
    .filter(Boolean)
}

function score(value: unknown, fallback: number) {
  if (typeof value !== 'number' || !Number.isFinite(value)) return fallback
  return Math.max(1, Math.min(10, Math.round(value * 10) / 10))
}

function optionalScore(value: unknown) {
  return typeof value === 'number' && Number.isFinite(value) ? score(value, 6) : null
}

function stageName(value: unknown): InterviewStageName {
  return stageNames.includes(value as InterviewStageName) ? (value as InterviewStageName) : 'warmup'
}

function stagePerformances(value: unknown): StructuredStagePerformance[] {
  if (!Array.isArray(value)) return []
  return value.filter(isRecord).map((item) => ({
    stageName: stageName(item.stageName),
    score: optionalScore(item.score),
    summary: text(item.summary, '本阶段暂无补充总结'),
    positiveSignals: strings(item.positiveSignals),
    negativeSignals: strings(item.negativeSignals),
    improvementSuggestions: strings(item.improvementSuggestions),
  }))
}

function questionReviews(value: unknown): StructuredQuestionReview[] {
  if (!Array.isArray(value)) return []
  return value.filter(isRecord).map((item) => ({
    stageName: stageName(item.stageName),
    question: text(item.question, '未记录问题文本'),
    answerSummary: text(item.answerSummary, '未记录有效回答'),
    score: optionalScore(item.score),
    scoringReason: text(item.scoringReason, '暂无评分依据'),
    improvementSuggestion: text(item.improvementSuggestion, '结合岗位要求继续完善回答。'),
  }))
}

export function parseInterviewReport(source: string): ParsedInterviewReport {
  const raw = source?.trim() || ''
  if (!raw.startsWith('{')) {
    return { kind: 'markdown', markdown: raw }
  }

  try {
    const parsed: unknown = JSON.parse(raw)
    if (!isRecord(parsed) || !isRecord(parsed.summary) || !isRecord(parsed.scores)) {
      return { kind: 'markdown', markdown: raw }
    }
    const dimensions = parsed.scores
    const technical = score(dimensions.technical, 6)
    const expression = score(dimensions.expression, 6)
    const logic = score(dimensions.logic, 6)
    const plan = isRecord(parsed.trainingPlan) ? parsed.trainingPlan : {}
    const summary = parsed.summary
    const markdownFallback = text(
      parsed.markdownFallback,
      '# 面试训练报告\n\n结构化字段不完整，请查看逐题复盘。',
    )
    return {
      kind: 'structured',
      report: {
        summary: {
          fitAssessment: text(summary.fitAssessment, '建议结合岗位要求继续评估'),
          actionRecommendation: text(summary.actionRecommendation, '针对薄弱项训练后再次模拟'),
          overallRisk: text(summary.overallRisk, '现有信息不足，需结合逐题表现判断'),
        },
        scores: {
          technical,
          expression,
          logic,
          overall: score(dimensions.overall, (technical + expression + logic) / 3),
        },
        stagePerformances: stagePerformances(parsed.stagePerformances),
        questionReviews: questionReviews(parsed.questionReviews),
        strengths: strings(parsed.strengths),
        weaknesses: strings(parsed.weaknesses),
        trainingPlan: {
          threeDay: strings(plan.threeDay),
          sevenDay: strings(plan.sevenDay),
          nextInterviewFocus: strings(plan.nextInterviewFocus),
        },
        finalAdvice: text(parsed.finalAdvice, '保持复盘，并围绕薄弱项继续专项训练。'),
        markdownFallback,
      },
    }
  } catch {
    return { kind: 'markdown', markdown: raw }
  }
}
