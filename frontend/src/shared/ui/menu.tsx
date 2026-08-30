import { Menu } from '@base-ui/react/menu'
import { Check } from 'lucide-react'
import type { ReactElement, ReactNode } from 'react'
import { classNames } from '@/shared/lib/class-names'

export function DropdownMenu({
  trigger,
  children,
  align = 'start',
  side = 'bottom',
  className,
}: {
  trigger: ReactElement
  children: ReactNode
  align?: 'start' | 'center' | 'end'
  side?: 'top' | 'bottom' | 'left' | 'right' | 'inline-start' | 'inline-end'
  className?: string
}) {
  return (
    <Menu.Root>
      <Menu.Trigger render={trigger} />
      <Menu.Portal>
        <Menu.Positioner
          className="prelude-menu-positioner"
          side={side}
          sideOffset={6}
          align={align}
        >
          <Menu.Popup className={classNames('prelude-menu', className)}>{children}</Menu.Popup>
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
        <Menu.Positioner className="prelude-menu-positioner" sideOffset={4} align="start">
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

export function DropdownMenuRadioItem({
  value,
  children,
}: {
  value: string
  children: ReactNode
}) {
  return (
    <Menu.RadioItem className="prelude-menu__item" value={value} closeOnClick>
      {children}
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
  className,
  disabled,
  onClick,
}: {
  children: ReactNode
  className?: string
  disabled?: boolean
  onClick?: () => void
}) {
  return (
    <Menu.Item
      className={classNames('prelude-menu__item', className)}
      disabled={disabled}
      onClick={onClick}
    >
      {children}
    </Menu.Item>
  )
}

export function DropdownMenuSeparator() {
  return <Menu.Separator className="prelude-menu__separator" />
}
