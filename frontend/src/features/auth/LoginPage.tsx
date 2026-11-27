import { useEffect, useMemo, useRef, useState, type FormEvent } from 'react'
import { Navigate, useLocation } from 'react-router'
import { useQuery } from '@tanstack/react-query'
import { BrandMetaballs } from '@/shared/brand/BrandMetaballs'
import { apiBaseUrl } from '@/shared/api/client'
import { cn } from '@/shared/lib/cn'
import { fetchOAuthProviders, login, register } from './api'
import { useAuth } from './auth-context'
import { AuthPage } from './AuthPage'
import { oauthStartPath, safeLocalRedirect } from './redirect'
import type { OAuthProvider } from './types'
import { RiEyeLine, RiEyeOffLine, RiGithubLine, RiGoogleLine } from '@remixicon/react'
import {
  Button,
  ErrorState,
  Field,
  FieldAction,
  FieldActions,
  Icon,
  Input,
  SegmentedControl,
  useFeedback,
} from '@/shared/ui'

type AuthMode = 'login' | 'register'

const oauthOptions = {
  google: { label: 'Google', icon: RiGoogleLine },
  github: { label: 'GitHub', icon: RiGithubLine },
} satisfies Record<OAuthProvider, { label: string; icon: typeof RiGoogleLine }>

export function LoginPage() {
  const [mode, setMode] = useState<AuthMode>('login')
  const [username, setUsername] = useState('')
  const [password, setPassword] = useState('')
  const [email, setEmail] = useState('')
  const [showPassword, setShowPassword] = useState(false)
  const [busy, setBusy] = useState(false)
  const auth = useAuth()
  const feedback = useFeedback()
  const location = useLocation()
  const reportedExpiration = useRef(false)
  const searchParams = useMemo(() => new URLSearchParams(location.search), [location.search])
  const oauthPending = searchParams.get('oauth') === 'pending'
  const providers = useQuery({
    queryKey: ['oauth-providers'],
    queryFn: fetchOAuthProviders,
    retry: false,
  })
  const enabledProviders = (providers.data ?? []).filter(
    (provider): provider is OAuthProvider => provider === 'google' || provider === 'github',
  )

  useEffect(() => {
    const expired = searchParams.get('reason') === 'expired'
    if (!expired) reportedExpiration.current = false
    else if (!reportedExpiration.current) {
      reportedExpiration.current = true
      feedback.notify('登录已失效，请重新登录。', 'error')
    }
  }, [feedback, searchParams])

  const switchMode = (next: AuthMode) => {
    setMode(next)
    setPassword('')
    setEmail('')
  }

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (username.trim().length < 2) {
      feedback.notify('用户名至少需要 2 个字符', 'error')
      document.getElementById('auth-username')?.focus()
      return
    }
    if (password.length < 6) {
      feedback.notify('密码至少需要 6 个字符', 'error')
      document.getElementById('auth-password')?.focus()
      return
    }

    setBusy(true)
    try {
      if (mode === 'register') {
        await register(username.trim(), password, email.trim() || undefined)
        feedback.notify('注册成功，请继续登录。', 'success')
        switchMode('login')
        return
      }
      const result = await login(username.trim(), password)
      await auth.signIn(result.accountId)
    } catch (reason) {
      feedback.notify(reason instanceof Error ? reason.message : '请求失败', 'error')
    } finally {
      setBusy(false)
    }
  }

  if (auth.status === 'authenticated') {
    return <Navigate to={safeLocalRedirect(searchParams.get('redirect'))} replace />
  }

  return (
    <AuthPage>
      <section className="login-card" aria-labelledby="auth-title">
        <div className="login-card__content">
          <aside className="login-card__brand-panel">
            <BrandMetaballs className="login-card__logo" />
            <p className="login-card__brand-caption">AI Mock Interview</p>
          </aside>

          <div className="login-card__form-panel">
            <header className="login-card__header">
              <h1 id="auth-title" className="type-hero">
                {mode === 'login' ? '进入面试工作台' : '创建工作台账号'}
              </h1>
            </header>

            {auth.status === 'unavailable' && (
              <ErrorState
                message="登录状态检查失败，请检查网络后重试。"
                onRetry={() => void auth.retry()}
              />
            )}

            {mode === 'login' && oauthPending && (
              <p role="alert" className="text-sm text-text-secondary">
                外部账号已匹配现有账户，请使用密码登录以完成验证。
              </p>
            )}

            <SegmentedControl
              ariaLabel="账号操作"
              items={[
                { value: 'login', label: '登录' },
                { value: 'register', label: '注册' },
              ]}
              value={mode}
              onValueChange={switchMode}
            />

            <form
              data-slot="auth-form"
              className="flex min-h-0 flex-col gap-md"
              onSubmit={(event) => void submit(event)}
              noValidate
            >
              <Field label="用户名" htmlFor="auth-username">
                <Input
                  id="auth-username"
                  value={username}
                  onChange={(event) => setUsername(event.target.value)}
                  autoComplete="username"
                  placeholder="请输入用户名"
                  required
                />
              </Field>

              <Field label="密码" htmlFor="auth-password">
                <FieldActions
                  actions={[
                    <FieldAction
                      label={showPassword ? '隐藏密码' : '显示密码'}
                      icon={showPassword ? <Icon as={RiEyeLine} /> : <Icon as={RiEyeOffLine} />}
                      onClick={() => setShowPassword((value) => !value)}
                    />,
                  ]}
                >
                  <Input
                    id="auth-password"
                    value={password}
                    onChange={(event) => setPassword(event.target.value)}
                    type={showPassword ? 'text' : 'password'}
                    autoComplete={mode === 'login' ? 'current-password' : 'new-password'}
                    placeholder="请输入密码"
                    required
                  />
                </FieldActions>
              </Field>

              <div
                className={cn('honeypot-field', mode === 'register' && 'is-visible')}
                aria-hidden={mode !== 'register'}
              >
                <Field label="邮箱" htmlFor="auth-email">
                  <Input
                    id="auth-email"
                    value={email}
                    onChange={(event) => setEmail(event.target.value)}
                    type="email"
                    autoComplete="email"
                    placeholder="请输入邮箱"
                    disabled={mode !== 'register'}
                  />
                </Field>
              </div>

              <div className="login-card__actions">
                <Button type="submit" className="w-full" loading={busy}>
                  {mode === 'login' ? '登录' : '完成注册'}
                </Button>
              </div>
            </form>

            {mode === 'login' && enabledProviders.length > 0 && (
              <div className="grid gap-sm">
                {enabledProviders.map((provider) => {
                  const option = oauthOptions[provider]
                  return (
                    <Button
                      key={provider}
                      variant="secondary"
                      className="w-full"
                      nativeButton={false}
                      render={
                        <a
                          href={`${apiBaseUrl}${oauthStartPath(provider, searchParams.get('redirect'))}`}
                        />
                      }
                    >
                      <Icon as={option.icon} />
                      {option.label}
                    </Button>
                  )
                })}
              </div>
            )}
          </div>
        </div>
      </section>
    </AuthPage>
  )
}
