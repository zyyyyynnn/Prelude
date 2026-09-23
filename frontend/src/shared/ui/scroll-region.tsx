import type { ComponentPropsWithRef, ReactNode } from 'react'
import { cn } from '@/shared/lib/cn'

/**
 * A region that scrolls on its own.
 *
 * The tab stop is part of what makes this container rather than something each call site
 * remembers: a scrolling region whose contents are all non-interactive — a report, a page of
 * prose, an empty workspace — has no other way for a keyboard user to reach what is below the
 * fold, and axe reports it as `scrollable-region-focusable`. `focusable={false}` is for a region
 * that is hidden from assistive tech at the same time: a focus stop inside `aria-hidden` is the
 * opposite defect.
 *
 * This component owns the scroll chrome and the tab stop only. The box — flex basis, inset,
 * stack gap, page-shell geometry — belongs to the caller (`shell` names a page shell class that
 * must appear as a literal so Tailwind emits it). `gutter` stays with the caller because both
 * answers are right: a shell that already reserves a scrollbar slot must not have it reserved
 * twice, and a column that grows into one needs it reserved so nothing beside it shifts.
 */
export function ScrollRegion({
  gutter,
  shell,
  className,
  children,
  focusable = true,
  ...rest
}: {
  gutter?: 'gutter-stable'
  shell?: 'workspace-page__content'
  className?: string
  children: ReactNode
  focusable?: boolean
} & Omit<ComponentPropsWithRef<'div'>, 'className' | 'children' | 'tabIndex'>) {
  return (
    <div
      className={cn('scrollable overflow-y-auto', gutter, shell, className)}
      tabIndex={focusable ? 0 : undefined}
      {...rest}
    >
      {children}
    </div>
  )
}
