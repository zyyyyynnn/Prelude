import { describe, expect, it } from 'vite-plus/test'
import type { InterviewModelConfig } from '../types'
import {
  interviewModelSource,
  resolveInterviewModelConfig,
  type InterviewModelDraft,
} from './model-draft'

const globalConfig: InterviewModelConfig & {
  customEndpointUrl: string | null
  fallbackModels: string[]
  maxOutputTokens: number
  hasApiKey?: boolean
  apiKeyMasked?: string | null
} = {
  provider: 'deepseek',
  customEndpointUrl: null,
  model: 'deepseek-v4-pro',
  reasoningLevel: 'AUTO',
  capability: {
    model: 'deepseek-v4-pro',
    reasoning: true,
    supportedReasoningLevels: ['AUTO', 'LOW', 'HIGH', 'MAX'],
  },
  fallbackModels: [],
  maxOutputTokens: 4096,
}

describe('interview model draft', () => {
  it('preserves the draft when unrelated model configuration changes', () => {
    const source = interviewModelSource(globalConfig)
    const draft: InterviewModelDraft = {
      source,
      config: { ...globalConfig, model: 'deepseek-v4-flash' },
    }
    const updated = {
      ...globalConfig,
      fallbackModels: ['deepseek-v4-flash'],
      maxOutputTokens: 8192,
      hasApiKey: true,
      apiKeyMasked: 'sk-***',
    }

    expect(interviewModelSource(updated)).toBe(source)
    expect(resolveInterviewModelConfig(draft, interviewModelSource(updated), updated)).toBe(
      draft.config,
    )
  })

  it('discards the draft when a source field changes', () => {
    const source = interviewModelSource(globalConfig)
    const draft: InterviewModelDraft = {
      source,
      config: { ...globalConfig, model: 'deepseek-v4-flash' },
    }
    const updated = { ...globalConfig, customEndpointUrl: 'https://models.example.com' }

    expect(interviewModelSource(updated)).not.toBe(source)
    expect(resolveInterviewModelConfig(draft, interviewModelSource(updated), updated)).toBe(updated)
  })
})
