import { useMemo, useState } from 'react'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { useFeedback } from '@/shared/ui/feedback'
import { discoverModels, saveLlmConfig, testLlmConfig } from './api'
import {
  getCustomProviderMeta,
  isCustomProvider,
  normalizeCustomBaseUrl,
} from './provider-protocol'
import type { LlmConfigPayload, LlmConfigResponse, LlmProviderResponse } from './types'

export function useLlmSettings(
  config: LlmConfigResponse,
  providers: LlmProviderResponse[],
  requestedProviderKey?: string,
) {
  const feedback = useFeedback()
  const client = useQueryClient()
  const initialProviderKey = providers.some((item) => item.providerKey === requestedProviderKey)
    ? requestedProviderKey!
    : config.providerKey
  const initialProvider = providers.find((item) => item.providerKey === initialProviderKey)
  const currentProviderSelected = initialProviderKey === config.providerKey
  const [draft, setDraft] = useState<LlmConfigPayload>({
    providerKey: initialProviderKey,
    baseUrl: currentProviderSelected ? (config.baseUrl ?? '') : '',
    model: currentProviderSelected ? config.model : (initialProvider?.availableModels[0] ?? ''),
    maxTokens: config.maxTokens ?? undefined,
    thinkingDepth: config.thinkingDepth,
  })
  const [models, setModels] = useState<string[]>(
    () => initialProvider?.availableModels ?? [],
  )
  const [showKey, setShowKey] = useState(false)
  const [testMessage, setTestMessage] = useState('')

  const provider = providers.find((item) => item.providerKey === draft.providerKey)
  const protocol = getCustomProviderMeta(draft.providerKey)
  const custom = isCustomProvider(draft.providerKey)
  const update = <K extends keyof LlmConfigPayload>(key: K, value: LlmConfigPayload[K]) => {
    setDraft((current) => ({ ...current, [key]: value }))
    setTestMessage('')
  }
  const payload = useMemo(
    () => ({
      ...draft,
      baseUrl: custom ? normalizeCustomBaseUrl(draft.baseUrl ?? '', draft.providerKey) : undefined,
      apiKey: draft.apiKey?.trim() || undefined,
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
        baseUrl: result.baseUrl ?? '',
        model: result.model,
        maxTokens: result.maxTokens ?? undefined,
        thinkingDepth: result.thinkingDepth,
      }))
      feedback.notify('LLM 配置已保存', 'success')
    },
    onError: (error) => feedback.notify(error.message, 'error'),
  })
  const test = useMutation({
    mutationFn: () => testLlmConfig(payload),
    onSuccess: (result) => {
      setTestMessage(result.message)
      feedback.notify(result.message, result.ok ? 'success' : 'error')
    },
    onError: (error) => {
      setTestMessage(error.message)
      feedback.notify(error.message, 'error')
    },
  })
  const discover = useMutation({
    mutationFn: () =>
      discoverModels({
        providerKey: draft.providerKey,
        baseUrl: payload.baseUrl ?? '',
        apiKey: payload.apiKey,
      }),
    onSuccess: (result) => {
      setModels(result.models)
      setDraft((current) => ({ ...current, baseUrl: result.baseUrl }))
      feedback.notify(
        result.models.length ? '模型列表已更新' : '未读取到模型，可手动填写模型 ID',
        result.models.length ? 'success' : 'info',
      )
    },
    onError: (error) => feedback.notify(error.message, 'error'),
  })

  function selectProvider(providerKey: string) {
    const next = providers.find((item) => item.providerKey === providerKey)
    setDraft((current) => ({
      ...current,
      providerKey,
      model: '',
      baseUrl: isCustomProvider(providerKey) ? '' : undefined,
      apiKey: undefined,
    }))
    setModels(next?.availableModels ?? [])
    setTestMessage('')
  }
  function validate() {
    if (!draft.providerKey || !draft.model.trim()) {
      feedback.notify('请选择接入方式并填写模型', 'error')
      return false
    }
    if (custom && !payload.baseUrl) {
      feedback.notify('请填写 Base URL', 'error')
      return false
    }
    return true
  }
  return {
    config,
    providers: providers.filter((item) => item.enabled === 1),
    provider,
    protocol,
    custom,
    draft,
    models,
    showKey,
    testMessage,
    saving: save.isPending,
    testing: test.isPending,
    discovering: discover.isPending,
    update,
    selectProvider,
    setShowKey,
    save: () => validate() && save.mutate(),
    test: () => validate() && test.mutate(),
    discover: () => {
      if (!protocol?.modelDiscovery) return
      if (validate()) discover.mutate()
    },
  }
}
