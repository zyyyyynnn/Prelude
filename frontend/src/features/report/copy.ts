import type { ReportStageName } from './types'

/** Every word the report surface paints. The document's structure is data-driven, but
 *  its section titles are not, so they arrive here instead of being baked into the
 *  markup: the product renders with `reportCopy`, and the component gallery renders the
 *  same components with role labels. One owner for the JSX, one place for the vocabulary. */
export type ReportCopy = {
  eyebrow: string
  title: string
  actionTitle: string
  riskTitle: string
  scores: {
    eyebrow: string
    title: string
    overall: string
    dimensions: readonly [string, string, string]
  }
  stages: {
    eyebrow: string
    title: string
    empty: string
    signals: { positive: string; risk: string; improvement: string }
    stageLabels: Record<ReportStageName, string>
  }
  reviews: {
    eyebrow: string
    title: string
    empty: string
    fields: { summary: string; reason: string; improvement: string }
  }
  traits: {
    eyebrow: string
    title: string
    strengths: string
    strengthsEmpty: string
    weaknesses: string
    weaknessesEmpty: string
  }
  plan: {
    eyebrow: string
    title: string
    groups: readonly [string, string, string]
    fallback: string
  }
  adviceTitle: string
  noScore: string
}

export const reportCopy: ReportCopy = {
  eyebrow: 'Interview Review',
  title: '求职训练报告',
  actionTitle: '行动建议',
  riskTitle: '总体风险',
  scores: {
    eyebrow: '能力画像',
    title: '三维评分',
    overall: '总体',
    dimensions: ['技术能力', '表达清晰度', '逻辑思维'],
  },
  stages: {
    eyebrow: '阶段复盘',
    title: '分阶段表现',
    empty: '当前报告没有可复盘的阶段表现。',
    signals: { positive: '正向信号', risk: '风险信号', improvement: '改进建议' },
    stageLabels: {
      warmup: '破冰',
      technical: '技术问答',
      deep_dive: '深度追问',
      closing: '收尾复盘',
    },
  },
  reviews: {
    eyebrow: '回答证据',
    title: '逐题复盘',
    empty: '当前报告没有可复盘的有效回答。',
    fields: { summary: '回答摘要', reason: '评分依据', improvement: '改进建议' },
  },
  traits: {
    eyebrow: '能力沉淀',
    title: '优势与短板',
    strengths: '核心优势',
    strengthsEmpty: '暂无可归纳的优势。',
    weaknesses: '主要短板',
    weaknessesEmpty: '暂无已沉淀的薄弱点。',
  },
  plan: {
    eyebrow: '下一步行动',
    title: '训练计划',
    groups: ['3 天补强', '7 天专项', '下次模拟重点'],
    fallback: '按逐题复盘中的建议完成一次定向练习。',
  },
  adviceTitle: '总结建议',
  noScore: '暂无评分',
}
