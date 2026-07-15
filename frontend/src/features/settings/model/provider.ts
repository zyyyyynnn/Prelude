import type { LlmProviderOption, LlmProviderResponse } from './types'

export function mapProviderResponses(providers: LlmProviderResponse[]): LlmProviderOption[] {
  return providers.map((provider) => {
    if (
      typeof provider.providerKey !== 'string' ||
      typeof provider.displayName !== 'string' ||
      !Array.isArray(provider.availableModels) ||
      !provider.availableModels.every((model) => typeof model === 'string') ||
      typeof provider.enabled !== 'number'
    ) {
      throw new Error('Invalid LLM provider response')
    }

    return {
      providerKey: provider.providerKey,
      displayName: provider.displayName,
      models: provider.availableModels,
    }
  })
}
