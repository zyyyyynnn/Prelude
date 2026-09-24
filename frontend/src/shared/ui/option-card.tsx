import type { ReactNode } from 'react'
import { cn } from '@/shared/lib/cn'

/** A selectable card in a small mutually exclusive set: the option's own preview, a
 *  title and a note. The `radiogroup` wrapper belongs to the list that owns the options. */
export function OptionCard({
  checked,
  onSelect,
  label,
  description,
  children,
}: {
  checked: boolean
  onSelect: () => void
  label: string
  description?: string
  children?: ReactNode
}) {
  return (
    <button
      type="button"
      role="radio"
      aria-checked={checked}
      className={cn('option-card ui-action ui-action-selectable', checked && 'is-active')}
      onClick={onSelect}
    >
      {children}
      <span className="grid gap-xs">
        <span className="font-serif text-sm font-semibold">{label}</span>
        {description && <span className="type-meta">{description}</span>}
      </span>
    </button>
  )
}
