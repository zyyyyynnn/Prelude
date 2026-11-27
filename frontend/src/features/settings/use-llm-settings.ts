import { useFeedback } from '@/shared/ui'
import { useMemo, useEffect, useRef, useState } from 'react'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { discoverCapabilities, discoverModels, saveLlmConfig } from './api'
import { buildLlmPayload, createProviderDraft, llmDraftError, providerModels } from './llm-draft'
import { getCustomProviderMeta, isCustomProvider } from './provider-protocol'
import { llmQueryKeys } from './query-keys'
import type {
  LlmConfigPayload,
  LlmConfigResponse,
  LlmProviderResponse,
  ModelCapabilityResponse,
} from './types'

type LlmRequestScope = {
  accountId: number
  provider: string
  baseUrl: string
  model: string
  generation: number
  apiKey?: string
}

function sameScope(current: LlmRequestScope, requested: LlmRequestScope) {
  return (
    current.accountId === requested.accountId &&
    current.generation === requested.generation &&
    current.provider === requested.provider &&
    current.baseUrl === requested.baseUrl &&
    current.model === requested.model
  )
}

export function useLlmSettings(
  accountId: number,
  config: LlmConfigResponse,
  providers: LlmProviderResponse[],
) {
  const feedback = useFeedback()
  const client = useQueryClient()
  const initialDraft: LlmConfigPayload = {
    provider: config.provider,
    customEndpointUrl: config.customEndpointUrl ?? '',
    model: config.model,
    apiKey: undefined,
    reasoningLevel: config.reasoningLevel,
    maxOutputTokens: config.maxOutputTokens,
    fallbackModels: config.fallbackModels,
  }
  const [draft, setDraft] = useState(initialDraft)
  const draftRef = useRef(initialDraft)
  const generation = useRef(0)
  const active = useRef(true)
  const account = useRef(accountId)
  useEffect(() => {
    account.current = accountId
    active.current = true
    return () => {
      active.current = false
      generation.current += 1
    }
  }, [accountId])
  const [models, setModels] = useState<ModelCapabilityResponse[]>(() =>
    providerModels(
      providers,
      config.provider,
      config.capability.model === config.model ? [config.capability] : [],
    ),
  )
  const [showKey, setShowKey] = useState(false)

  const custom = isCustomProvider(draft.provider)
  const protocol = getCustomProviderMeta(draft.provider)
  const selectedCapability =
    models.find((item) => item.model === draft.model) ??
    (config.provider === draft.provider && config.model === draft.model
      ? config.capability
      : undefined)
  const reasoningLevels = selectedCapability?.supportedReasoningLevels ?? []

  function commitDraft(next: LlmConfigPayload) {
    draftRef.current = next
    generation.current += 1
    setDraft(next)
  }

  function captureScope(): LlmRequestScope {
    const current = draftRef.current
    return {
      accountId,
      provider: current.provider,
      baseUrl: isCustomProvider(current.provider)
        ? (buildLlmPayload(current, true).customEndpointUrl ?? '')
        : '',
      model: current.model,
      generation: generation.current,
      apiKey: current.apiKey,
    }
  }

  function isCurrent(scope: LlmRequestScope) {
    return active.current && account.current === scope.accountId && sameScope(captureScope(), scope)
  }

  const update = <K extends keyof LlmConfigPayload>(key: K, value: LlmConfigPayload[K]) => {
    commitDraft({ ...draftRef.current, [key]: value })
  }
  const payload = useMemo(() => buildLlmPayload(draft, custom), [custom, draft])

  const save = useMutation({
    mutationFn: ({ payload }: { scope: LlmRequestScope; payload: LlmConfigPayload }) =>
      saveLlmConfig(payload),
    onSuccess: (result, { scope }) => {
      if (!isCurrent(scope)) return
      client.setQueryData(llmQueryKeys.config(scope.accountId), result)
      commitDraft({
        ...draftRef.current,
        provider: result.provider,
        apiKey: undefined,
        customEndpointUrl: result.customEndpointUrl ?? '',
        model: result.model,
        reasoningLevel: result.reasoningLevel,
        maxOutputTokens: result.maxOutputTokens,
        fallbackModels: result.fallbackModels,
      })
      setModels(() => providerModels(providers, result.provider, [result.capability]))
      feedback.notify('LLM 配置已保存', 'success')
    },
    onError: (error, { scope }) => {
      if (isCurrent(scope)) feedback.notify(error.message, 'error')
    },
  })
  const capabilityProbe = useMutation({
    mutationFn: ({ scope, model }: { scope: LlmRequestScope; model: string }) =>
      discoverCapabilities({
        provider: scope.provider,
        baseUrl: scope.baseUrl,
        apiKey: scope.apiKey,
        model,
      }),
    onSuccess: (capability, { scope, model }) => {
      if (!isCurrent(scope) || capability.model !== model) return
      setModels((current) => {
        const withoutCurrent = current.filter((item) => item.model !== capability.model)
        return [...withoutCurrent, capability]
      })
    },
    onError: (_, { scope }) => {
      if (isCurrent(scope)) {
        feedback.notify('模型能力检测失败；未确认能力前仅可使用服务端已返回的能力', 'info')
      }
    },
  })
  const discover = useMutation({
    mutationFn: (scope: LlmRequestScope) =>
      discoverModels({
        provider: scope.provider,
        baseUrl: scope.baseUrl,
        apiKey: scope.apiKey,
      }),
    onSuccess: (result, scope) => {
      if (!isCurrent(scope)) return
      setModels(result.models)
      commitDraft({ ...draftRef.current, customEndpointUrl: result.baseUrl })
      feedback.notify(
        result.models.length ? '模型列表已更新' : '未读取到模型，可手动填写模型 ID',
        result.models.length ? 'success' : 'info',
      )
    },
    onError: (error, scope) => {
      if (isCurrent(scope)) feedback.notify(error.message, 'error')
    },
  })

  function selectProvider(providerKey: string) {
    const next = providers.find((item) => item.providerKey === providerKey)
    commitDraft(createProviderDraft(draftRef.current, providerKey))
    setModels(next?.models ?? [])
  }
  function selectModel(model: string) {
    commitDraft({ ...draftRef.current, model })
    const scope = captureScope()
    if (isCustomProvider(scope.provider) && model.trim() && scope.baseUrl) {
      capabilityProbe.mutate({ model: model.trim(), scope })
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
    saving: save.isPending,
    discovering: discover.isPending,
    update,
    selectProvider,
    selectModel,
    setShowKey,
    save: () =>
      validate() && save.mutate({ scope: captureScope(), payload: buildLlmPayload(draft, custom) }),
    discover: () => {
      if (!protocol?.modelDiscovery) return
      if (validateDiscovery()) discover.mutate(captureScope())
    },
  }
}
