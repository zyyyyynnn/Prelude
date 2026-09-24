import {
  ErrorState,
  LoadingState,
  HiddenFileInput,
  SubSection,
  Button,
  Field,
  FieldAction,
  FieldActions,
  Input,
  Panel,
  useFeedback,
} from '@/shared/ui'
import { useRef, useState, type FormEvent } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Eye, EyeOff, Upload } from 'lucide-react'
import { formText } from '@/shared/lib/form-data'
import { fetchProfile, saveProfile, uploadAvatar } from '../api'
import { sectionTitles } from '../settings-context'

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
    if (!profile.data) return
    save.mutate({
      username: formText(data, 'username'),
      email: formText(data, 'email'),
      oldPassword: oldPassword || undefined,
      newPassword: newPassword || undefined,
      expectedRevision: profile.data.revision,
      operationId: crypto.randomUUID(),
    })
  }
  if (profile.isPending) return <LoadingState message="正在读取账号资料…" />
  if (profile.isError)
    return <ErrorState message={profile.error.message} onRetry={() => void profile.refetch()} />
  const initial = (profile.data?.username?.trim()[0] || 'P').toUpperCase()
  return (
    <Panel
      title={sectionTitles.profile}
      actions={
        <Button type="submit" form="profile-settings-form" loading={save.isPending}>
          保存设置
        </Button>
      }
    >
      <form
        className="flex flex-col gap-md"
        id="profile-settings-form"
        key={`${profile.data?.username}:${profile.data?.email}:${profile.data?.avatarUrl}`}
        onSubmit={submit}
      >
        <section className="grid items-center justify-items-center gap-sm">
          <div className="grid size-(--layout-avatar-inline-size) place-items-center overflow-hidden rounded-full bg-surface-muted font-serif text-lg font-semibold text-brand elevated-ring">
            {profile.data?.avatarUrl ? (
              <img className="size-full object-cover" src={profile.data.avatarUrl} alt="当前头像" />
            ) : (
              <span>{initial}</span>
            )}
          </div>
          <div className="grid justify-items-center gap-xs">
            <HiddenFileInput
              id="avatar-upload"
              label="选择头像"
              accept="image/png,image/jpeg,image/webp,image/gif"
              inputRef={avatarInput}
              onFiles={(files) => avatar.mutate(files[0])}
            />
            <Button
              type="button"
              variant="secondary"
              loading={avatar.isPending}
              onClick={() => avatarInput.current?.click()}
            >
              <Upload />
              上传头像
            </Button>
          </div>
        </section>
        <div className="form-grid gap-md">
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
        <SubSection title="修改密码">
          <div className="form-grid gap-md">
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
        </SubSection>
      </form>
    </Panel>
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
      <FieldActions
        actions={[
          <FieldAction
            label={visible ? '隐藏密码' : '显示密码'}
            icon={visible ? <Eye /> : <EyeOff />}
            onClick={onToggle}
          />,
        ]}
      >
        <Input
          id={name}
          name={name}
          type={visible ? 'text' : 'password'}
          autoComplete={autoComplete}
          placeholder="留空表示不修改密码"
        />
      </FieldActions>
    </Field>
  )
}
