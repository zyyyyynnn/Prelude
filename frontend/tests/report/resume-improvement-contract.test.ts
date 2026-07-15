import assert from 'node:assert/strict'
import test from 'node:test'

import { parseInterviewReport } from '../../src/features/report/lib/parseInterviewReport.ts'

const baseReport = {
  summary: {
    fitAssessment: '匹配',
    actionRecommendation: '继续训练',
    overallRisk: '边界表达不足',
  },
  scores: { technical: 7, expression: 8, logic: 7, overall: 7.3 },
}

void test('parses evidence-backed resume improvements from the report contract', () => {
  const parsed = parseInterviewReport(
    JSON.stringify({
      ...baseReport,
      resumeImprovements: [
        {
          id: 11,
          resumeId: 5,
          sessionId: 7,
          targetPath: 'projects[0].bullets[0]',
          currentText: '负责接口开发',
          proposedText: '将接口 P95 降至 180ms',
          rationale: '补充量化结果',
          evidence: '通过缓存和批处理降低接口耗时',
          baseDocumentVersion: 2,
          status: 'pending',
        },
      ],
    }),
  )

  assert.equal(parsed.kind, 'structured')
  if (parsed.kind === 'structured') {
    assert.deepEqual(parsed.report.resumeImprovements, [
      {
        id: 11,
        resumeId: 5,
        sessionId: 7,
        targetPath: 'projects[0].bullets[0]',
        currentText: '负责接口开发',
        proposedText: '将接口 P95 降至 180ms',
        rationale: '补充量化结果',
        evidence: '通过缓存和批处理降低接口耗时',
        baseDocumentVersion: 2,
        status: 'pending',
      },
    ])
  }
})

void test('drops malformed or unknown improvement states', () => {
  const parsed = parseInterviewReport(
    JSON.stringify({
      ...baseReport,
      resumeImprovements: [{ id: 11, resumeId: 5, sessionId: 7, status: 'applied' }],
    }),
  )

  assert.equal(parsed.kind, 'structured')
  if (parsed.kind === 'structured') assert.deepEqual(parsed.report.resumeImprovements, [])
})
