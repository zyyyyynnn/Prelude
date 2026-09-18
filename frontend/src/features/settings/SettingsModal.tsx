import {
  BriefcaseBusiness,
  Eye,
  EyeOff,
  FileText,
  LogOut,
  Palette,
  RefreshCw,
  SquareTerminal,
  Trash2,
  Upload,
  UserRound,
} from 'lucide-react'
import { Suspense, useMemo, useRef, useState, type FormEvent, type ReactNode } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useNavigate } from 'react-router'
import { useAuth } from '@/features/auth'
import { cn } from '@/shared/lib/cn'
import { Button } from '@/shared/ui/button'
import { Field, Input } from '@/shared/ui/field'
import { Dialog, IconTooltip } from '@/shared/ui/overlay'
import { Select } from '@/shared/ui/select'
import { useFeedback } from '@/shared/ui/feedback-context'
import { formText } from '@/shared/lib/form-data'
import './settings.css'
import {
  discoverCapabilities,
  discoverModels,
  fetchLlmConfig,
  fetchProfile,
  fetchProviders,
  saveLlmConfig,
  saveProfile,
  uploadAvatar,
} from './index'
import {
  getCustomProviderMeta,
  isCustomProvider,
  normalizeCustomBaseUrl,
} from './provider-protocol'
import { applyTheme, readTheme } from './theme'
import {
  REASONING_LABELS,
  type LlmConfigPayload,
  type LlmConfigResponse,
  type ModelCapabilityResponse,
  type LlmProviderResponse,
  type ReasoningLevel,
  type ThemePreference,
} from './types'
import {
  SettingsContext,
  type SettingsIntent,
  type SettingsOpenRequest,
  type SettingsRequest,
  type SettingsSection,
} from './settings-context'

type ResourcePanelRenderer = (request: SettingsRequest) => ReactNode

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
      <div className="llm-model-selection-grid">
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

export function SettingsProvider({
  children,
  renderResourcePanel,
}: {
  children: ReactNode
  renderResourcePanel: ResourcePanelRenderer
}) {
  const [open, setOpen] = useState(false)
  const [request, setRequest] = useState<SettingsRequest>({ section: 'profile', requestId: 0 })

  function openSettings(next: SettingsOpenRequest = {}) {
    setRequest({
      section: next.section ?? 'profile',
      provider: next.provider,
      intent: next.intent,
      requestId: Date.now(),
    })
    setOpen(true)
  }

  return (
    <SettingsContext.Provider value={{ openSettings }}>
      {children}
      {open && (
        <SettingsModal
          open={open}
          section={request.section}
          provider={request.provider}
          intent={request.intent}
          requestId={request.requestId}
          onSectionChange={(section) =>
            setRequest((current) => ({ ...current, section, intent: undefined }))
          }
          onOpenChange={setOpen}
          renderResourcePanel={renderResourcePanel}
        />
      )}
    </SettingsContext.Provider>
  )
}

const themeOptions: Array<{ value: ThemePreference; label: string; description: string }> = [
  { value: 'light', label: '浅色', description: '暖色纸面' },
  { value: 'dark', label: '暗色', description: '低亮度阅读' },
  { value: 'system', label: '跟随系统', description: '自动同步' },
]

function ThemePanel() {
  const profile = useQuery({ queryKey: ['profile'], queryFn: fetchProfile })
  if (profile.isPending) return <div className="empty-state">正在读取主题偏好…</div>
  if (profile.isError) return <div className="empty-state">{profile.error.message}</div>
  const initial = profile.data?.themePreference ?? readTheme()
  return <ThemeForm key={initial} initial={initial} revision={profile.data?.revision ?? 0} />
}

function ThemeForm({ initial, revision }: { initial: ThemePreference; revision: number }) {
  const [value, setValue] = useState(initial)
  const client = useQueryClient()
  const feedback = useFeedback()
  const save = useMutation({
    mutationFn: () =>
      saveProfile({
        themePreference: value,
        expectedRevision: revision,
        operationId: crypto.randomUUID(),
      }),
    onSuccess: (data) => {
      client.setQueryData(['profile'], data)
      applyTheme(data.themePreference ?? value)
      feedback.notify('主题已保存', 'success')
    },
    onError: (error) => {
      applyTheme(initial)
      feedback.notify(error.message, 'error')
    },
  })
  return (
    <div className="panel-content-wrapper">
      <div className="theme-grid" role="radiogroup" aria-label="主题偏好">
        {themeOptions.map((option) => (
          <button
            key={option.value}
            type="button"
            role="radio"
            aria-checked={value === option.value}
            className={cn(
              'theme-option ui-action ui-action-selectable',
              value === option.value && 'is-active',
            )}
            onClick={() => {
              setValue(option.value)
              applyTheme(option.value)
            }}
          >
            <span className="theme-option__preview" data-theme-preview={option.value}>
              <span />
              <span />
            </span>
            <span className="theme-option__copy">
              <span className="theme-option__label">{option.label}</span>
              <span className="theme-option__desc">{option.description}</span>
            </span>
          </button>
        ))}
      </div>
      <div className="settings-inline-actions settings-inline-actions--header">
        <Button loading={save.isPending} disabled={value === initial} onClick={() => save.mutate()}>
          保存主题
        </Button>
      </div>
    </div>
  )
}

