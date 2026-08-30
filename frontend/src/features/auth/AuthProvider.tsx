import { createContext, use, useCallback, useEffect, useRef, useState, type ReactNode } from 'react'
import { useQueryClient } from '@tanstack/react-query'
import { configureApi } from '@/shared/api/client'
import { logout } from './api'

const STORAGE_KEY = 'prelude-user-id'

type AuthValue = {
  userId: number | null
  expired: boolean
  signIn: (userId: number) => Promise<void>
  signOut: () => Promise<void>
}

const AuthContext = createContext<AuthValue | null>(null)

export function AuthProvider({ children }: { children: ReactNode }) {
  const client = useQueryClient()
  const [userId, setUserId] = useState<number | null>(() => {
    try {
      const value = localStorage.getItem(STORAGE_KEY)
      return value && Number.isInteger(Number(value)) ? Number(value) : null
    } catch {
      return null
    }
  })
  const [expired, setExpired] = useState(false)
  const principal = useRef(userId)

  const disposePrincipal = useCallback(
    async (reason: 'expired' | 'sign-out' | 'change') => {
      principal.current = null
      try {
        localStorage.removeItem(STORAGE_KEY)
      } catch {
        // Storage can be unavailable; in-memory auth state remains authoritative.
      }
      setExpired(reason === 'expired')
      setUserId(null)
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

  return (
    <AuthContext
      value={{
        userId,
        expired,
        signIn: async (id) => {
          if (principal.current !== id) await disposePrincipal('change')
          principal.current = id
          try {
            localStorage.setItem(STORAGE_KEY, String(id))
          } catch {
            // The authenticated session still works without persistent storage.
          }
          setExpired(false)
          setUserId(id)
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
