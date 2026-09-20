import { Pin, Trash2 } from 'lucide-react'
import type { ReactNode } from 'react'
import { cn } from '@/shared/lib/cn'
import { IconTooltip } from '@/shared/ui/overlay'

export type SessionRowState = 'idle' | 'active' | 'loading' | 'error'

/** One row of the session list: open the session, pin it, or delete it. The pin and
 *  delete actions ride on hover/focus so the row keeps a single tab stop. */
export function SessionRow({
  name,
  state = 'idle',
  finished = false,
  pinned = false,
  onOpen,
  onTogglePin,
  onRemove,
}: {
  name: string
  state?: SessionRowState
  finished?: boolean
  pinned?: boolean
  onOpen: () => void
  onTogglePin: () => void
  onRemove: () => void
}) {
  const isLoading = state === 'loading'
  const isFailed = state === 'error'
  const actionPrefix = isFailed ? '重试打开会话' : finished ? '打开已结束会话' : '打开会话'

  return (
    <li className="session-row-host group/row">
      <button
        className={cn(
          'session-row ui-action ui-action-nav',
          state === 'active' && 'is-active',
          isLoading && 'is-loading',
          isFailed && 'is-error',
        )}
        aria-label={`${actionPrefix} ${name}`}
        aria-busy={isLoading || undefined}
        onClick={onOpen}
      >
        <span className="min-w-0 truncate">{name}</span>
        {(isLoading || isFailed) && (
          <span className="ms-auto shrink-0 text-xs">{isLoading ? '加载中' : '加载失败'}</span>
        )}
      </button>
      {pinned && (
        <Pin
          className="pointer-events-none absolute top-1/2 inset-e-sm flex size-(--ui-glyph-sm) -translate-y-1/2 items-center text-accent-text opacity-80 group-hover/row:hidden group-focus-within/row:hidden"
          fill="currentColor"
          aria-hidden="true"
        />
      )}
      <div className="session-row-actions group-hover/row:opacity-100 group-focus-within/row:opacity-100">
        <IconTooltip label={pinned ? '取消置顶' : '置顶会话'}>
          <button
            className="row-action ui-action ui-action-icon"
            aria-label={pinned ? '取消置顶' : '置顶会话'}
            onClick={onTogglePin}
          >
            <Pin fill={pinned ? 'currentColor' : 'none'} />
          </button>
        </IconTooltip>
        <IconTooltip label="删除会话">
          <button
            className="row-action row-action-danger ui-action ui-action-danger"
            aria-label="删除会话"
            onClick={onRemove}
          >
            <Trash2 />
          </button>
        </IconTooltip>
      </div>
    </li>
  )
}

/** A labelled run of session rows, or the empty note when a group has none. */
/** The caption above a session group, and the stand-in when the group has no rows. The
 *  loading placeholder in the shell is the same voice as an empty group, so it is the same
 *  element rather than a second copy of the atoms. */
export function SessionGroupLabel({ children }: { children: ReactNode }) {
  return <p className="mx-sm text-xs font-semibold tracking-label text-text-tertiary">{children}</p>
}

export function SessionGroup({
  label,
  rows,
  emptyLabel,
}: {
  label: string
  rows: {
    key: string | number
    name: string
    state?: SessionRowState
    finished?: boolean
    pinned?: boolean
    onOpen: () => void
    onTogglePin: () => void
    onRemove: () => void
  }[]
  emptyLabel: string
}) {
  return (
    <section className="session-group" aria-label={label}>
      <SessionGroupLabel>{label}</SessionGroupLabel>
      {rows.length ? (
        <ul className="list-plain flex flex-col gap-sm">
          {rows.map(({ key, ...row }) => (
            <SessionRow key={key} {...row} />
          ))}
        </ul>
      ) : (
        <SessionGroupLabel>{emptyLabel}</SessionGroupLabel>
      )}
    </section>
  )
}
