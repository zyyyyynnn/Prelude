import type { ReactNode } from 'react'

export function AuthPage({ children }: { children: ReactNode }) {
  return <main className="page page--center page--viewport">{children}</main>
}
