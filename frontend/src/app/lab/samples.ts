import type { ReportCopy } from '@/features/report'
import type { AttachmentItem } from '@/features/assets'
import type { InterviewModelConfig, InterviewModelProvider } from '@/features/interview'
import type { Position } from '@/features/position'
import type { ResumeItem } from '@/features/resume'
import type { StructuredInterviewReport } from '@/features/report'

/* Gallery fixtures. Every string names the role it fills — the data a component is handed,
   and the copy the gallery supplies through a prop — so a screenshot shows where content
   lands without pretending to be a real candidate, a real company or a real feature. Words
   a product component welds into itself are not sample data and stay as they are. */

const resumeSample: ResumeItem = {
  id: 1,
  fileName: '示例文件一.pdf',
  createdAt: '2026-01-01T09:00:00',
  sessionCount: 1,
}
const resumeInUseSample: ResumeItem = {
  id: 2,
  fileName: '示例文件二.pdf',
  createdAt: '2026-01-02T09:00:00',
  sessionCount: 3,
  inUse: true,
}

export const sampleResumes: ResumeItem[] = [resumeSample, resumeInUseSample]

const positionSample: Position = { id: 1, name: '示例条目一', editable: true }
/** Carries no `editable`, so the row shows the read-only variant with no edit action. */
const builtinPositionSample: Position = { id: 2, name: '示例条目二' }

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
  { id: 11, fileName: '示例文件三.pdf', mediaType: 'application/pdf', size: 24576, image: false },
  { id: 12, fileName: '示例图片一.png', mediaType: 'image/png', size: 10240, image: true },
]

export const sampleModelName = '示例模型一'

export const sampleModelConfig: InterviewModelConfig = {
  model: sampleModelName,
  provider: 'sample',
  reasoningLevel: 'AUTO',
  capability: {
    model: sampleModelName,
    reasoning: true,
    supportedReasoningLevels: ['AUTO', 'LOW', 'MEDIUM', 'HIGH'],
  },
}

export const sampleModelProviders: InterviewModelProvider[] = [
  {
    providerKey: 'sample',
    displayName: '示例条目一',
    models: [
      sampleModelConfig.capability,
      { model: '示例模型二' },
      /** `reasoning: false` — the model menu hides its thinking submenu for this entry. */
      { model: '示例模型三', reasoning: false },
    ],
  },
]

/** The report is a document specimen, so its section titles come from here too: the
 *  product renders `reportCopy`, the gallery passes this over it. */
export const sampleReportCopy: ReportCopy = {
  eyebrow: '样张引导',
  title: '样张标题',
  actionTitle: '小节一',
  riskTitle: '小节二',
  scores: {
    eyebrow: '小节引导',
    title: '小节三',
    overall: '标签',
    dimensions: ['条目一', '条目二', '条目三'],
  },
  stages: {
    eyebrow: '小节引导',
    title: '小节四',
    empty: '空态示例文本。',
    signals: { positive: '条目一', risk: '条目二', improvement: '条目三' },
    stageLabels: {
      warmup: '数据一',
      technical: '数据二',
      deep_dive: '数据三',
      closing: '数据四',
    },
  },
  reviews: {
    eyebrow: '小节引导',
    title: '小节五',
    empty: '空态示例文本。',
    fields: { summary: '字段一', reason: '字段二', improvement: '字段三' },
  },
  traits: {
    eyebrow: '小节引导',
    title: '小节六',
    strengths: '条目一',
    strengthsEmpty: '空态示例文本。',
    weaknesses: '条目二',
    weaknessesEmpty: '空态示例文本。',
  },
  plan: {
    eyebrow: '小节引导',
    title: '小节七',
    groups: ['条目一', '条目二', '条目三'],
    fallback: '示例文本一。',
  },
  adviceTitle: '小节八',
  noScore: '空值示例文本',
}

/** Prose length varies on purpose — that is what shows wrapping and measure. The sentences
 *  never explain what the reviewer is supposed to be looking at. */
export const sampleReport: StructuredInterviewReport = {
  summary: {
    fitAssessment: '示例文本一，长度用来检查首屏段落在阅读宽度下的换行。示例文本二。示例文本三。',
    actionRecommendation: '示例文本一。示例文本二。',
    overallRisk: '示例文本一。示例文本二。',
  },
  scores: { technical: 6, expression: 7, logic: 6, overall: 6.3 },
  stagePerformances: [
    {
      stageName: 'warmup',
      score: 6.5,
      summary: '示例文本一，长度用来检查阶段卡的换行。示例文本二。示例文本三。',
      positiveSignals: ['示例文本一', '示例文本二'],
      negativeSignals: ['示例文本一'],
      improvementSuggestions: ['示例文本一'],
    },
    {
      stageName: 'technical',
      score: 6,
      summary: '示例文本一。示例文本二。',
      positiveSignals: ['示例文本一'],
      negativeSignals: ['示例文本一', '示例文本二'],
      improvementSuggestions: ['示例文本一', '示例文本二'],
    },
    {
      stageName: 'deep_dive',
      score: null,
      summary: '示例文本一。示例文本二。',
      positiveSignals: [],
      negativeSignals: [],
      improvementSuggestions: ['示例文本一'],
    },
    {
      stageName: 'closing',
      score: 7,
      summary: '示例文本一。示例文本二。',
      positiveSignals: ['示例文本一'],
      negativeSignals: ['示例文本一'],
      improvementSuggestions: ['示例文本一'],
    },
  ],
  questionReviews: [
    {
      stageName: 'technical',
      question: '示例文本一，长度用来检查标题占两行时的排布。',
      answerSummary: '示例文本一。示例文本二。',
      score: 6,
      scoringReason: '示例文本一。示例文本二。',
      improvementSuggestion: '示例文本一。示例文本二。',
    },
    {
      stageName: 'deep_dive',
      question: '示例文本一。',
      answerSummary: '示例文本一。示例文本二。',
      score: null,
      scoringReason: '示例文本一。示例文本二。',
      improvementSuggestion: '示例文本一。示例文本二。',
    },
  ],
  strengths: ['示例文本一', '示例文本二'],
  /** Left empty on purpose: the report then renders the second trait's empty state, so the
   *  gallery does not need a second panel to show the same component twice. */
  weaknesses: [],
  trainingPlan: {
    threeDay: ['示例文本一', '示例文本二'],
    sevenDay: ['示例文本一'],
    nextInterviewFocus: ['示例文本一', '示例文本二'],
  },
  finalAdvice: '示例文本一，长度用来检查末段在报告阅读宽度下的行长与两端对齐。示例文本二。',
}
