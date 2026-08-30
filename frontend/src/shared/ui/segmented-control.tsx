import type { CSSProperties, ReactNode } from 'react'

export type SegmentedControlItem<Value extends string = string> = {
  value: Value
  label: ReactNode
}

export function SegmentedControl<Value extends string>({
  items,
  value,
  onValueChange,
  ariaLabel,
}: {
  items: readonly SegmentedControlItem<Value>[]
  value: Value
  onValueChange: (value: Value) => void
  ariaLabel: string
}) {
  const activeIndex = Math.max(
    0,
    items.findIndex((item) => item.value === value),
  )
  const style = {
    '--segmented-index': activeIndex,
    '--segmented-count': items.length,
  } as CSSProperties

  return (
    <div className="segmented-control" role="group" aria-label={ariaLabel} style={style}>
      {items.map((item) => (
        <button
          className={`segmented-control__item${item.value === value ? ' is-active' : ''}`}
          key={item.value}
          type="button"
          aria-pressed={item.value === value}
          onClick={() => onValueChange(item.value)}
        >
          {item.label}
        </button>
      ))}
    </div>
  )
}
