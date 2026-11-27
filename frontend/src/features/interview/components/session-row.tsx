import { cn } from '@/shared/lib/cn'
import { Icon, IconTooltip } from '@/shared/ui'
import { RiDeleteBinLine, RiPushpinFill, RiPushpinLine } from '@remixicon/react'

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
        <span
          aria-hidden="true"
          className="pointer-events-none absolute top-1/2 inset-e-sm flex size-(--ui-glyph-sm) -translate-y-1/2 items-center text-accent-text opacity-80 group-hover/row:hidden group-focus-within/row:hidden"
        >
          <Icon as={RiPushpinFill} size="sm" />
        </span>
      )}
      <div className="session-row-actions group-hover/row:opacity-100 group-focus-within/row:opacity-100">
        <IconTooltip label={pinned ? '取消置顶' : '置顶会话'}>
          <button
            className="row-action ui-action ui-action-icon"
            aria-label={pinned ? '取消置顶' : '置顶会话'}
            onClick={onTogglePin}
          >
            <Icon as={pinned ? RiPushpinFill : RiPushpinLine} />
          </button>
        </IconTooltip>
        <IconTooltip label="删除会话">
          <button
            className="row-action row-action-danger ui-action ui-action-danger"
            aria-label="删除会话"
            onClick={onRemove}
          >
            <Icon as={RiDeleteBinLine} />
          </button>
        </IconTooltip>
      </div>
    </li>
  )
}
