import { useEffect, useRef, useState, type FormEvent } from 'react'
import { Eye, EyeOff } from 'lucide-react'
import { Navigate, useLocation } from 'react-router'
import { BrandMetaballs } from '@/shared/brand/BrandMetaballs'
import { cn } from '@/shared/lib/cn'
import {
  Button,
  Field,
  FieldAction,
  FieldActions,
  Input,
  SegmentedControl,
  useFeedback,
} from '@/shared/ui'
import { login, register } from './api'
import { useAuth } from './auth-context'

type AuthMode = 'login' | 'register'

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

  useEffect(() => {
    const expired = new URLSearchParams(location.search).get('reason') === 'expired'
    if (!expired) reportedExpiration.current = false
    else if (!reportedExpiration.current) {
      reportedExpiration.current = true
      feedback.notify('登录已失效，请重新登录。', 'error')
    }
  }, [feedback, location.search])

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
    const redirect = new URLSearchParams(location.search).get('redirect') || '/interview'
    return <Navigate to={redirect} replace />
  }

  return (
    <main className="page page--center page--auth">
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
                      icon={showPassword ? <Eye /> : <EyeOff />}
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
          </div>
        </div>
      </section>
    </main>
  )
}
