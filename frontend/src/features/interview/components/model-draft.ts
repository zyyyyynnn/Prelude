import type { InterviewModelConfig } from '../types'

type ModelSourceConfig = InterviewModelConfig & { customEndpointUrl?: string | null }

export type InterviewModelDraft = {
  source: string
  config: InterviewModelConfig
}

export function interviewModelSource(config: ModelSourceConfig) {
  return JSON.stringify({
    provider: config.provider,
    customEndpointUrl: config.customEndpointUrl,
    model: config.model,
    reasoningLevel: config.reasoningLevel,
    capability: config.capability,
  })
}

export function resolveInterviewModelConfig(
  draft: InterviewModelDraft | null,
  source: string,
  globalConfig: InterviewModelConfig | undefined,
) {
  return draft?.source === source ? draft.config : globalConfig
}
