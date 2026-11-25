import { ErrorState, LoadingState, Button, Panel, useFeedback } from '@/shared/ui'
import { ThemeChoiceGroup } from './theme-picker'
import { useLayoutEffect, useRef, useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { fetchProfile, profileQueryKey, saveProfile } from '../api'
import { themeOptions } from '../types'
import { sectionTitles } from '../settings-context'
import { applyTheme } from '../theme'
import { useAuth } from '@/features/auth'
import type { ThemePreference } from '../types'

export function ThemePanel() {
  const auth = useAuth()
  const queryKey = profileQueryKey(auth.accountId ?? 0)
  const profile = useQuery({
    queryKey,
    queryFn: ({ signal }) => fetchProfile(signal),
    enabled: auth.accountId != null,
  })
  if (profile.isPending) return <LoadingState message="正在读取主题偏好…" />
  if (profile.isError)
    return <ErrorState message={profile.error.message} onRetry={() => void profile.refetch()} />
  const initial = profile.data?.themePreference ?? 'system'
  const accountId = profile.data?.accountId ?? auth.accountId ?? 0
  return (
    <ThemeForm
      key={`${accountId}:${initial}`}
      initial={initial}
      revision={profile.data?.revision ?? 0}
    />
  )
}

function ThemeForm({ initial, revision }: { initial: ThemePreference; revision: number }) {
  const [value, setValue] = useState(initial)
  const auth = useAuth()
  const queryKey = profileQueryKey(auth.accountId ?? 0)
  const client = useQueryClient()
  const feedback = useFeedback()
  const mounted = useRef(true)
  const currentAccountId = useRef(auth.accountId)
  const writeSequence = useRef(0)

  useLayoutEffect(() => {
    mounted.current = true
    currentAccountId.current = auth.accountId
    return () => {
      mounted.current = false
    }
  }, [auth.accountId])

  const save = useMutation({
    mutationFn: () =>
      saveProfile({
        themePreference: value,
        expectedRevision: revision,
        operationId: crypto.randomUUID(),
      }),
    onMutate: () => {
      void client.cancelQueries({ queryKey })
      return {
        accountId: auth.accountId,
        queryKey,
        sequence: ++writeSequence.current,
      }
    },
    onSuccess: (data, _, context) => {
      if (
        !context ||
        !mounted.current ||
        context.sequence !== writeSequence.current ||
        context.accountId !== currentAccountId.current ||
        data.accountId !== context.accountId
      ) {
        return
      }
      client.setQueryData(context.queryKey, data)
      applyTheme(data.themePreference ?? value, context.accountId)
      feedback.notify('主题已保存', 'success')
    },
    onError: (error, _, context) => {
      if (
        !context ||
        !mounted.current ||
        context.sequence !== writeSequence.current ||
        context.accountId !== currentAccountId.current
      ) {
        return
      }
      applyTheme(initial, context.accountId)
      feedback.notify(error.message, 'error')
    },
  })
  return (
    <Panel
      title={sectionTitles.theme}
      actions={
        <Button loading={save.isPending} disabled={value === initial} onClick={() => save.mutate()}>
          保存主题
        </Button>
      }
    >
      <ThemeChoiceGroup
        value={value}
        options={themeOptions}
        onSelect={(next) => {
          setValue(next)
          applyTheme(next, auth.accountId ?? undefined)
        }}
      />
    </Panel>
  )
}
