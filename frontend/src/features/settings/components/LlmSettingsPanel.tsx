import {
  ErrorState,
  LoadingState,
  SubSection,
  Button,
  Field,
  FieldAction,
  FieldActions,
  Input,
  Panel,
  Select,
} from '@/shared/ui'
import { useQuery } from '@tanstack/react-query'
import { Eye, EyeOff, RefreshCw, Trash2 } from 'lucide-react'
import { fetchLlmConfig, fetchProviders } from '../api'
import { sectionTitles } from '../settings-context'
import { REASONING_LABELS, type LlmConfigResponse, type LlmProviderResponse } from '../types'
import { useLlmSettings } from '../use-llm-settings'

export function LlmSettingsPanel({ providerKey }: { providerKey?: string }) {
  const config = useQuery({ queryKey: ['llm-config'], queryFn: fetchLlmConfig })
  const providers = useQuery({ queryKey: ['llm-providers'], queryFn: fetchProviders })
  if (config.isPending || providers.isPending) return <LoadingState message="正在读取模型配置…" />
  const error = config.error || providers.error
  if (error || !config.data || !providers.data)
    return (
      <ErrorState
        message={error?.message ?? '模型配置不可用'}
        onRetry={() => {
          void config.refetch()
          void providers.refetch()
        }}
      />
    )
  return (
    <LlmSettingsForm
      key={`${providerKey ?? config.data.provider}:${config.data.customEndpointUrl}:${config.data.model}`}
      config={config.data}
      providers={providers.data}
    />
  )
}

/** The whole form is one hook's state: it only decides how that state is laid out. */
function LlmSettingsForm({
  config,
  providers,
}: {
  config: LlmConfigResponse
  providers: LlmProviderResponse[]
}) {
  const state = useLlmSettings(config, providers)
  const endpointHint = state.protocol
    ? `填写接口根地址，系统会请求 ${state.protocol.endpointSuffix}。`
    : '内置接入方式使用系统配置的服务地址。'
  const revealKey = (
    <FieldAction
      label={state.showKey ? '隐藏 API Key' : '显示 API Key'}
      icon={state.showKey ? <Eye /> : <EyeOff />}
      onClick={() => state.setShowKey(!state.showKey)}
    />
  )
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
            options={providers.map((provider) => ({
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
        hint={config.hasApiKey && config.apiKeyMasked ? `已保存 ${config.apiKeyMasked}` : undefined}
      >
        <FieldActions
          actions={
            config.hasApiKey
              ? [
                  <FieldAction
                    label="清除已保存的 API Key"
                    icon={<Trash2 />}
                    onClick={() => state.update('apiKey', '__CLEAR__')}
                  />,
                  revealKey,
                ]
              : [revealKey]
          }
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
      <SubSection title="高级设置">
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
      </SubSection>
      {state.testMessage && (
        <p className="type-meta" role="status">
          {state.testMessage}
        </p>
      )}
    </Panel>
  )
}
