import type { InterviewStageName } from '../api/contracts'

export const STAGE_ORDER: InterviewStageName[] = ['warmup', 'technical', 'deep_dive', 'closing']

const STAGE_LABELS: Record<InterviewStageName, string> = {
  warmup: '破冰',
  technical: '技术',
  deep_dive: '深挖',
  closing: '收尾',
}

export function stageLabel(stage?: InterviewStageName) {
  if (!stage) {
    return '未开始'
  }
  return STAGE_LABELS[stage]
}

export function nextStage(stage?: InterviewStageName): InterviewStageName | null {
  const currentIndex = stage ? STAGE_ORDER.indexOf(stage) : -1
  if (currentIndex === -1) {
    return STAGE_ORDER[0]
  }
  return STAGE_ORDER[currentIndex + 1] ?? null
}

export function isSameOrPreviousStage(base: InterviewStageName, target: InterviewStageName) {
  return STAGE_ORDER.indexOf(target) <= STAGE_ORDER.indexOf(base)
}
