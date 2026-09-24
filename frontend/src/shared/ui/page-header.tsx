import type { ReactNode } from 'react'
import { IconTooltip } from './overlay'

/** The band above a workspace page: a title that truncates with the room it is given and
 *  carries its full text as a tooltip, and an optional cluster of actions pinned to the end.
 *  Two pages had been copying this nesting by hand, so only the interview header kept the
 *  tooltip and the others silently lost it. */
export function PageHeader({ title, actions }: { title: string; actions?: ReactNode }) {
  return (
    <header className="workspace-header" data-slot="page-header">
      <div className="workspace-header__main">
        <div className="workspace-header__title-area">
          <IconTooltip label={title}>
            <h1 className="workspace-header__title max-w-full" aria-label={title}>
              {title}
            </h1>
          </IconTooltip>
        </div>
        {actions ? (
          <div className="flex shrink-0 items-center gap-lg" data-slot="page-header-actions">
            {actions}
          </div>
        ) : null}
      </div>
    </header>
  )
}
