import { isCustomProvider, normalizeCustomBaseUrl } from './provider-protocol'
import type {
  LlmConfigPayload,
  LlmProviderResponse,
  ModelCapabilityResponse,
  ReasoningLevel,
} from './types'

/** Providers publish their model catalog; the saved capability is the fallback for custom endpoints. */
export function providerModels(
  providers: LlmProviderResponse[],
  providerKey: string,
  fallback: ModelCapabilityResponse[],
) {
  const provider = providers.find((item) => item.providerKey === providerKey)
  return provider?.models.length ? provider.models : fallback
}

/** First blocking draft error, or null when the draft can be saved. */
export function llmDraftError({
  provider,
  model,
  custom,
  customEndpointUrl,
  reasoningLevel,
  capability,
}: {
  provider: string
  model: string
  custom: boolean
  customEndpointUrl?: string | null
  reasoningLevel?: ReasoningLevel | null
  capability?: ModelCapabilityResponse
}) {
  if (!provider || !model.trim()) return '请选择接入方式并填写模型'
  if (custom && !customEndpointUrl) return '请填写 Base URL'
  const level = reasoningLevel ?? 'AUTO'
  if (capability && !capability.supportedReasoningLevels.includes(level))
    return '当前思考深度与所选模型不兼容，请显式选择该模型支持的思考深度'
  if (!capability && level !== 'AUTO') return '所选模型能力尚未确认，不能沿用当前思考深度'
  return null
}

/**
 * The draft as the API wants it: the endpoint normalised to a root URL, a blank key
 * meaning "keep the stored one", and the fallback list always present.
 */
export function buildLlmPayload(draft: LlmConfigPayload, custom: boolean): LlmConfigPayload {
  return {
    ...draft,
    customEndpointUrl: custom
      ? normalizeCustomBaseUrl(draft.customEndpointUrl ?? '', draft.provider)
      : undefined,
    apiKey: draft.apiKey?.trim() || undefined,
    reasoningLevel: draft.reasoningLevel,
    fallbackModels: draft.fallbackModels ?? [],
  }
}

/** Switching provider keeps the untouched fields and clears everything provider-specific. */
export function createProviderDraft(current: LlmConfigPayload, provider: string): LlmConfigPayload {
  return {
    ...current,
    provider,
    model: '',
    customEndpointUrl: isCustomProvider(provider) ? '' : undefined,
    apiKey: undefined,
  }
}
