import type { HTMLAttributes } from 'react'
import { cn } from '@/shared/lib/cn'

/**
 * A card inset into another surface: the muted fill and the tight stack that keeps a
 * label with its value. ScoreTile and the weakness entries are the same shell around
 * different content, so the shell lives here once.
 */
export function InsetCard({ className, children, ...rest }: HTMLAttributes<HTMLElement>) {
  return (
    <article className={cn('grid gap-sm break-inside-avoid inset-card', className)} {...rest}>
      {children}
    </article>
  )
}
