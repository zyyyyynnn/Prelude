import { describe, expect, it } from 'vite-plus/test'
import { parseInterviewReport } from './parse'

const report = {
  summary: {
    fitAssessment: '适合继续训练',
    actionRecommendation: '继续投递',
    overallRisk: '细节不足',
  },
  scores: { technical: 8, expression: 8, logic: 8, overall: null },
  finalAdvice: '继续练习',
}

describe('parseInterviewReport', () => {
  it('keeps a structured report when no overall score is available', () => {
    const parsed = parseInterviewReport(JSON.stringify(report))

    expect(parsed.kind).toBe('structured')
    if (parsed.kind === 'structured') expect(parsed.report.scores.overall).toBeNull()
  })

  it('rejects a non-null invalid overall score', () => {
    const parsed = parseInterviewReport(
      JSON.stringify({ ...report, scores: { ...report.scores, overall: 'unknown' } }),
    )

    expect(parsed.kind).toBe('plain')
  })
})
