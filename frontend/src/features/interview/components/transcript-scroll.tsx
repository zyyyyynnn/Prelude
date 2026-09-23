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
      /* A transcript of plain messages holds nothing focusable, so without this stop the part
         below the fold is unreachable by keyboard — the rule `ScrollRegion` exists for, applied
         here because each view adds its own padding to the same column. */
      tabIndex={0}
      {...rest}
    >
      {children}
    </div>
  )
}
