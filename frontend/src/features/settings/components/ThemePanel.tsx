import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { cn } from '@/shared/lib/cn'
import { Button } from '@/shared/ui/button'
import { Panel } from '@/shared/ui/panel'
import { useFeedback } from '@/shared/ui/feedback-context'
import { fetchProfile, saveProfile } from '../index'
import { sectionTitles } from '../settings-context'
import { applyTheme, readTheme } from '../theme'
import type { ThemePreference } from '../types'

const themeOptions: Array<{ value: ThemePreference; label: string; description: string }> = [
  { value: 'light', label: '浅色', description: '暖色纸面' },
  { value: 'dark', label: '暗色', description: '低亮度阅读' },
  { value: 'system', label: '跟随系统', description: '自动同步' },
]

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
          <button
            key={option.value}
            type="button"
            role="radio"
            aria-checked={value === option.value}
            className={cn(
              'option-card ui-action ui-action-selectable',
              value === option.value && 'is-active',
            )}
            onClick={() => {
              setValue(option.value)
              applyTheme(option.value)
            }}
          >
            <span className="grid h-(--ui-height-control) grid-cols-2 gap-xs">
              <span
                className={cn(
                  'rounded-sm',
                  option.value === 'dark' ? 'bg-text-secondary' : 'bg-surface-muted',
                )}
              />
              <span
                className={cn(
                  'rounded-sm',
                  option.value === 'light' ? 'bg-surface-muted' : 'bg-text-secondary',
                )}
              />
            </span>
            <span className="grid gap-xs">
              <span className="font-serif text-sm font-semibold">{option.label}</span>
              <span className="type-meta">{option.description}</span>
            </span>
          </button>
        ))}
      </div>
    </Panel>
  )
}
