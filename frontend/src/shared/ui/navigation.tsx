import type { ReactNode } from 'react'
import { cn } from '@/shared/lib/cn'

/** One entry of a left-hand navigation column. The current entry carries
 *  `aria-current`, and the destructive variant is a tone rather than a second component. */
export function NavItem({
  label,
  icon,
  active = false,
  tone = 'nav',
  onClick,
}: {
  label: string
  icon: ReactNode
  active?: boolean
  tone?: 'nav' | 'danger'
  onClick?: () => void
}) {
  return (
    <button
      type="button"
      className={cn(
        'nav-item',
        tone === 'danger'
          ? 'nav-item-danger ui-action ui-action-danger'
          : 'ui-action ui-action-nav',
        active && 'is-active',
      )}
      aria-current={active ? 'page' : undefined}
      onClick={onClick}
    >
      {icon}
      {label}
    </button>
  )
}
