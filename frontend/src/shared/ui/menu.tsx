import { Menu } from '@base-ui/react/menu'
import { Check, ChevronRight } from 'lucide-react'
import type { ReactElement, ReactNode } from 'react'
import { OVERLAY_OFFSET } from './positioning'
import { cn } from '@/shared/lib/cn'

export function DropdownMenu({
  trigger,
  children,
  align = 'start',
  side = 'bottom',
  layout,
  className,
}: {
  trigger: ReactElement
  children: ReactNode
  align?: 'start' | 'center' | 'end'
  side?: 'top' | 'bottom' | 'left' | 'right' | 'inline-start' | 'inline-end'
  layout?: 'structured' | 'model'
  className?: string
}) {
  return (
    <Menu.Root>
      <Menu.Trigger render={trigger} />
      <Menu.Portal>
        <Menu.Positioner
          className="prelude-menu-positioner"
          side={side}
          sideOffset={OVERLAY_OFFSET.menu}
          align={align}
        >
          <Menu.Popup
            className={cn(
              'prelude-menu',
              layout && 'prelude-menu--structured',
              layout === 'model' && 'prelude-menu--model',
              className,
            )}
          >
            {children}
          </Menu.Popup>
        </Menu.Positioner>
      </Menu.Portal>
    </Menu.Root>
  )
}

export function DropdownMenuGroup({ children }: { children: ReactNode }) {
  return <Menu.Group>{children}</Menu.Group>
}

export function DropdownMenuSubmenu({
  trigger,
  children,
  disabled,
}: {
  trigger: ReactNode
  children: ReactNode
  disabled?: boolean
}) {
  return (
    <Menu.SubmenuRoot>
      <Menu.SubmenuTrigger className="prelude-menu__item" disabled={disabled}>
        {trigger}
      </Menu.SubmenuTrigger>
      <Menu.Portal>
        <Menu.Positioner
          className="prelude-menu-positioner"
          sideOffset={OVERLAY_OFFSET.submenu}
          align="start"
        >
          <Menu.Popup className="prelude-menu">{children}</Menu.Popup>
        </Menu.Positioner>
      </Menu.Portal>
    </Menu.SubmenuRoot>
  )
}

export function DropdownMenuRadioGroup({
  value,
  onValueChange,
  children,
}: {
  value: string
  onValueChange: (value: string) => void
  children: ReactNode
}) {
  return (
    <Menu.RadioGroup value={value} onValueChange={onValueChange}>
      {children}
    </Menu.RadioGroup>
  )
}

export function DropdownMenuRadioItem({ value, children }: { value: string; children: ReactNode }) {
  return (
    <Menu.RadioItem className="prelude-menu__item" value={value} closeOnClick>
      <span className="prelude-menu__item-label">{children}</span>
      <Menu.RadioItemIndicator
        className="prelude-menu__indicator prelude-menu__indicator--end"
        aria-hidden="true"
      >
        <Check />
      </Menu.RadioItemIndicator>
    </Menu.RadioItem>
  )
}

export function DropdownMenuCheckboxItem({
  checked,
  children,
  onCheckedChange,
}: {
  checked: boolean
  children: ReactNode
  onCheckedChange: (checked: boolean) => void
}) {
  return (
    <Menu.CheckboxItem
      className="prelude-menu__item"
      checked={checked}
      closeOnClick
      onCheckedChange={onCheckedChange}
    >
      {children}
      <Menu.CheckboxItemIndicator
        className="prelude-menu__indicator prelude-menu__indicator--end"
        aria-hidden="true"
      >
        <Check />
      </Menu.CheckboxItemIndicator>
    </Menu.CheckboxItem>
  )
}

export function DropdownMenuItem({
  children,
  icon,
  className,
  disabled,
  onClick,
}: {
  children: ReactNode
  icon?: ReactNode
  className?: string
  disabled?: boolean
  onClick?: () => void
}) {
  return (
    <Menu.Item
      className={cn('prelude-menu__item', icon && 'prelude-menu__item--leading-icon', className)}
      disabled={disabled}
      onClick={onClick}
    >
      {icon ? (
        <>
          <span className="prelude-menu__icon--leading" aria-hidden="true">
            {icon}
          </span>
          <span className="prelude-menu__item-label">{children}</span>
        </>
      ) : (
        children
      )}
    </Menu.Item>
  )
}

export function DropdownMenuSeparator() {
  return <Menu.Separator className="prelude-menu__separator" />
}

/** The content of a menu row or submenu trigger: an optional leading glyph, the label, an
 *  optional current value beside it, and the chevron when it opens a submenu. Call sites had
 *  been composing these four pieces from the internal class names, which is how the gallery's
 *  menus and the product's drifted apart. */
export function MenuLabel({
  label,
  icon,
  detail,
  submenu,
}: {
  label: string
  icon?: ReactNode
  detail?: string
  submenu?: boolean
}) {
  return (
    <>
      {icon && (
        <span className="prelude-menu__icon" aria-hidden="true">
          {icon}
        </span>
      )}
      <span className="prelude-menu__label">{label}</span>
      {detail && <span className="prelude-menu__detail">{detail}</span>}
      {submenu && <ChevronRight className="prelude-menu__chevron" aria-hidden="true" />}
    </>
  )
}
