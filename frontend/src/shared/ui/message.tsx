import { cn } from '@/shared/lib/cn'
import type { ReactNode } from 'react'

/**
 * One turn of a conversation: the speaker line above the bubble. Which role says
 * what is the caller's copy — a live turn carries no score or coaching, those
 * belong to the finished report.
 */
export function MessageBubble({
  side,
  speaker,
  pending = false,
  children,
}: {
  side: 'assistant' | 'user'
  speaker: ReactNode
  pending?: boolean
  children?: ReactNode
}) {
  const user = side === 'user'
  return (
    <article
      className={cn('message-bubble', user ? 'items-end self-end' : 'items-start self-start')}
    >
      <div className="flex w-full items-center gap-sm">
        <span className={cn('font-serif text-xs text-text-tertiary', user && 'ms-auto')}>
          {speaker}
        </span>
      </div>
      <div
        className={cn(
          'message-bubble-body',
          user
            ? 'rounded-lg rounded-se-sm bg-surface-muted'
            : 'rounded-lg rounded-ss-sm bg-surface elevated-whisper',
        )}
      >
        {pending ? <span className="ellipsis-progress text-text-tertiary">思考中</span> : children}
      </div>
    </article>
  )
}
