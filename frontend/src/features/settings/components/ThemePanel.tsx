import { ErrorState, LoadingState, Button, ThemeChoiceGroup, Panel, useFeedback } from '@/shared/ui'
import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { fetchProfile, saveProfile } from '../api'
import { themeOptions } from '../types'
import { sectionTitles } from '../settings-context'
import { applyTheme, readTheme } from '../theme'
import type { ThemePreference } from '../types'

export function ThemePanel() {
  const profile = useQuery({ queryKey: ['profile'], queryFn: fetchProfile })
  if (profile.isPending) return <LoadingState message="正在读取主题偏好…" />
  if (profile.isError)
    return <ErrorState message={profile.error.message} onRetry={() => void profile.refetch()} />
  const initial = profile.data?.themePreference ?? readTheme()
  return <ThemeForm key={initial} initial={initial} revision={profile.data?.revision ?? 0} />
}

function ThemeForm({ initial, revision }: { initial: ThemePreference; revision: number }) {
  const [value, setValue] = useState(initial)
  const client = useQueryClient()
  const feedback = useFeedback()
  const save = useMutation({
    mutationFn: () =>
      saveProfile({
        themePreference: value,
        expectedRevision: revision,
        operationId: crypto.randomUUID(),
      }),
    onSuccess: (data) => {
      client.setQueryData(['profile'], data)
      applyTheme(data.themePreference ?? value)
      feedback.notify('主题已保存', 'success')
    },
    onError: (error) => {
      applyTheme(initial)
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
          applyTheme(next)
        }}
      />
    </Panel>
  )
}
