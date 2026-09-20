import { RefreshCw } from 'lucide-react'
import { cn } from '@/shared/lib/cn'
import { Button } from './button'

/** What a region says when it has no content yet. `.empty-state` owns the centring and the
 *  floor height; these three own the difference between "still coming", "nothing here" and
 *  "it failed" — a difference the call sites had each been improvising, down to whether a
 *  loading message was announced at all. */
export function LoadingState({ message, className }: { message: string; className?: string }) {
  return (
    <div className={cn('empty-state', className)} role="status">
      {message}
    </div>
  )
}

export function EmptyState({ message, className }: { message: string; className?: string }) {
  return (
    <div className={cn('empty-state', className)} data-slot="empty-state">
      {message}
    </div>
  )
}

/** A failure always offers the retry: every error this renders came from a read that can be
 *  re-issued, and a dead end with no way out reads as a broken screen rather than a message. */
export function ErrorState({
  message,
  onRetry,
  retryLabel = '重新加载',
  className,
}: {
  message: string
  onRetry: () => void
  retryLabel?: string
  className?: string
}) {
  return (
    <div className={cn('empty-state', className)} data-slot="error-state">
      <p>{message}</p>
      <Button variant="secondary" onClick={onRetry}>
        <RefreshCw aria-hidden="true" />
        {retryLabel}
      </Button>
    </div>
  )
}
