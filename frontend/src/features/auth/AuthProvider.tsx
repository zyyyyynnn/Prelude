import { createContext, use, useEffect, useState, type ReactNode } from 'react'
import { configureApi } from '@/shared/api/client'
import { logout } from './api'

const STORAGE_KEY = 'prelude-user-id'

type AuthValue = {
  userId: number | null
  signIn: (userId: number) => void
  signOut: () => Promise<void>
}

const AuthContext = createContext<AuthValue | null>(null)

export function AuthProvider({ children }: { children: ReactNode }) {
  const [userId, setUserId] = useState<number | null>(() => {
    const value = localStorage.getItem(STORAGE_KEY)
    return value && Number.isInteger(Number(value)) ? Number(value) : null
  })

  useEffect(
    () =>
      configureApi({
        onUnauthorized: () => {
          localStorage.removeItem(STORAGE_KEY)
          setUserId(null)
        },
      }),
    [],
  )

  return (
    <AuthContext
      value={{
        userId,
        signIn: (id) => {
          localStorage.setItem(STORAGE_KEY, String(id))
          setUserId(id)
        },
        signOut: async () => {
          try {
            await logout()
          } finally {
            localStorage.removeItem(STORAGE_KEY)
            setUserId(null)
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
