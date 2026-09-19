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
        kind === 'sessions'
          ? 'sidebar-sessions scrollable'
          : 'flex w-full flex-col justify-end pb-sm',
        visible && 'is-visible',
      )}
      aria-hidden={!visible}
    >
      {children}
    </div>
  )
}
