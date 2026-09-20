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

/** The settings column: the section list with the one destructive action pinned to its
 *  bottom. Section titles come from the caller — the product passes its own and the gallery
 *  passes role names — but the width, the hairline and the `mt-auto` have one owner, so the
 *  gallery cannot drift away from the panel it is supposed to be showing. */
export function SettingsNavigation<TSection extends string>({
  items,
  active,
  onSelect,
  danger,
}: {
  items: ReadonlyArray<{ key: TSection; label: string; icon: ReactNode }>
  active: TSection
  onSelect: (key: TSection) => void
  danger: { label: string; icon: ReactNode; onSelect: () => void }
}) {
  return (
    <aside
      className="flex w-(--layout-settings-sidebar-inline-size) flex-col border-e border-e-border py-md"
      data-slot="settings-sidebar"
    >
      <nav className="flex flex-1 flex-col gap-sm px-sm" aria-label="设置分类">
        {items.map(({ key, label, icon }) => (
          <NavItem
            key={key}
            active={key === active}
            icon={icon}
            label={label}
            onClick={() => onSelect(key)}
          />
        ))}
      </nav>
      <div className="mt-auto px-sm">
        <NavItem label={danger.label} tone="danger" icon={danger.icon} onClick={danger.onSelect} />
      </div>
    </aside>
  )
}
