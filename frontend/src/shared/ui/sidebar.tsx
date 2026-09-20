import { ChevronLeft, ChevronRight } from 'lucide-react'
import { NavLink } from 'react-router'
import { cn } from '@/shared/lib/cn'
import { IconTooltip } from '@/shared/ui/overlay'
import type { ReactNode } from 'react'

/**
 * One entry of the application rail. While the rail is collapsed the label is
 * dropped and the accessible name moves to a tooltip, so the row keeps its icon
 * hit area and its name at the same time.
 */
export function SidebarAction({
  label,
  icon,
  collapsed = false,
  to,
  onClick,
  tone = 'nav',
}: {
  label: string
  icon: ReactNode
  collapsed?: boolean
  to?: string
  onClick?: () => void
  tone?: 'nav' | 'primary'
}) {
  const classes = cn(
    'sidebar-action',
    tone === 'primary'
      ? 'sidebar-action-primary ui-action ui-action-primary'
      : 'ui-action ui-action-nav',
  )
  const content = (
    <>
      {icon}
      <span data-sidebar-label>{label}</span>
    </>
  )
  const body = to ? (
    <NavLink
      className={({ isActive }) => cn(classes, isActive && 'is-active')}
      to={to}
      aria-label={label}
    >
      {content}
    </NavLink>
  ) : (
    <button type="button" className={classes} aria-label={label} onClick={onClick}>
      {content}
    </button>
  )
  return collapsed ? <IconTooltip label={label}>{body}</IconTooltip> : body
}

/** The collapse control: both chevrons are painted and CSS cross-fades between them,
 *  so the icon never changes size while the rail animates. */
export function SidebarToggle({
  collapsed,
  onToggle,
}: {
  collapsed: boolean
  onToggle: () => void
}) {
  const label = collapsed ? '展开侧栏' : '收起侧栏'
  return (
    <IconTooltip label={label}>
      <button
        type="button"
        className="sidebar-toggle ui-action ui-action-icon"
        aria-label={label}
        data-collapsed={collapsed ? 'true' : undefined}
        onClick={onToggle}
      >
        <span data-toggle-icon-stack aria-hidden="true">
          <ChevronLeft data-toggle-icon="collapse" />
          <ChevronRight data-toggle-icon="expand" />
        </span>
      </button>
    </IconTooltip>
  )
}

/** The application rail: one `sidebar-frame` column holding the brand and the
 *  collapse control, the primary action above a divider, the scrolling middle,
 *  then the footer. The product wraps it in the `app-sidebar` shell to pin it to
 *  the viewport; anywhere else it is already the whole rail. */
export function SidebarFrame({
  collapsed,
  onToggle,
  brand,
  primary,
  footer,
  children,
}: {
  collapsed: boolean
  onToggle: () => void
  brand: ReactNode
  primary: ReactNode
  footer: ReactNode
  children: ReactNode
}) {
  return (
    <div className={cn('sidebar-frame sidebar-rail', collapsed && 'is-collapsed')}>
      <header className="flex h-(--layout-sidebar-header-block-size) shrink-0 items-center justify-between p-sm">
        <div
          className="flex items-center gap-sm overflow-hidden whitespace-nowrap"
          data-sidebar-brand
        >
          {brand}
        </div>
        <SidebarToggle collapsed={collapsed} onToggle={onToggle} />
      </header>
      <div className="flex min-h-0 flex-1 flex-col overflow-hidden p-sm">
        <div className="border-b border-border pb-md">{primary}</div>
        {children}
      </div>
      <footer className="px-sm pb-sm">{footer}</footer>
    </div>
  )
}

/** One of the two cross-fading rail panes; only the pane matching the rail width is
 *  visible, and the hidden one is removed from the accessibility tree. `sessions`
 *  scrolls the session list, `rail` pins the collapsed icon strip to the bottom. */
export function SidebarPane({
  kind,
  visible,
  children,
}: {
  kind: 'sessions' | 'rail'
  visible: boolean
  children: ReactNode
}) {
  return (
    <div
      className={cn(
        'sidebar-pane',
        kind === 'sessions' ? 'sidebar-sessions scrollable' : 'flex flex-col justify-end pb-sm',
        visible && 'is-visible',
      )}
      aria-hidden={!visible}
    >
      {children}
    </div>
  )
}
