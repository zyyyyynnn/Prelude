import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Button } from '@/shared/ui'
import { useFeedback } from '@/shared/ui/feedback'
import { fetchProfile, saveProfile } from './api'
import { applyTheme, readTheme } from './theme'
import type { ThemePreference } from './types'

const options: Array<{ value: ThemePreference; label: string; description: string }> = [
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
      saveProfile({ themePreference: value, expectedRevision: revision, operationId: crypto.randomUUID() }),
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
    <div className="panel-content-wrapper">
      <div className="theme-grid" role="radiogroup" aria-label="主题偏好">
        {options.map((option) => (
          <button
            key={option.value}
            type="button"
            role="radio"
            aria-checked={value === option.value}
            className={`theme-option ui-action ui-action-selectable${value === option.value ? ' is-active' : ''}`}
            onClick={() => {
              setValue(option.value)
              applyTheme(option.value)
            }}
          >
            <span className="theme-option__preview" data-theme-preview={option.value}>
              <span />
              <span />
            </span>
            <span className="theme-option__copy">
              <span className="theme-option__label">{option.label}</span>
              <span className="theme-option__desc">{option.description}</span>
            </span>
          </button>
        ))}
      </div>
      <div className="settings-inline-actions settings-inline-actions--header">
        <Button loading={save.isPending} disabled={value === initial} onClick={() => save.mutate()}>
          保存主题
        </Button>
      </div>
    </div>
  )
}
