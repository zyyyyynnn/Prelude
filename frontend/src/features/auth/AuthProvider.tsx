import { useCallback, useEffect, useRef, useState, type ReactNode } from 'react'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { configureApi } from '@/shared/api/client'
import { fetchCurrentUser, logout } from './api'
import { AuthContext, type AuthStatus } from './auth-context'

const authQueryKey = ['auth-session'] as const

export function AuthProvider({ children }: { children: ReactNode }) {
  const client = useQueryClient()
  const [signedInAccountId, setSignedInAccountId] = useState<number | null>(null)
  const [anonymousOverride, setAnonymousOverride] = useState(false)
  const [expired, setExpired] = useState(false)
  const principal = useRef<number | null>(null)
  const session = useQuery({
    queryKey: authQueryKey,
    queryFn: ({ signal }) => fetchCurrentUser(signal),
    enabled: signedInAccountId == null && !anonymousOverride,
    retry: false,
    staleTime: Infinity,
  })
  const sessionAccountId =
    session.data?.status === 'authenticated' ? session.data.user.accountId : null
  const accountId = signedInAccountId ?? sessionAccountId
  const status: AuthStatus =
    signedInAccountId != null
      ? 'authenticated'
      : anonymousOverride
        ? 'anonymous'
        : session.isPending
          ? 'checking'
          : (session.data?.status ?? 'unavailable')

  useEffect(() => {
    principal.current = accountId
  }, [accountId])

  const retry = useCallback(async () => {
    setExpired(false)
    await session.refetch()
  }, [session])

  const disposePrincipal = useCallback(
    async (reason: 'expired' | 'sign-out' | 'change') => {
      principal.current = null
      setSignedInAccountId(null)
      setAnonymousOverride(true)
      setExpired(reason === 'expired')
      await client.cancelQueries()
      client.clear()
      client.setQueryData(authQueryKey, { status: 'anonymous' })
    },
    [client],
  )

  useEffect(
    () => configureApi({ onUnauthorized: () => disposePrincipal('expired') }),
    [disposePrincipal],
  )

  return (
    <AuthContext
      value={{
        status,
        accountId,
        expired,
        retry,
        signIn: async (id) => {
          if (principal.current !== id) await disposePrincipal('change')
          setAnonymousOverride(false)
          setSignedInAccountId(id)
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
