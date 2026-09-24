import type { HTMLAttributes } from 'react'
import { cn } from '@/shared/lib/cn'

/** The card's internal stack. `sm` is a label over a value; `lg` separates a panel's sections. */
const CARD_STACK = {
  sm: 'gap-sm',
  lg: 'gap-lg',
} as const

/**
 * The elevated card: the one surface that sits on the workspace background. Panel renders
 * `layout="card"` through it, and a block that needs the same elevation without a heading
 * row renders through it directly — so the recipe has one owner instead of one per caller.
 */
export function Card({
  stack = 'sm',
  className,
  children,
  ...rest
}: { stack?: keyof typeof CARD_STACK } & HTMLAttributes<HTMLElement>) {
  return (
    <section
      className={cn(
        'grid rounded-lg border border-border bg-surface p-lg elevated-whisper',
        CARD_STACK[stack],
        className,
      )}
      {...rest}
    >
      {children}
    </section>
  )
}
