import { useEffect, useRef, useState, type FormEvent } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { formText } from '@/shared/lib/form-data'
import { ApiClientError } from '@/shared/api/client'
import { ProfileAvatar } from '@/features/profile'
import { useAuth } from '@/features/auth'
import { fetchProfile, profileQueryKey, saveProfile, uploadAvatar } from '../api'
import { sectionTitles } from '../settings-context'
import type { UserProfilePayload } from '../types'
import { RiEyeLine, RiEyeOffLine, RiUploadLine } from '@remixicon/react'
import {
  Button,
  ErrorState,
  Field,
  FieldAction,
  FieldActions,
  HiddenFileInput,
  Icon,
  Input,
  LoadingState,
  Panel,
  SubSection,
  useFeedback,
} from '@/shared/ui'

type ProfileWrite = { kind: 'save'; payload: UserProfilePayload } | { kind: 'avatar'; file: File }

export function ProfilePanel() {
  const auth = useAuth()
  const queryKey = profileQueryKey(auth.accountId ?? 0)
  const profile = useQuery({
    queryKey,
    queryFn: ({ signal }) => fetchProfile(signal),
    enabled: auth.accountId != null,
  })
  const client = useQueryClient()
  const feedback = useFeedback()
  const avatarInput = useRef<HTMLInputElement>(null)
  const mounted = useRef(true)
  const activeWrite = useRef(false)
  const writeSequence = useRef(0)
  const revisionRefreshPending = useRef(false)
  const [refreshingRevision, setRefreshingRevision] = useState(false)
  const [oldVisible, setOldVisible] = useState(false)
  const [newVisible, setNewVisible] = useState(false)

  useEffect(() => {
    mounted.current = true
    return () => {
      mounted.current = false
    }
  }, [])

  const write = useMutation({
    mutationFn: ({ operation }: { operation: ProfileWrite; sequence: number }) =>
      operation.kind === 'save' ? saveProfile(operation.payload) : uploadAvatar(operation.file),
    onMutate: ({ sequence }) => {
      void client.cancelQueries({ queryKey })
      return { sequence, accountId: auth.accountId }
    },
    onSuccess: (data, _, context) => {
      if (
        !mounted.current ||
        context.sequence !== writeSequence.current ||
        data.accountId !== context.accountId
      ) {
        return
      }
      client.setQueryData(queryKey, data)
      feedback.notify(
        write.variables?.operation.kind === 'avatar' ? '头像已更新' : '资料已保存',
        'success',
      )
    },
    onError: (error, _, context) => {
      if (!mounted.current || !context || context.sequence !== writeSequence.current) return
      if (error instanceof ApiClientError && error.status === 409) {
        revisionRefreshPending.current = true
        setRefreshingRevision(true)
        void profile.refetch().finally(() => {
          if (mounted.current && context.sequence === writeSequence.current) {
            revisionRefreshPending.current = false
            setRefreshingRevision(false)
          }
        })
      }
      feedback.notify(error.message, 'error')
    },
    onSettled: (_, __, context) => {
      if (context.sequence === writeSequence.current) activeWrite.current = false
    },
  })

  function beginWrite(operation: ProfileWrite) {
    if (
      activeWrite.current ||
      revisionRefreshPending.current ||
      !profile.data ||
      auth.accountId == null
    ) {
      return
    }
    activeWrite.current = true
    write.mutate({ operation, sequence: ++writeSequence.current })
  }

  function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (activeWrite.current || revisionRefreshPending.current) return
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
    beginWrite({
      kind: 'save',
      payload: {
        username: formText(data, 'username'),
        email: formText(data, 'email'),
        oldPassword: oldPassword || undefined,
        newPassword: newPassword || undefined,
        expectedRevision: profile.data.revision,
        operationId: crypto.randomUUID(),
      },
    })
  }

  if (profile.isPending) return <LoadingState message="正在读取账号资料…" />
  if (profile.isError)
    return <ErrorState message={profile.error.message} onRetry={() => void profile.refetch()} />
  const initial = (profile.data?.username?.trim()[0] || 'P').toUpperCase()
  const saving = write.isPending && write.variables?.operation.kind === 'save'
  const uploading = write.isPending && write.variables?.operation.kind === 'avatar'
  const writeBusy = write.isPending || refreshingRevision

  return (
    <Panel
      title={sectionTitles.profile}
      actions={
        <Button
          type="submit"
          form="profile-settings-form"
          loading={saving || refreshingRevision}
          disabled={writeBusy}
        >
          保存设置
        </Button>
      }
    >
      <form
        className="flex flex-col gap-md"
        id="profile-settings-form"
        key={`${profile.data?.accountId}:${profile.data?.revision}:${profile.data?.username}:${profile.data?.email}:${profile.data?.avatarUrl}`}
        onSubmit={submit}
      >
        <section className="grid items-center justify-items-center gap-sm">
          <ProfileAvatar src={profile.data?.avatarUrl} fallback={initial} />
          <div className="grid justify-items-center gap-xs">
            <HiddenFileInput
              id="avatar-upload"
              label="选择头像"
              accept="image/png,image/jpeg,image/webp,image/gif"
              inputRef={avatarInput}
              onFiles={(files) => {
                if (files[0]) beginWrite({ kind: 'avatar', file: files[0] })
              }}
            />
            <Button
              type="button"
              variant="secondary"
              loading={uploading}
              disabled={writeBusy}
              onClick={() => avatarInput.current?.click()}
            >
              <Icon as={RiUploadLine} />
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
            icon={visible ? <Icon as={RiEyeLine} /> : <Icon as={RiEyeOffLine} />}
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
