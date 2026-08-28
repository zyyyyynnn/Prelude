import { useEffect, useState, type FormEvent } from 'react'
import { Eye, EyeOff } from 'lucide-react'
import { useLocation, useNavigate } from 'react-router'
import { BrandMetaballs } from '@/shared/brand/BrandMetaballs'
import { Button, Field, IconTooltip, Input } from '@/shared/ui'
import { useFeedback } from '@/shared/ui/feedback'
import { login, register } from './api'
import { useAuth } from './AuthProvider'

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
  const navigate = useNavigate()
  const location = useLocation()

  useEffect(() => {
    if (new URLSearchParams(location.search).get('reason') === 'expired') {
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
      auth.signIn(result.userId)
      const redirect = new URLSearchParams(location.search).get('redirect') || '/interview'
      await navigate(redirect, { replace: true })
    } catch (reason) {
      feedback.notify(reason instanceof Error ? reason.message : '请求失败', 'error')
    } finally {
      setBusy(false)
    }
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
            <header className="page__header login-card__header">
              <h1 id="auth-title" className="page__title">
                {mode === 'login' ? '进入面试工作台' : '创建工作台账号'}
              </h1>
            </header>

            <div className="segmented-control" role="group" aria-label="账号操作">
              <button
                className={mode === 'login' ? 'is-active' : ''}
                type="button"
                onClick={() => switchMode('login')}
              >
                登录
              </button>
              <button
                className={mode === 'register' ? 'is-active' : ''}
                type="button"
                onClick={() => switchMode('register')}
              >
                注册
              </button>
            </div>

            <form
              className="form-grid auth-form"
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
                <div className="password-field">
                  <Input
                    id="auth-password"
                    value={password}
                    onChange={(event) => setPassword(event.target.value)}
                    type={showPassword ? 'text' : 'password'}
                    autoComplete={mode === 'login' ? 'current-password' : 'new-password'}
                    placeholder="请输入密码"
                    required
                  />
                  <IconTooltip label={showPassword ? '隐藏密码' : '显示密码'}>
                    <button
                      type="button"
                      className="password-field__toggle ui-action ui-action-icon"
                      aria-label={showPassword ? '隐藏密码' : '显示密码'}
                      onClick={() => setShowPassword((value) => !value)}
                    >
                      {showPassword ? <Eye size={17} /> : <EyeOff size={17} />}
                    </button>
                  </IconTooltip>
                </div>
              </Field>

              <div
                className={`auth-email-field${mode === 'register' ? ' is-visible' : ''}`}
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
                <Button type="submit" className="login-card__submit" loading={busy}>
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
