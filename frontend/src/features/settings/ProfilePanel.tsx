import { useRef, useState, type FormEvent } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Eye, EyeOff, Upload } from 'lucide-react'
import { Button, Field, IconTooltip, Input } from '@/shared/ui'
import { useFeedback } from '@/shared/ui/feedback'
import { formText } from '@/shared/lib/form-data'
import { fetchProfile, saveProfile, uploadAvatar } from './api'

export function ProfilePanel() {
  const profile = useQuery({ queryKey: ['profile'], queryFn: fetchProfile })
  const client = useQueryClient()
  const feedback = useFeedback()
  const avatarInput = useRef<HTMLInputElement>(null)
  const [oldVisible, setOldVisible] = useState(false)
  const [newVisible, setNewVisible] = useState(false)
  const save = useMutation({
    mutationFn: saveProfile,
    onSuccess: (data) => {
      client.setQueryData(['profile'], data)
      feedback.notify('资料已保存', 'success')
    },
    onError: (error) => feedback.notify(error.message, 'error'),
  })
  const avatar = useMutation({
    mutationFn: uploadAvatar,
    onSuccess: (data) => {
      client.setQueryData(['profile'], data)
      feedback.notify('头像已更新', 'success')
    },
    onError: (error) => feedback.notify(error.message, 'error'),
  })
  function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const data = new FormData(event.currentTarget)
    const oldPassword = formText(data, 'oldPassword')
    const newPassword = formText(data, 'newPassword')
    if (Boolean(oldPassword) !== Boolean(newPassword)) {
      feedback.notify('修改密码时必须同时填写旧密码和新密码', 'error')
      return
    }
    if (oldPassword && oldPassword === newPassword) {
      feedback.notify('新密码不能与旧密码相同', 'error')
      return
    }
    save.mutate({
      username: formText(data, 'username'),
      email: formText(data, 'email'),
      oldPassword: oldPassword || undefined,
      newPassword: newPassword || undefined,
    })
  }
  if (profile.isPending) return <div className="empty-state">正在读取账号资料…</div>
  if (profile.isError) return <div className="empty-state">{profile.error.message}</div>
  const initial = (profile.data?.username?.trim()[0] || 'P').toUpperCase()
  return (
    <form
      className="panel-content-wrapper"
      key={`${profile.data?.username}:${profile.data?.email}:${profile.data?.avatarUrl}`}
      onSubmit={submit}
    >
      <section className="profile-avatar-row">
        <div className="profile-avatar">
          {profile.data?.avatarUrl ? (
            <img className="profile-avatar__image" src={profile.data.avatarUrl} alt="当前头像" />
          ) : (
            <span>{initial}</span>
          )}
        </div>
        <div className="profile-avatar__actions">
          <label className="sr-only" htmlFor="avatar-upload">
            选择头像
          </label>
          <input
            id="avatar-upload"
            ref={avatarInput}
            className="sr-only"
            type="file"
            accept="image/png,image/jpeg,image/webp,image/gif"
            onChange={(event) => {
              const file = event.target.files?.[0]
              if (file) avatar.mutate(file)
              event.currentTarget.value = ''
            }}
          />
          <Button
            type="button"
            variant="secondary"
            loading={avatar.isPending}
            onClick={() => avatarInput.current?.click()}
          >
            <Upload size={15} />
            上传头像
          </Button>
        </div>
      </section>
      <div className="field-grid">
        <Field label="用户名" htmlFor="profile-username">
          <Input
            id="profile-username"
            name="username"
            required
            autoComplete="username"
            defaultValue={profile.data?.username}
          />
        </Field>
        <Field label="邮箱" htmlFor="profile-email">
          <Input
            id="profile-email"
            name="email"
            type="email"
            autoComplete="email"
            defaultValue={profile.data?.email}
          />
        </Field>
      </div>
      <section className="settings-form-section">
        <h3 className="settings-form-section__title">修改密码</h3>
        <div className="field-grid">
          <PasswordField
            label="旧密码"
            name="oldPassword"
            visible={oldVisible}
            onToggle={() => setOldVisible((value) => !value)}
            autoComplete="current-password"
          />
          <PasswordField
            label="新密码"
            name="newPassword"
            visible={newVisible}
            onToggle={() => setNewVisible((value) => !value)}
            autoComplete="new-password"
          />
        </div>
      </section>
      <div className="settings-inline-actions settings-inline-actions--header">
        <Button type="submit" loading={save.isPending}>
          保存设置
        </Button>
      </div>
    </form>
  )
}

function PasswordField({
  label,
  name,
  visible,
  onToggle,
  autoComplete,
}: {
  label: string
  name: string
  visible: boolean
  onToggle: () => void
  autoComplete: string
}) {
  return (
    <Field label={label} htmlFor={name}>
      <div className="password-field">
        <Input
          id={name}
          name={name}
          type={visible ? 'text' : 'password'}
          autoComplete={autoComplete}
          placeholder="留空表示不修改密码"
        />
        <IconTooltip label={visible ? '隐藏密码' : '显示密码'}>
          <button
            type="button"
            className="password-toggle ui-action ui-action-icon"
            aria-label={visible ? '隐藏密码' : '显示密码'}
            onClick={onToggle}
          >
            {visible ? <Eye size={16} /> : <EyeOff size={16} />}
          </button>
        </IconTooltip>
      </div>
    </Field>
  )
}
