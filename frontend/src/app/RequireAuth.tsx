import { Navigate, Outlet, useLocation } from 'react-router'
import { useAuth } from '@/features/auth'

export function RequireAuth() {
  const { status, expired } = useAuth()
  const location = useLocation()
  if (status === 'authenticated') return <Outlet />
  if (status === 'checking') return <div className="workspace-loading" aria-busy="true" />
  const redirect = `${location.pathname}${location.search}`
  const target = expired
    ? `/login?reason=expired&redirect=${encodeURIComponent(redirect)}`
    : '/login'
  return <Navigate to={target} replace />
}
