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
 */
export function ScrollRegion({
  className,
  children,
  focusable = true,
  ...rest
}: { className?: string; children: ReactNode; focusable?: boolean } & Omit<
  ComponentPropsWithRef<'div'>,
  'className' | 'children' | 'tabIndex'
>) {
  return (
    <div
      className={cn('scrollable overflow-y-auto', className)}
      tabIndex={focusable ? 0 : undefined}
      {...rest}
    >
      {children}
    </div>
  )
}