function ProfilePanel() {
  const profile = useQuery({ queryKey: ['profile'], queryFn: fetchProfile })
  const client = useQueryClient()
  const feedback = useFeedback()
  const avatarInput = useRef<HTMLInputElement>(null)
  const [oldVisible, setOldVisible] = useState(false)
  const [newVisible, setNewVisible] = useState(false)
  const save = useMutation({
    mutationFn: saveProfile,
    onSuccess: (data) => {
      client.setQueryData(['profile'], data)
      feedback.notify('资料已保存', 'success')
    },
    onError: (error) => feedback.notify(error.message, 'error'),
  })
  const avatar = useMutation({
    mutationFn: uploadAvatar,
    onSuccess: (data) => {
      client.setQueryData(['profile'], data)
      feedback.notify('头像已更新', 'success')
    },
    onError: (error) => feedback.notify(error.message, 'error'),
  })
  function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const data = new FormData(event.currentTarget)
    const oldPassword = formText(data, 'oldPassword')
    const newPassword = formText(data, 'newPassword')
    if (Boolean(oldPassword) !== Boolean(newPassword)) {
      feedback.notify('修改密码时必须同时填写旧密码和新密码', 'error')
      return
    }
    if (oldPassword && oldPassword === newPassword) {
      feedback.notify('新密码不能与旧密码相同', 'error')
      return
    }
    if (!profile.data) return
    save.mutate({
      username: formText(data, 'username'),
      email: formText(data, 'email'),
      oldPassword: oldPassword || undefined,
      newPassword: newPassword || undefined,
      expectedRevision: profile.data.revision,
      operationId: crypto.randomUUID(),
    })
  }
  if (profile.isPending) return <div className="empty-state">正在读取账号资料…</div>
  if (profile.isError) return <div className="empty-state">{profile.error.message}</div>
  const initial = (profile.data?.username?.trim()[0] || 'P').toUpperCase()
  return (
    <form
      className="panel-content-wrapper"
      key={`${profile.data?.username}:${profile.data?.email}:${profile.data?.avatarUrl}`}
      onSubmit={submit}
    >
      <section className="profile-avatar-row">
        <div className="profile-avatar">
          {profile.data?.avatarUrl ? (
            <img className="profile-avatar__image" src={profile.data.avatarUrl} alt="当前头像" />
          ) : (
            <span>{initial}</span>
          )}
        </div>
        <div className="profile-avatar__actions">
          <label className="sr-only" htmlFor="avatar-upload">
            选择头像
          </label>
          <input
            id="avatar-upload"
            ref={avatarInput}
            className="sr-only"
            type="file"
            accept="image/png,image/jpeg,image/webp,image/gif"
            onChange={(event) => {
              const file = event.target.files?.[0]
              if (file) avatar.mutate(file)
              event.currentTarget.value = ''
            }}
          />
          <Button
            type="button"
            variant="secondary"
            loading={avatar.isPending}
            onClick={() => avatarInput.current?.click()}
          >
            <Upload size={15} />
            上传头像
          </Button>
        </div>
      </section>
      <div className="field-grid">
        <Field label="用户名" htmlFor="profile-username">
          <Input
            id="profile-username"
            name="username"
            required
            autoComplete="username"
            defaultValue={profile.data?.username}
          />
        </Field>
        <Field label="邮箱" htmlFor="profile-email">
          <Input
            id="profile-email"
            name="email"
            type="email"
            autoComplete="email"
            defaultValue={profile.data?.email}
          />
        </Field>
      </div>
      <section className="settings-form-section">
        <h3 className="settings-form-section__title">修改密码</h3>
        <div className="field-grid">
          <PasswordField
            label="旧密码"
            name="oldPassword"
            visible={oldVisible}
            onToggle={() => setOldVisible((value) => !value)}
            autoComplete="current-password"
          />
          <PasswordField
            label="新密码"
            name="newPassword"
            visible={newVisible}
            onToggle={() => setNewVisible((value) => !value)}
            autoComplete="new-password"
          />
        </div>
      </section>
      <div className="settings-inline-actions settings-inline-actions--header">
        <Button type="submit" loading={save.isPending}>
          保存设置
        </Button>
      </div>
    </form>
  )
}

