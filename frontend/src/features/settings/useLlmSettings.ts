import { useMemo, useState } from 'react'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { useFeedback } from '@/shared/ui/feedback'
import { discoverCapabilities, discoverModels, saveLlmConfig } from './api'
import {
  getCustomProviderMeta,
  isCustomProvider,
  normalizeCustomBaseUrl,
} from './provider-protocol'
import type {
  LlmConfigPayload,
  LlmConfigResponse,
  ModelCapabilityResponse,
  LlmProviderResponse,
  ReasoningLevel,
} from './types'

export function useLlmSettings(
  config: LlmConfigResponse,
  providers: LlmProviderResponse[],
) {
  const feedback = useFeedback()
  const client = useQueryClient()
  const initialProvider = config.provider
  const [draft, setDraft] = useState<LlmConfigPayload>({
    provider: initialProvider,
    customEndpointUrl: config.customEndpointUrl ?? '',
    model: config.model,
    apiKey: undefined,
    reasoningLevel: config.reasoningLevel,
    maxOutputTokens: config.maxOutputTokens,
    fallbackModels: config.fallbackModels,
  })
  const [models, setModels] = useState<ModelCapabilityResponse[]>(() => {
    const provider = providers.find((item) => item.providerKey === config.provider)
    if (provider?.models.length) return provider.models
    return config.capability.model === config.model ? [config.capability] : []
  })
  const [showKey, setShowKey] = useState(false)
  const [testMessage, setTestMessage] = useState('')

  const custom = isCustomProvider(draft.provider)
  const protocol = getCustomProviderMeta(draft.provider)
  const selectedCapability = models.find((item) => item.model === draft.model)
    ?? (config.provider === draft.provider && config.model === draft.model ? config.capability : undefined)
  const reasoningLevels = selectedCapability?.supportedReasoningLevels ?? []

  const update = <K extends keyof LlmConfigPayload>(key: K, value: LlmConfigPayload[K]) => {
    setDraft((current) => ({ ...current, [key]: value }))
    setTestMessage('')
  }
  const payload = useMemo(
    () => ({
      ...draft,
      customEndpointUrl: custom
        ? normalizeCustomBaseUrl(draft.customEndpointUrl ?? '', draft.provider)
        : undefined,
      apiKey: draft.apiKey?.trim() || undefined,
      reasoningLevel: draft.reasoningLevel,
      fallbackModels: draft.fallbackModels ?? [],
    }),
    [custom, draft],
  )

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
      setModels(() => {
        const provider = providers.find((item) => item.providerKey === result.provider)
        if (provider?.models.length) return provider.models
        return [result.capability]
      })
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
    onError: (_error, model) => {
      setModels((current) => {
        const fallback: ModelCapabilityResponse = {
          provider: draft.provider,
          model,
          reasoning: false,
          structuredOutput: false,
          toolCalling: false,
          streaming: true,
          vision: false,
          multilingual: false,
          longContext: false,
          embedding: false,
          nativeRealtimeVoice: false,
          supportedReasoningLevels: ['AUTO'],
        }
        return [...current.filter((item) => item.model !== model), fallback]
      })
      feedback.notify('模型能力检测失败，仅保留默认思考深度', 'info')
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

  function selectProvider(provider: string) {
    const next = providers.find((item) => item.providerKey === provider)
    setDraft((current) => ({
      ...current,
      provider,
      model: '',
      customEndpointUrl: isCustomProvider(provider) ? '' : undefined,
      apiKey: undefined,
    }))
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
    if (!draft.provider || !draft.model.trim()) {
      feedback.notify('请选择接入方式并填写模型', 'error')
      return false
    }
    if (custom && !payload.customEndpointUrl) {
      feedback.notify('请填写 Base URL', 'error')
      return false
    }
    const level = draft.reasoningLevel ?? 'AUTO'
    if (selectedCapability && !selectedCapability.supportedReasoningLevels.includes(level)) {
      feedback.notify('当前思考深度与所选模型不兼容，请显式选择该模型支持的思考深度', 'error')
      return false
    }
    if (!selectedCapability && level !== 'AUTO') {
      feedback.notify('所选模型能力尚未确认，不能沿用当前思考深度', 'error')
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
    config,
    providers,
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

export const REASONING_LABELS: Record<ReasoningLevel, string> = {
  AUTO: '默认',
  LOW: '低',
  MEDIUM: '中',
  HIGH: '高',
  XHIGH: '超高',
  MAX: '最大',
}
