import { useMemo, useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Eye, EyeOff, RefreshCw, Trash2 } from 'lucide-react'
import { Button } from '@/shared/ui/button'
import { Field, FieldAction, FieldActions, Input } from '@/shared/ui/field'
import { Panel } from '@/shared/ui/panel'
import { Select } from '@/shared/ui/select'
import { useFeedback } from '@/shared/ui/feedback-context'
import {
  discoverCapabilities,
  discoverModels,
  fetchLlmConfig,
  fetchProviders,
  saveLlmConfig,
} from '../index'
import { sectionTitles } from '../settings-context'
import {
  getCustomProviderMeta,
  isCustomProvider,
  normalizeCustomBaseUrl,
} from '../provider-protocol'
import {
  REASONING_LABELS,
  type LlmConfigPayload,
  type LlmConfigResponse,
  type ModelCapabilityResponse,
  type LlmProviderResponse,
  type ReasoningLevel,
} from '../types'

export function LlmSettingsPanel({ providerKey }: { providerKey?: string }) {
  const config = useQuery({ queryKey: ['llm-config'], queryFn: fetchLlmConfig })
  const providers = useQuery({ queryKey: ['llm-providers'], queryFn: fetchProviders })
  if (config.isPending || providers.isPending)
    return <div className="empty-state">正在读取模型配置…</div>
  const error = config.error || providers.error
  if (error || !config.data || !providers.data)
    return <div className="empty-state">{error?.message ?? '模型配置不可用'}</div>
  return (
    <LlmSettingsForm
      key={`${providerKey ?? config.data.provider}:${config.data.customEndpointUrl}:${config.data.model}`}
      config={config.data}
      providers={providers.data}
      providerKey={providerKey}
    />
  )
}

function LlmSettingsForm({
  config,
  providers,
}: {
  config: Awaited<ReturnType<typeof fetchLlmConfig>>
  providers: Awaited<ReturnType<typeof fetchProviders>>
  providerKey?: string
}) {
  const state = useLlmSettings(config, providers)
  const endpointHint = state.protocol
    ? `填写接口根地址，系统会请求 ${state.protocol.endpointSuffix}。`
    : '内置接入方式使用系统配置的服务地址。'
  return (
    <Panel
      title={sectionTitles.llm}
      actions={
        <Button loading={state.saving} onClick={state.save}>
          保存设置
        </Button>
      }
    >
      <div className="form-grid gap-md" data-slot="model-selection">
        <Field label="接入方式" htmlFor="llm-provider">
          <Select
            id="llm-provider"
            value={state.draft.provider}
            options={state.providers.map((provider) => ({
              value: provider.providerKey,
              label: provider.displayName,
            }))}
            onValueChange={state.selectProvider}
          />
        </Field>
        <Field label="模型" htmlFor="llm-model">
          {state.models.length ? (
            <Select
              id="llm-model"
              value={state.draft.model}
              options={[...new Set([state.draft.model, ...state.models.map((item) => item.model)])]
                .filter(Boolean)
                .map((model) => ({ value: model, label: model }))}
              onValueChange={state.selectModel}
            />
          ) : (
            <Input
              id="llm-model"
              value={state.draft.model}
              placeholder="输入模型 ID"
              onChange={(event) => state.update('model', event.target.value)}
            />
          )}
        </Field>
      </div>
      {state.custom && (
        <Field label="Base URL" htmlFor="llm-base-url" hint={endpointHint}>
          <div className="label-end-grid gap-sm">
            <Input
              id="llm-base-url"
              value={state.draft.customEndpointUrl ?? ''}
              placeholder={state.protocol?.placeholder}
              onChange={(event) => state.update('customEndpointUrl', event.target.value)}
            />
            {state.protocol?.modelDiscovery && (
              <Button
                type="button"
                variant="secondary"
                loading={state.discovering}
                onClick={state.discover}
              >
                <RefreshCw />
                检测模型
              </Button>
            )}
          </div>
        </Field>
      )}
      <Field
        label="API Key"
        htmlFor="llm-api-key"
        hint={
          state.config?.hasApiKey && state.config.apiKeyMasked
            ? `已保存 ${state.config.apiKeyMasked}`
            : undefined
        }
      >
        <FieldActions
          actions={[
            ...(state.config?.hasApiKey
              ? [
                  <FieldAction
                    label="清除已保存的 API Key"
                    icon={<Trash2 />}
                    onClick={() => state.update('apiKey', '__CLEAR__')}
                  />,
                ]
              : []),
            <FieldAction
              label={state.showKey ? '隐藏 API Key' : '显示 API Key'}
              icon={state.showKey ? <Eye /> : <EyeOff />}
              onClick={() => state.setShowKey(!state.showKey)}
            />,
          ]}
        >
          <Input
            id="llm-api-key"
            type={state.showKey ? 'text' : 'password'}
            autoComplete="off"
            value={state.draft.apiKey ?? ''}
            placeholder="留空表示保留当前 Key"
            onChange={(event) => state.update('apiKey', event.target.value)}
          />
        </FieldActions>
      </Field>
      <section className="grid gap-sm border-t border-border pt-md">
        <h3 className="type-subtitle">高级设置</h3>
        <div className="form-grid gap-md">
          {state.selectedCapability &&
          (state.custom ||
            state.selectedCapability.reasoning ||
            !state.selectedCapability.supportedReasoningLevels.includes(
              state.draft.reasoningLevel ?? 'AUTO',
            )) ? (
            <Field label="思考深度" htmlFor="llm-reasoning-level">
              <Select
                id="llm-reasoning-level"
                value={state.draft.reasoningLevel ?? ''}
                options={state.reasoningLevels.map((level) => ({
                  value: level,
                  label: REASONING_LABELS[level],
                }))}
                onValueChange={(value) => state.update('reasoningLevel', value as never)}
              />
            </Field>
          ) : null}
          <Field label="最大回复长度" htmlFor="llm-max-output-tokens">
            <Select
              id="llm-max-output-tokens"
              value={String(state.draft.maxOutputTokens ?? 4096)}
              options={[
                { value: '4096', label: '常规 · 4,096 tokens' },
                { value: '8192', label: '长回复 · 8,192 tokens' },
                { value: '32768', label: '深度分析 · 32,768 tokens' },
              ]}
              onValueChange={(value) => state.update('maxOutputTokens', Number(value))}
            />
          </Field>
        </div>
      </section>
      {state.testMessage && (
        <p className="type-meta" role="status">
          {state.testMessage}
        </p>
      )}
    </Panel>
  )
}

/** Providers publish their model catalog; the saved capability is the fallback for custom endpoints. */
function providerModels(
  providers: LlmProviderResponse[],
  providerKey: string,
  fallback: ModelCapabilityResponse[],
) {
  const provider = providers.find((item) => item.providerKey === providerKey)
  return provider?.models.length ? provider.models : fallback
}

/** First blocking draft error, or null when the draft can be saved. */
function llmDraftError({
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

function buildLlmPayload(draft: LlmConfigPayload, custom: boolean): LlmConfigPayload {
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

function createProviderDraft(current: LlmConfigPayload, provider: string): LlmConfigPayload {
  return {
    ...current,
    provider,
    model: '',
    customEndpointUrl: isCustomProvider(provider) ? '' : undefined,
    apiKey: undefined,
  }
}

function useLlmSettings(config: LlmConfigResponse, providers: LlmProviderResponse[]) {
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
