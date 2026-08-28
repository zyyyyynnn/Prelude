import { Eye, EyeOff, RefreshCw, Trash2 } from 'lucide-react'
import { useQuery } from '@tanstack/react-query'
import { Button, Field, IconTooltip, Input, Select } from '@/shared/ui'
import { useLlmSettings } from './useLlmSettings'
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
      key={`${providerKey ?? config.data.providerKey}:${config.data.baseUrl}:${config.data.model}`}
      config={config.data}
      providers={providers.data}
      providerKey={providerKey}
    />
  )
}

function LlmSettingsForm({
  config,
  providers,
  providerKey,
}: {
  config: Awaited<ReturnType<typeof fetchLlmConfig>>
  providers: Awaited<ReturnType<typeof fetchProviders>>
  providerKey?: string
}) {
  const state = useLlmSettings(config, providers, providerKey)
  const endpointHint = state.protocol
    ? `填写接口根地址，系统会请求 ${state.protocol.endpointSuffix}。`
    : 'DeepSeek 使用系统配置的服务地址。'
  return (
    <div className="panel-content-wrapper">
      <Field label="服务协议" htmlFor="llm-provider">
        <Select
          id="llm-provider"
          value={state.draft.providerKey}
          options={state.providers.map((provider) => ({
            value: provider.providerKey,
            label: provider.displayName,
          }))}
          onValueChange={state.selectProvider}
        />
      </Field>
      {state.custom && (
        <Field label="Base URL" htmlFor="llm-base-url" hint={endpointHint}>
          <div className="endpoint-row">
            <Input
              id="llm-base-url"
              value={state.draft.baseUrl ?? ''}
              placeholder={state.protocol?.placeholder}
              onChange={(event) => state.update('baseUrl', event.target.value)}
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
      <section className="form-section">
        <h3 className="form-section__title">高级设置</h3>
        <div className="advanced-grid">
          <Field label="最大回复长度" htmlFor="llm-max-tokens">
            <Select
              id="llm-max-tokens"
              value={state.draft.maxTokens ? String(state.draft.maxTokens) : ''}
              options={[
                { value: '', label: '模型默认' },
                { value: '4096', label: '常规 (4096)' },
                { value: '8192', label: '长回复 (8192)' },
                { value: '32768', label: '深度分析 (32768)' },
              ]}
              onValueChange={(value) =>
                state.update('maxTokens', value ? Number(value) : undefined)
              }
            />
          </Field>
          <Field label="思考深度" htmlFor="llm-thinking-depth">
            <Select
              id="llm-thinking-depth"
              value={state.draft.thinkingDepth ?? ''}
              options={[
                { value: '', label: '默认' },
                { value: 'low', label: '低' },
                { value: 'medium', label: '中' },
                { value: 'high', label: '高' },
                { value: 'xhigh', label: '极高' },
              ]}
              onValueChange={(value) => state.update('thinkingDepth', value || null)}
            />
          </Field>
        </div>
      </section>
      {state.testMessage && (
        <p className="helper-text" role="status">
          {state.testMessage}
        </p>
      )}
      <div className="settings-inline-actions settings-inline-actions--header">
        <Button
          variant="secondary"
          loading={state.testing}
          disabled={state.saving}
          onClick={state.test}
        >
          测试连接
        </Button>
        <Button loading={state.saving} disabled={state.testing} onClick={state.save}>
          保存设置
        </Button>
      </div>
    </div>
  )
}