function PasswordField({
  label,
  name,
  visible,
  onToggle,
  autoComplete,
}: {
  label: string
  name: string
  visible: boolean
  onToggle: () => void
  autoComplete: string
}) {
  return (
    <Field label={label} htmlFor={name}>
      <div className="password-field">
        <Input
          id={name}
          name={name}
          type={visible ? 'text' : 'password'}
          autoComplete={autoComplete}
          placeholder="留空表示不修改密码"
        />
        <IconTooltip label={visible ? '隐藏密码' : '显示密码'}>
          <button
            type="button"
            className="password-toggle ui-action ui-action-icon"
            aria-label={visible ? '隐藏密码' : '显示密码'}
            onClick={onToggle}
          >
            {visible ? <Eye size={16} /> : <EyeOff size={16} />}
          </button>
        </IconTooltip>
      </div>
    </Field>
  )
}

const titles: Record<SettingsSection, string> = {
  profile: '账号资料',
  resumes: '简历管理',
  positions: '岗位管理',
  llm: '模型管理',
  theme: '主题',
}

export function SettingsModal({
  open,
  section,
  provider,
  intent,
  requestId,
  onSectionChange,
  onOpenChange,
  renderResourcePanel,
}: {
  open: boolean
  section: SettingsSection
  provider?: string
  intent?: SettingsIntent
  requestId: number
  onSectionChange: (section: SettingsSection) => void
  onOpenChange: (value: boolean) => void
  renderResourcePanel: ResourcePanelRenderer
}) {
  const auth = useAuth()
  const navigate = useNavigate()
  return (
    <Dialog
      open={open}
      onOpenChange={onOpenChange}
      title="全局设置"
      layout="workspace"
      showClose={false}
    >
      <div className="settings-layout">
        <aside className="settings-sidebar">
          <nav className="sidebar-menu" aria-label="设置分类">
            <TabButton
              active={section === 'profile'}
              onClick={() => onSectionChange('profile')}
              icon={<UserRound aria-hidden="true" />}
            >
              账号资料
            </TabButton>
            <TabButton
              active={section === 'resumes'}
              onClick={() => onSectionChange('resumes')}
              icon={<FileText aria-hidden="true" />}
            >
              简历管理
            </TabButton>
            <TabButton
              active={section === 'positions'}
              onClick={() => onSectionChange('positions')}
              icon={<BriefcaseBusiness aria-hidden="true" />}
            >
              岗位管理
            </TabButton>
            <TabButton
              active={section === 'llm'}
              onClick={() => onSectionChange('llm')}
              icon={<SquareTerminal aria-hidden="true" />}
            >
              模型管理
            </TabButton>
            <TabButton
              active={section === 'theme'}
              onClick={() => onSectionChange('theme')}
              icon={<Palette aria-hidden="true" />}
            >
              主题
            </TabButton>
          </nav>
          <div className="sidebar-footer">
            <button
              className="settings-sidebar__item settings-sidebar__item--danger ui-action ui-action-danger"
              onClick={() => {
                onOpenChange(false)
                void auth.signOut().then(() => navigate('/login'))
              }}
            >
              <LogOut aria-hidden="true" />
              退出登录
            </button>
          </div>
        </aside>
        <main className="settings-main">
          <header className="settings-header">
            <h2 className="settings-header__title">{titles[section]}</h2>
          </header>
          <div className="settings-content scrollable">
            <Suspense
              fallback={
                <div className="empty-state" role="status">
                  正在加载设置…
                </div>
              }
            >
              {section === 'profile' && <ProfilePanel />}
              {(section === 'resumes' || section === 'positions') &&
                renderResourcePanel({ section, provider, intent, requestId })}
              {section === 'llm' && <LlmSettingsPanel providerKey={provider} />}
              {section === 'theme' && <ThemePanel />}
            </Suspense>
          </div>
        </main>
      </div>
    </Dialog>
  )
}

function TabButton({
  active,
  onClick,
  icon,
  children,
}: {
  active: boolean
  onClick: () => void
  icon: ReactNode
  children: ReactNode
}) {
  return (
    <button
      className={cn('settings-sidebar__item ui-action ui-action-nav', active && 'is-active')}
      aria-current={active ? 'page' : undefined}
      onClick={onClick}
    >
      {icon}
      {children}
    </button>
  )
}
