import { createContext, use } from 'react'

export type AuthStatus = 'checking' | 'authenticated' | 'anonymous'

export type AuthValue = {
  status: AuthStatus
  accountId: number | null
  expired: boolean
  signIn: (accountId: number) => Promise<void>
  signOut: () => Promise<void>
}

export const AuthContext = createContext<AuthValue | null>(null)

export function useAuth() {
  const value = use(AuthContext)
  if (!value) throw new Error('AuthProvider is missing')
  return value
}
