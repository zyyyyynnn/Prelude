import { useState, type FormEvent } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Pencil, Plus, RefreshCw, Trash2 } from 'lucide-react'
import { Button, Field, Input, Textarea } from '@/shared/ui'
import { useFeedback } from '@/shared/ui/feedback'
import { createPosition, deletePosition, fetchPositions, updatePosition } from './api'
import type { PositionTemplate } from './types'

const emptyDraft = { name: '', systemPrompt: '' }

export function PositionManagementPanel() {
  const client = useQueryClient()
  const feedback = useFeedback()
  const [editing, setEditing] = useState<PositionTemplate | null>(null)
  const [draft, setDraft] = useState(emptyDraft)
  const positions = useQuery({ queryKey: ['positions'], queryFn: fetchPositions })
  const save = useMutation({
    mutationFn: () =>
      editing
        ? updatePosition(editing.id, draft)
        : createPosition(draft),
    onSuccess: () => {
      feedback.notify(editing ? '岗位已更新' : '岗位已创建', 'success')
      setEditing(null)
      setDraft(emptyDraft)
      void client.invalidateQueries({ queryKey: ['positions'] })
    },
    onError: (error) => feedback.notify(error.message, 'error'),
  })
  const remove = useMutation({
    mutationFn: deletePosition,
    onSuccess: () => {
      feedback.notify('岗位已删除', 'success')
      setEditing(null)
      setDraft(emptyDraft)
      void client.invalidateQueries({ queryKey: ['positions'] })
    },
    onError: (error) => feedback.notify(error.message, 'error'),
  })

  function edit(position: PositionTemplate) {
    setEditing(position)
    setDraft({ name: position.name, systemPrompt: position.systemPrompt ?? '' })
  }

  function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (!draft.name.trim() || !draft.systemPrompt.trim()) {
      feedback.notify('请填写岗位名称和面试侧重点', 'error')
      return
    }
    save.mutate()
  }

  async function removePosition(position: PositionTemplate) {
    const accepted = await feedback.confirm({
      title: '删除岗位',
      message: `确认删除“${position.name}”？删除后无法恢复。`,
      confirmText: '删除',
      danger: true,
    })
    if (accepted) remove.mutate(position.id)
  }

  return (
    <div className="panel-content-wrapper position-settings">
      <div className="settings-inline-actions settings-inline-actions--header">
        {editing && (
          <Button
            type="button"
            variant="danger"
            loading={remove.isPending}
            disabled={save.isPending}
            onClick={() => void removePosition(editing)}
          >
            <Trash2 aria-hidden="true" />
            删除岗位
          </Button>
        )}
        <Button
          type="submit"
          form="position-settings-form"
          loading={save.isPending}
          disabled={remove.isPending}
        >
          {editing ? '保存岗位' : '创建岗位'}
        </Button>
      </div>
      <section className="position-settings__catalog" aria-label="岗位列表">
        <h3 className="position-settings__section-title">可选岗位</h3>
        {positions.isPending ? (
          <div className="empty-state">正在读取岗位…</div>
        ) : positions.isError ? (
          <div className="empty-state">
            <p>{positions.error.message}</p>
            <Button variant="secondary" onClick={() => void positions.refetch()}>
              <RefreshCw aria-hidden="true" />
              重新加载
            </Button>
          </div>
        ) : (
          <div className="position-settings__list">
            {positions.data?.map((position) => (
              <div className="position-settings__item" key={position.id}>
                <span>{position.name}</span>
                {position.editable ? (
                  <Button
                    type="button"
                    size="icon"
                    variant="ghost"
                    aria-label={`编辑 ${position.name}`}
                    onClick={() => edit(position)}
                  >
                    <Pencil aria-hidden="true" />
                  </Button>
                ) : (
                  <span className="status-badge">内置</span>
                )}
              </div>
            ))}
          </div>
        )}
      </section>
      <form id="position-settings-form" className="position-settings__form" onSubmit={submit}>
        <div className="position-settings__form-heading">
          <h3 className="position-settings__section-title">{editing ? '编辑岗位' : '新建岗位'}</h3>
          {editing && (
            <Button
              type="button"
              size="compact"
              variant="ghost"
              onClick={() => {
                setEditing(null)
                setDraft(emptyDraft)
              }}
            >
              <Plus aria-hidden="true" />
              新建
            </Button>
          )}
        </div>
        <div className="position-settings__fields">
          <Field label="岗位名称" htmlFor="position-name">
            <Input
              id="position-name"
              autoFocus
              maxLength={100}
              value={draft.name}
              onChange={(event) =>
                setDraft((current) => ({ ...current, name: event.target.value }))
              }
            />
          </Field>
          <Field
            label="面试侧重点"
            htmlFor="position-prompt"
            hint="描述需要重点考察的能力、追问方式与面试风格。"
          >
            <Textarea
              id="position-prompt"
              maxLength={4000}
              rows={3}
              value={draft.systemPrompt}
              onChange={(event) =>
                setDraft((current) => ({ ...current, systemPrompt: event.target.value }))
              }
            />
          </Field>
        </div>
      </form>
    </div>
  )
}
