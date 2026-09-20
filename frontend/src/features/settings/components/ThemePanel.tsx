import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Button } from '@/shared/ui/button'
import { OptionCard, ThemePreview } from '@/shared/ui/option-card'
import { Panel } from '@/shared/ui/panel'
import { useFeedback } from '@/shared/ui/feedback-context'
import { fetchProfile, saveProfile, themeOptions } from '../index'
import { sectionTitles } from '../settings-context'
import { applyTheme, readTheme } from '../theme'
import type { ThemePreference } from '../types'

export function ThemePanel() {
  const profile = useQuery({ queryKey: ['profile'], queryFn: fetchProfile })
  if (profile.isPending) return <div className="empty-state">正在读取主题偏好…</div>
  if (profile.isError) return <div className="empty-state">{profile.error.message}</div>
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
      <div className="grid grid-cols-3 gap-sm" role="radiogroup" aria-label="主题偏好">
        {themeOptions.map((option) => (
          <OptionCard
            key={option.value}
            checked={value === option.value}
            label={option.label}
            description={option.description}
            onSelect={() => {
              setValue(option.value)
              applyTheme(option.value)
            }}
          >
            <ThemePreview tone={option.value} />
          </OptionCard>
        ))}
      </div>
    </Panel>
  )
}
