import type { ComponentPropsWithRef, ReactNode } from 'react'
import { ScrollRegion } from '@/shared/ui'

/**
 * The interview transcript's scrolling column.
 *
 * The report view and the message thread are the same scroll container with different end
 * padding and alignment. The scrolling contract — the stable gutter and the tab stop that makes
 * a plain transcript reachable below the fold — comes from `ScrollRegion`; this owner states the
 * shared column geometry and which of the two views it is. Class names are written out in full
 * rather than composed, because Tailwind reads class names from source text and a name that only
 * exists as a fragment emits no CSS.
 */
export function TranscriptScroll({
  view = 'thread',
  children,
  ...rest
}: {
  view?: 'thread' | 'report'
  children: ReactNode
} & Omit<ComponentPropsWithRef<'div'>, 'className' | 'children' | 'tabIndex'>) {
  return (
    <ScrollRegion
      gutter="gutter-stable"
      className={
        view === 'report'
          ? 'flex min-h-0 flex-1 px-(--spacing-2xl) items-start justify-center py-(--layout-workspace-report-block-padding)'
          : 'flex min-h-0 flex-1 px-(--spacing-2xl) flex-col gap-(--spacing-lg) pt-(--spacing-lg) pb-(--layout-composer-reserve-block-size)'
      }
      {...rest}
    >
      {children}
    </ScrollRegion>
  )
}
