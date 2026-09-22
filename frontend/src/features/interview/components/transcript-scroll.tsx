import type { ComponentPropsWithRef, ReactNode } from 'react'
import { cn } from '@/shared/lib/cn'

/**
 * The interview transcript's scrolling column. The report view and the message thread are
 * the same scroll container with different contents and different end padding, so the
 * shared part — the stable gutter, the flex basis, the horizontal inset — lives here and
 * each view states only what makes it different.
 */
export function TranscriptScroll({
  className,
  children,
  ...rest
}: { className?: string; children: ReactNode } & Omit<
  ComponentPropsWithRef<'div'>,
  'className' | 'children'
>) {
  return (
    <div
      className={cn(
        'scrollable gutter-stable flex min-h-0 flex-1 overflow-y-auto px-2xl',
        className,
      )}
      {...rest}
    >
      {children}
    </div>
  )
}
