import { useMemo, useState } from 'react'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { useFeedback } from '@/shared/ui/feedback'
import { discoverModels, saveLlmConfig } from './api'
import {
  getCustomProviderMeta,
  isCustomProvider,
  normalizeCustomBaseUrl,
} from './provider-protocol'
import type {
  LlmConfigPayload,
  LlmConfigResponse,
  LlmProviderResponse,
  ReasoningLevel,
} from './types'

export const PROVIDER_LABELS: Record<string, string> = {
  deepseek: 'DeepSeek',
  openai: 'OpenAI',
  anthropic: 'Anthropic',
  'openai-compatible': 'OpenAI 兼容端点',
}

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
    fallbackModels: config.fallbackModels,
  })
  const [models, setModels] = useState<string[]>(() => {
    const provider = providers.find((item) => item.providerKey === config.provider)
    return provider?.availableModels ?? []
  })
  const [showKey, setShowKey] = useState(false)
  const [testMessage, setTestMessage] = useState('')

  const custom = isCustomProvider(draft.provider)
  const protocol = getCustomProviderMeta(draft.provider)
  const reasoningLevels = config.supportedReasoningLevels

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
      reasoningLevel: config.reasoningSupported ? draft.reasoningLevel : null,
      fallbackModels: draft.fallbackModels ?? [],
    }),
    [config.reasoningSupported, custom, draft],
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
        fallbackModels: result.fallbackModels,
      }))
      feedback.notify('LLM 配置已保存', 'success')
    },
    onError: (error) => feedback.notify(error.message, 'error'),
  })
  const discover = useMutation({
    mutationFn: () =>
      discoverModels({
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
    setModels(next?.availableModels ?? [])
    setTestMessage('')
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
    return true
  }
  return {
    config,
    providers: providers.filter((item) => item.enabled === 1),
    providerLabels: PROVIDER_LABELS,
    protocol,
    custom,
    draft,
    models,
    reasoningLevels,
    showKey,
    testMessage,
    saving: save.isPending,
    discovering: discover.isPending,
    update,
    selectProvider,
    setShowKey,
    save: () => validate() && save.mutate(),
    discover: () => {
      if (!protocol?.modelDiscovery) return
      if (validate()) discover.mutate()
    },
  }
}

export const REASONING_LABELS: Record<ReasoningLevel, string> = {
  AUTO: '默认',
  LOW: '低',
  MEDIUM: '中',
  HIGH: '高',
}
