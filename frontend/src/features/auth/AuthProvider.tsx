import { createContext, use, useCallback, useEffect, useRef, useState, type ReactNode } from 'react'
import { useQueryClient } from '@tanstack/react-query'
import { configureApi } from '@/shared/api/client'
import { fetchCurrentUser, logout } from './api'

export type AuthStatus = 'checking' | 'authenticated' | 'anonymous'

type AuthValue = {
  status: AuthStatus
  accountId: number | null
  expired: boolean
  signIn: (accountId: number) => Promise<void>
  signOut: () => Promise<void>
}

const AuthContext = createContext<AuthValue | null>(null)

export function AuthProvider({ children }: { children: ReactNode }) {
  const client = useQueryClient()
  const [status, setStatus] = useState<AuthStatus>('checking')
  const [accountId, setAccountId] = useState<number | null>(null)
  const [expired, setExpired] = useState(false)
  const principal = useRef(accountId)

  const disposePrincipal = useCallback(
    async (reason: 'expired' | 'sign-out' | 'change') => {
      principal.current = null
      setStatus('anonymous')
      setExpired(reason === 'expired')
      setAccountId(null)
      await client.cancelQueries()
      client.clear()
    },
    [client],
  )

  useEffect(
    () =>
      configureApi({
        onUnauthorized: () => disposePrincipal('expired'),
      }),
    [disposePrincipal],
  )

  useEffect(() => {
    let cancelled = false
    void (async () => {
      const current = await fetchCurrentUser()
      if (cancelled) return
      if (current) {
        principal.current = current.accountId
        setAccountId(current.accountId)
        setStatus('authenticated')
      } else {
        setStatus('anonymous')
      }
    })()
    return () => {
      cancelled = true
    }
  }, [])

  return (
    <AuthContext
      value={{
        status,
        accountId,
        expired,
        signIn: async (id) => {
          if (principal.current !== id) await disposePrincipal('change')
          principal.current = id
          setExpired(false)
          setAccountId(id)
          setStatus('authenticated')
        },
        signOut: async () => {
          try {
            await logout()
          } finally {
            await disposePrincipal('sign-out')
          }
        },
      }}
    >
      {children}
    </AuthContext>
  )
}

// eslint-disable-next-line react-refresh/only-export-components
export function useAuth() {
  const value = use(AuthContext)
  if (!value) throw new Error('AuthProvider is missing')
  return value
}
