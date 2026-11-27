import { Navigate, Outlet, useLocation } from 'react-router'
import { AuthUnavailablePage, useAuth } from '@/features/auth'

export function RequireAuth() {
  const { status, expired, retry } = useAuth()
  const location = useLocation()
  if (status === 'authenticated') return <Outlet />
  if (status === 'checking')
    return <div className="flex min-h-dvh items-center justify-center" aria-busy="true" />
  if (status === 'unavailable') return <AuthUnavailablePage onRetry={() => void retry()} />
  const redirect = `${location.pathname}${location.search}`
  const target = expired
    ? `/login?reason=expired&redirect=${encodeURIComponent(redirect)}`
    : '/login'
  return <Navigate to={target} replace />
}
