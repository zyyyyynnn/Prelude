import type { AttachmentItem } from '@/features/assets'
import type { InterviewModelConfig, InterviewModelProvider } from '@/features/interview'
import type { Position } from '@/features/position'
import type { ResumeItem } from '@/features/resume'
import type { StructuredInterviewReport } from '@/features/report'

/* Gallery fixtures. Every string names the role it fills, so a screenshot shows where
   content lands without pretending to be a real candidate, a real company or a real
   interview — the same convention the Button panel uses for 主要操作 / 次要操作. */
const resumeSample: ResumeItem = {
  id: 1,
  fileName: '示例简历.pdf',
  createdAt: '2026-01-01T09:00:00',
  sessionCount: 1,
}
const resumeInUseSample: ResumeItem = {
  id: 2,
  fileName: '使用中简历.pdf',
  createdAt: '2026-01-02T09:00:00',
  sessionCount: 3,
  inUse: true,
}

export const sampleResumes: ResumeItem[] = [resumeSample, resumeInUseSample]

const positionSample: Position = { id: 1, name: '示例岗位', editable: true }
/** Carries no `editable`, so the row shows the built-in variant with no edit action. */
const builtinPositionSample: Position = { id: 2, name: '内置岗位' }

export const samplePositions: Position[] = [positionSample, builtinPositionSample]

/** The names the composer rows pin into their context chips, so a label is written once. */
export const sampleContextNames = {
  resumeName: resumeSample.fileName,
  positionName: positionSample.name,
}

/* The gallery has no backend behind it. These are the only stand-ins its composers get:
   an upload reports itself unavailable, which the setup composer already handles as a
   failed upload, and a delete settles without touching anything. */
export function rejectUpload(): Promise<AttachmentItem> {
  return Promise.reject(new Error('实验台不上传文件'))
}

export function ignoreDelete(): Promise<void> {
  return Promise.resolve()
}

export const sampleAttachments: AttachmentItem[] = [
  { id: 11, fileName: '示例文档.pdf', mediaType: 'application/pdf', size: 24576, image: false },
  { id: 12, fileName: '示例截图.png', mediaType: 'image/png', size: 10240, image: true },
]

export const sampleModelConfig: InterviewModelConfig = {
  model: '示例模型',
  provider: 'sample',
  reasoningLevel: 'AUTO',
  capability: {
    model: '示例模型',
    reasoning: true,
    supportedReasoningLevels: ['AUTO', 'LOW', 'MEDIUM', 'HIGH'],
  },
}

export const sampleModelProviders: InterviewModelProvider[] = [
  {
    providerKey: 'sample',
    displayName: '示例服务商',
    models: [
      sampleModelConfig.capability,
      { model: '备选模型' },
      { model: '不可用模型', reasoning: false },
    ],
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
  /** Left empty on purpose: the real page then renders 主要短板's empty state, so the
   *  gallery does not need a second panel to show the same `Trait` twice. */
  weaknesses: [],
  trainingPlan: {
    threeDay: ['三日计划条目一', '三日计划条目二'],
    sevenDay: ['七日计划条目一'],
    nextInterviewFocus: ['下一轮重点一', '下一轮重点二'],
  },
  finalAdvice: '总结建议示例文本，用于检查报告末段的阅读宽度。',
}
