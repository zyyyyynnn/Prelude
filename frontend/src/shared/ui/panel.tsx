import type { HTMLAttributes, ReactNode } from 'react'
import { cn } from '@/shared/lib/cn'

/**
 * A titled surface: the heading row owns its actions, so a top-right button is
 * flex-centred against the title instead of floating over the surface.
 *
 * `fill` is a full-height surface that scrolls its own body; `card` is a
 * self-contained block that grows with its content. `level` keeps the heading
 * outline honest, and the title role follows it: h2 reads as `type-title`,
 * h3 as `type-subtitle`.
 */
export function Panel({
  title,
  eyebrow,
  description,
  actions,
  footer,
  layout = 'fill',
  level = 2,
  className,
  bodyClassName,
  children,
  ...rest
}: Omit<HTMLAttributes<HTMLElement>, 'title'> & {
  title: ReactNode
  eyebrow?: ReactNode
  description?: ReactNode
  actions?: ReactNode
  footer?: ReactNode
  layout?: 'fill' | 'card'
  level?: 2 | 3
  className?: string
  bodyClassName?: string
  children: ReactNode
}) {
  const fill = layout === 'fill'
  const Heading = level === 3 ? 'h3' : 'h2'
  return (
    <section
      className={cn(
        'flex flex-col',
        fill
          ? 'min-h-0 flex-1'
          : 'max-w-(--layout-workspace-content-max-inline-size) gap-lg rounded-lg border border-border bg-surface p-lg elevated-whisper',
        className,
      )}
      data-slot="panel"
      {...rest}
    >
      <header
        className={cn(
          'flex shrink-0 items-center justify-between gap-lg',
          fill && 'border-b border-border bg-surface px-lg py-md',
        )}
        data-slot="panel-header"
      >
        <div className="grid min-w-0 gap-xs" data-slot="panel-heading">
          {eyebrow && <p className="type-eyebrow">{eyebrow}</p>}
          <Heading className={level === 3 ? 'type-subtitle' : 'type-title'}>{title}</Heading>
          {description && <p className="type-meta">{description}</p>}
        </div>
        {actions && (
          <div className="flex shrink-0 items-center gap-sm" data-slot="panel-actions">
            {actions}
          </div>
        )}
      </header>
      <div
        className={cn(
          'flex min-w-0 flex-col gap-md',
          fill && 'scrollable min-h-0 flex-1 overflow-y-auto p-lg text-sm leading-base',
          bodyClassName,
        )}
        data-slot="panel-body"
      >
        {children}
      </div>
      {footer && (
        <footer
          className={cn(
            'flex shrink-0 items-center justify-end gap-sm border-t border-border',
            fill && 'bg-surface px-lg py-md',
          )}
          data-slot="panel-footer"
        >
          {footer}
        </footer>
      )}
    </section>
  )
}

/** A band inside a panel that is its own subject: the hairline that opens it, the room the
 *  line needs on both sides, and the tight gap that keeps its heading with its controls.
 *  The gallery shows the same band the two settings sections render. */
export function SubSection({ title, children }: { title: string; children: ReactNode }) {
  return (
    <section className="grid gap-sm border-t border-border pt-md">
      <h3 className="type-subtitle">{title}</h3>
      {children}
    </section>
  )
}
