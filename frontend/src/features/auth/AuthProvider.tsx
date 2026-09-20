import { useCallback, useEffect, useRef, useState, type ReactNode } from 'react'
import { useQueryClient } from '@tanstack/react-query'
import { configureApi } from '@/shared/api/client'
import { fetchCurrentUser, logout } from './api'
import { AuthContext, type AuthStatus } from './auth-context'

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
      // Drop the previous account's server cache before the next principal loads.
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
