import { Select as BaseSelect } from '@base-ui/react/select'
import { Check, ChevronDown } from 'lucide-react'
import { classNames } from '@/shared/lib/class-names'

export type SelectOption = {
  value: string
  label: string
  disabled?: boolean
}

export function Select({
  id,
  value,
  options,
  disabled,
  name,
  className,
  ariaLabel,
  onValueChange,
}: {
  id?: string
  value: string
  options: SelectOption[]
  disabled?: boolean
  name?: string
  className?: string
  ariaLabel?: string
  onValueChange: (value: string) => void
}) {
  return (
    <BaseSelect.Root
      items={options}
      value={value}
      disabled={disabled}
      name={name}
      onValueChange={(nextValue) => {
        if (nextValue !== null) onValueChange(nextValue)
      }}
    >
      <BaseSelect.Trigger
        id={id}
        aria-label={ariaLabel}
        className={classNames('prelude-select', 'ui-field-control', className)}
      >
        <BaseSelect.Value />
        <BaseSelect.Icon className="prelude-select__icon">
          <ChevronDown aria-hidden="true" />
        </BaseSelect.Icon>
      </BaseSelect.Trigger>
      <BaseSelect.Portal>
        <BaseSelect.Positioner
          className="prelude-menu-positioner"
          sideOffset={4}
          alignItemWithTrigger={false}
        >
          <BaseSelect.Popup className="prelude-menu prelude-select-popup">
            <BaseSelect.List className="prelude-select__list">
              {options.map((option) => (
                <BaseSelect.Item
                  key={option.value}
                  value={option.value}
                  disabled={option.disabled}
                  className="prelude-menu__item prelude-select__item"
                >
                  <BaseSelect.ItemIndicator
                    className="prelude-menu__indicator"
                    aria-hidden="true"
                  >
                    <Check />
                  </BaseSelect.ItemIndicator>
                  <BaseSelect.ItemText className="prelude-menu__item-label">
                    {option.label}
                  </BaseSelect.ItemText>
                </BaseSelect.Item>
              ))}
            </BaseSelect.List>
          </BaseSelect.Popup>
        </BaseSelect.Positioner>
      </BaseSelect.Portal>
    </BaseSelect.Root>
  )
}
