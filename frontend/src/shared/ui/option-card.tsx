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

/** The two-tone preview of a theme option: the dark half sits where that theme's surface is. */
export type ThemeTone = 'light' | 'dark' | 'system'

export function ThemePreview({ tone }: { tone: ThemeTone }) {
  return (
    <span className="grid h-(--ui-height-control) grid-cols-2 gap-xs">
      <span
        className={cn('rounded-sm', tone === 'dark' ? 'bg-text-secondary' : 'bg-surface-muted')}
      />
      <span
        className={cn('rounded-sm', tone === 'light' ? 'bg-surface-muted' : 'bg-text-secondary')}
      />
    </span>
  )
}

/** The theme preference set as the settings panel renders it: one row of three, preview above
 *  the words. Option text comes from the caller — the product passes its own labels and the
 *  gallery passes role names — but the geometry and the radio semantics have one owner. */
export function ThemeChoiceGroup({
  value,
  options,
  onSelect,
}: {
  value: ThemeTone
  options: ReadonlyArray<{ value: ThemeTone; label: string; description: string }>
  onSelect: (value: ThemeTone) => void
}) {
  return (
    <div className="grid w-full grid-cols-3 gap-sm" role="radiogroup" aria-label="主题偏好">
      {options.map((option) => (
        <OptionCard
          key={option.value}
          checked={value === option.value}
          label={option.label}
          description={option.description}
          onSelect={() => onSelect(option.value)}
        >
          <ThemePreview tone={option.value} />
        </OptionCard>
      ))}
    </div>
  )
}
