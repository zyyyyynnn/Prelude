import { Eye, EyeOff, RefreshCw, Trash2 } from 'lucide-react'
import { useQuery } from '@tanstack/react-query'
import { Button, Field, IconTooltip, Input, Select } from '@/shared/ui'
import { REASONING_LABELS, useLlmSettings } from './useLlmSettings'
import { fetchLlmConfig, fetchProviders } from './api'

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
    <div className="panel-content-wrapper">
      <Field label="接入方式" htmlFor="llm-provider">
        <Select
          id="llm-provider"
          value={state.draft.provider}
          options={state.providers.map((provider) => ({
            value: provider.providerKey,
            label: state.providerLabels[provider.providerKey] ?? provider.providerKey,
          }))}
          onValueChange={state.selectProvider}
        />
      </Field>
      {state.custom && (
        <Field label="Base URL" htmlFor="llm-base-url" hint={endpointHint}>
          <div className="endpoint-row">
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
                <RefreshCw size={15} />
                检测模型
              </Button>
            )}
          </div>
        </Field>
      )}
      <Field label="模型" htmlFor="llm-model">
        {state.models.length ? (
          <Select
            id="llm-model"
            value={state.draft.model}
            options={[...new Set([state.draft.model, ...state.models])]
              .filter(Boolean)
              .map((model) => ({ value: model, label: model }))}
            onValueChange={(value) => state.update('model', value)}
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
      <Field
        label="API Key"
        htmlFor="llm-api-key"
        hint={
          state.config?.hasApiKey && state.config.apiKeyMasked
            ? `已保存 ${state.config.apiKeyMasked}`
            : undefined
        }
      >
        <div className="password-field">
          <Input
            id="llm-api-key"
            type={state.showKey ? 'text' : 'password'}
            autoComplete="off"
            value={state.draft.apiKey ?? ''}
            placeholder="留空表示保留当前 Key"
            onChange={(event) => state.update('apiKey', event.target.value)}
          />
          <div className="password-field__actions">
            {state.config?.hasApiKey && (
              <IconTooltip label="清除已保存的 API Key">
                <button
                  className="password-toggle ui-action ui-action-icon"
                  type="button"
                  aria-label="清除已保存的 API Key"
                  onClick={() => state.update('apiKey', '__CLEAR__')}
                >
                  <Trash2 size={16} />
                </button>
              </IconTooltip>
            )}
            <IconTooltip label={state.showKey ? '隐藏 API Key' : '显示 API Key'}>
              <button
                className="password-toggle ui-action ui-action-icon"
                type="button"
                aria-label={state.showKey ? '隐藏 API Key' : '显示 API Key'}
                onClick={() => state.setShowKey(!state.showKey)}
              >
                {state.showKey ? <Eye size={16} /> : <EyeOff size={16} />}
              </button>
            </IconTooltip>
          </div>
        </div>
      </Field>
      <section className="settings-form-section">
        <h3 className="settings-form-section__title">高级设置</h3>
        <div className="advanced-grid">
          {state.config?.reasoningSupported ? (
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
        </div>
      </section>
      {state.testMessage && (
        <p className="helper-text" role="status">
          {state.testMessage}
        </p>
      )}
      <div className="settings-inline-actions settings-inline-actions--header">
        <Button loading={state.saving} onClick={state.save}>
          保存设置
        </Button>
      </div>
    </div>
  )
}
