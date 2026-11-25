import { useEffect, useLayoutEffect, type ReactNode } from 'react'
import { useQuery } from '@tanstack/react-query'
import { useAuth } from '@/features/auth'
import { fetchProfile, profileQueryKey } from '@/features/settings/api'
import { applyTheme, readTheme } from '@/features/settings/theme'

export function AccountProfileSync({ children }: { children: ReactNode }) {
  const { accountId, status } = useAuth()
  const profile = useQuery({
    queryKey: profileQueryKey(accountId ?? 0),
    queryFn: ({ signal }) => fetchProfile(signal),
    enabled: status === 'authenticated' && accountId != null,
  })

  useLayoutEffect(() => {
    if (status !== 'authenticated' || accountId == null) {
      if (status === 'anonymous') applyTheme(readTheme())
      return
    }
    applyTheme(readTheme(accountId), accountId)
  }, [accountId, status])

  useEffect(() => {
    if (profile.data?.accountId === accountId) {
      applyTheme(profile.data.themePreference ?? 'system', accountId)
    }
  }, [accountId, profile.data])

  return children
}
