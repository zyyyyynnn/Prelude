import { Navigate, Outlet } from 'react-router'
import { useAuth } from '@/features/auth'

export function RequireAuth() {
  const { userId } = useAuth()
  return userId ? <Outlet /> : <Navigate to="/login" replace />
}
