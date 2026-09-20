import type { ResumeItem } from '@/features/resume'
import type { StructuredInterviewReport } from '@/features/report'

/* Gallery fixtures. Every string names the role it fills, so a screenshot shows where
   content lands without pretending to be a real candidate, a real company or a real
   interview — the same convention the Button panel uses for 主要操作 / 次要操作. */
export const sampleResumes: ResumeItem[] = [
  { id: 1, fileName: '示例简历.pdf', createdAt: '2026-01-01T09:00:00', sessionCount: 1 },
  {
    id: 2,
    fileName: '使用中简历.pdf',
    createdAt: '2026-01-02T09:00:00',
    sessionCount: 3,
    inUse: true,
  },
]

export const sampleReport: StructuredInterviewReport = {
  summary: {
    fitAssessment: '岗位匹配度评估示例文本，用于检查报告首屏的行长与阅读宽度。',
    actionRecommendation: '行动建议示例文本。',
    overallRisk: '总体风险示例文本。',
  },
  scores: { technical: 6, expression: 7, logic: 6, overall: 6.3 },
  stagePerformances: [
    {
      stageName: 'warmup',
      score: 6.5,
      summary: '阶段小结示例文本，用于检查阶段卡的换行。',
      positiveSignals: ['正向信号一', '正向信号二'],
      negativeSignals: ['风险信号一'],
      improvementSuggestions: ['改进建议一'],
    },
    {
      stageName: 'technical',
      score: 6,
      summary: '阶段小结示例文本。',
      positiveSignals: ['正向信号一'],
      negativeSignals: ['风险信号一', '风险信号二'],
      improvementSuggestions: ['改进建议一', '改进建议二'],
    },
    {
      stageName: 'deep_dive',
      score: null,
      summary: '阶段小结示例文本，本阶段暂无评分。',
      positiveSignals: [],
      negativeSignals: [],
      improvementSuggestions: ['改进建议一'],
    },
    {
      stageName: 'closing',
      score: 7,
      summary: '阶段小结示例文本。',
      positiveSignals: ['正向信号一'],
      negativeSignals: ['风险信号一'],
      improvementSuggestions: ['改进建议一'],
    },
  ],
  questionReviews: [
    {
      stageName: 'technical',
      question: '示例问题一：请说明该场景下的处理思路。',
      answerSummary: '回答要点示例文本。',
      score: 6,
      scoringReason: '评分理由示例文本。',
      improvementSuggestion: '改进建议示例文本。',
    },
    {
      stageName: 'deep_dive',
      question: '示例问题二：请补充该方案的边界条件。',
      answerSummary: '回答要点示例文本。',
      score: null,
      scoringReason: '评分理由示例文本。',
      improvementSuggestion: '改进建议示例文本。',
    },
  ],
  strengths: ['优势条目一', '优势条目二'],
  weaknesses: ['短板条目一'],
  trainingPlan: {
    threeDay: ['三日计划条目一', '三日计划条目二'],
    sevenDay: ['七日计划条目一'],
    nextInterviewFocus: ['下一轮重点一', '下一轮重点二'],
  },
  finalAdvice: '总结建议示例文本，用于检查报告末段的阅读宽度。',
}
