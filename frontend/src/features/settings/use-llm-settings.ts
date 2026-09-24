import { useFeedback } from '@/shared/ui'
import { useMemo, useState } from 'react'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { discoverCapabilities, discoverModels, saveLlmConfig } from './api'
import { buildLlmPayload, createProviderDraft, llmDraftError, providerModels } from './llm-draft'
import { getCustomProviderMeta, isCustomProvider } from './provider-protocol'
import type {
  LlmConfigPayload,
  LlmConfigResponse,
  LlmProviderResponse,
  ModelCapabilityResponse,
} from './types'

/**
 * Everything the LLM settings form needs beyond its own markup: the draft, the model
 * catalog for the selected provider, and the three mutations that write it back.
 *
 * The catalog starts from what the provider published, because a custom endpoint has
 * nothing to publish; probing a model adds to it, and a failed probe leaves it alone —
 * the form then falls back to the capability the server already returned.
 */
export function useLlmSettings(config: LlmConfigResponse, providers: LlmProviderResponse[]) {
  const feedback = useFeedback()
  const client = useQueryClient()
  const [draft, setDraft] = useState<LlmConfigPayload>({
    provider: config.provider,
    customEndpointUrl: config.customEndpointUrl ?? '',
    model: config.model,
    apiKey: undefined,
    reasoningLevel: config.reasoningLevel,
    maxOutputTokens: config.maxOutputTokens,
    fallbackModels: config.fallbackModels,
  })
  const [models, setModels] = useState<ModelCapabilityResponse[]>(() =>
    providerModels(
      providers,
      config.provider,
      config.capability.model === config.model ? [config.capability] : [],
    ),
  )
  const [showKey, setShowKey] = useState(false)
  const [testMessage, setTestMessage] = useState('')

  const custom = isCustomProvider(draft.provider)
  const protocol = getCustomProviderMeta(draft.provider)
  const selectedCapability =
    models.find((item) => item.model === draft.model) ??
    (config.provider === draft.provider && config.model === draft.model
      ? config.capability
      : undefined)
  const reasoningLevels = selectedCapability?.supportedReasoningLevels ?? []

  const update = <K extends keyof LlmConfigPayload>(key: K, value: LlmConfigPayload[K]) => {
    setDraft((current) => ({ ...current, [key]: value }))
    setTestMessage('')
  }
  const payload = useMemo(() => buildLlmPayload(draft, custom), [custom, draft])

  const save = useMutation({
    mutationFn: () => saveLlmConfig(payload),
    onSuccess: (result) => {
      client.setQueryData(['llm-config'], result)
      setDraft((current) => ({
        ...current,
        apiKey: undefined,
        customEndpointUrl: result.customEndpointUrl ?? '',
        model: result.model,
        reasoningLevel: result.reasoningLevel,
        maxOutputTokens: result.maxOutputTokens,
        fallbackModels: result.fallbackModels,
      }))
      setModels(() => providerModels(providers, result.provider, [result.capability]))
      feedback.notify('LLM 配置已保存', 'success')
    },
    onError: (error) => feedback.notify(error.message, 'error'),
  })
  const capabilityProbe = useMutation({
    mutationFn: (model: string) =>
      discoverCapabilities({
        provider: draft.provider,
        baseUrl: payload.customEndpointUrl ?? '',
        apiKey: payload.apiKey,
        model,
      }),
    onSuccess: (capability) => {
      setModels((current) => {
        const withoutCurrent = current.filter((item) => item.model !== capability.model)
        return [...withoutCurrent, capability]
      })
    },
    onError: () => {
      feedback.notify('模型能力检测失败；未确认能力前仅可使用服务端已返回的能力', 'info')
    },
  })
  const discover = useMutation({
    mutationFn: () =>
      discoverModels({
        provider: draft.provider,
        baseUrl: payload.customEndpointUrl ?? '',
        apiKey: payload.apiKey,
      }),
    onSuccess: (result) => {
      setModels(result.models)
      setDraft((current) => ({ ...current, customEndpointUrl: result.baseUrl }))
      feedback.notify(
        result.models.length ? '模型列表已更新' : '未读取到模型，可手动填写模型 ID',
        result.models.length ? 'success' : 'info',
      )
    },
    onError: (error) => feedback.notify(error.message, 'error'),
  })

  function selectProvider(providerKey: string) {
    const next = providers.find((item) => item.providerKey === providerKey)
    setDraft((current) => createProviderDraft(current, providerKey))
    setModels(next?.models ?? [])
    setTestMessage('')
  }
  function selectModel(model: string) {
    update('model', model)
    if (isCustomProvider(draft.provider) && model.trim() && payload.customEndpointUrl) {
      capabilityProbe.mutate(model.trim())
    }
  }
  function validate() {
    const error = llmDraftError({
      provider: draft.provider,
      model: draft.model,
      custom,
      customEndpointUrl: payload.customEndpointUrl,
      reasoningLevel: draft.reasoningLevel,
      capability: selectedCapability,
    })
    if (error) {
      feedback.notify(error, 'error')
      return false
    }
    return true
  }
  function validateDiscovery() {
    if (!custom || !payload.customEndpointUrl) {
      feedback.notify('请先填写 Base URL', 'error')
      return false
    }
    return true
  }
  return {
    protocol,
    custom,
    draft,
    models,
    selectedCapability,
    reasoningLevels,
    showKey,
    testMessage,
    saving: save.isPending,
    discovering: discover.isPending,
    update,
    selectProvider,
    selectModel,
    setShowKey,
    save: () => validate() && save.mutate(),
    discover: () => {
      if (!protocol?.modelDiscovery) return
      if (validateDiscovery()) discover.mutate()
    },
  }
}
