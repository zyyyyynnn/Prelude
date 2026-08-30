import { Navigate, Outlet, useLocation } from 'react-router'
import { useAuth } from '@/features/auth'

export function RequireAuth() {
  const { userId, expired } = useAuth()
  const location = useLocation()
  if (userId) return <Outlet />
  const redirect = `${location.pathname}${location.search}`
  const target = expired
    ? `/login?reason=expired&redirect=${encodeURIComponent(redirect)}`
    : '/login'
  return <Navigate to={target} replace />
}
