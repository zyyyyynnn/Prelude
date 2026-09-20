import { useState, type FormEvent } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Plus, RefreshCw, Trash2 } from 'lucide-react'
import { Button } from '@/shared/ui/button'
import { Field, Input, Textarea } from '@/shared/ui/field'
import { Panel } from '@/shared/ui/panel'
import { useFeedback } from '@/shared/ui/feedback-context'
import { sectionTitles } from '@/features/settings'
import {
  createPosition,
  deletePosition,
  fetchPositions,
  PositionRow,
  updatePosition,
} from './index'
import type { Position } from './types'

const emptyDraft = { name: '', systemPrompt: '' }

export function PositionManagementPanel() {
  const client = useQueryClient()
  const feedback = useFeedback()
  const [editing, setEditing] = useState<Position | null>(null)
  const [draft, setDraft] = useState(emptyDraft)
  const positions = useQuery({ queryKey: ['positions'], queryFn: fetchPositions })
  const save = useMutation({
    mutationFn: () => (editing ? updatePosition(editing.id, draft) : createPosition(draft)),
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

  function edit(position: Position) {
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

  async function removePosition(position: Position) {
    const accepted = await feedback.confirm({
      title: '删除岗位',
      message: `确认删除“${position.name}”？删除后无法恢复。`,
      confirmText: '删除',
      danger: true,
    })
    if (accepted) remove.mutate(position.id)
  }

  return (
    <Panel
      title={sectionTitles.positions}
      actions={
        <>
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
        </>
      }
    >
      <div className="flex flex-wrap items-start gap-md" data-slot="position-workspace">
        <Panel
          layout="card"
          level={3}
          title="岗位库"
          className="flex-1 basis-(--layout-position-catalog-min-inline-size)"
          data-slot="position-catalog"
        >
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
            <div className="position-item-grid" role="list" aria-label="岗位列表">
              {positions.data?.map((position) => (
                <PositionRow
                  key={position.id}
                  name={position.name}
                  editable={position.editable}
                  onEdit={() => edit(position)}
                />
              ))}
            </div>
          )}
        </Panel>
        <Panel
          layout="card"
          level={3}
          className="grow-2 basis-(--layout-position-form-min-inline-size)"
          title={editing ? '编辑岗位' : '新建岗位'}
          actions={
            editing && (
              <Button
                type="button"
                variant="ghost"
                onClick={() => {
                  setEditing(null)
                  setDraft(emptyDraft)
                }}
              >
                <Plus aria-hidden="true" />
                新建
              </Button>
            )
          }
          data-slot="position-form"
        >
          <form
            id="position-settings-form"
            className="grid gap-md"
            data-slot="position-fields"
            onSubmit={submit}
          >
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
                rows={7}
                value={draft.systemPrompt}
                onChange={(event) =>
                  setDraft((current) => ({ ...current, systemPrompt: event.target.value }))
                }
              />
            </Field>
          </form>
        </Panel>
      </div>
    </Panel>
  )
}
