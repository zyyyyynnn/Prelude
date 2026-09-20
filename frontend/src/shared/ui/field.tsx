import { Input as InputPrimitive } from '@base-ui/react/input'
import {
  Fragment,
  type InputHTMLAttributes,
  type ReactNode,
  type TextareaHTMLAttributes,
} from 'react'
import { cn } from '@/shared/lib/cn'
import { IconTooltip } from '@/shared/ui/overlay'

export function Input({ className, ...props }: InputHTMLAttributes<HTMLInputElement>) {
  return (
    <InputPrimitive
      data-slot="input"
      className={cn('prelude-input', 'ui-field-control', className)}
      {...props}
    />
  )
}
export function Textarea({ className, ...props }: TextareaHTMLAttributes<HTMLTextAreaElement>) {
  return (
    <textarea
      data-slot="textarea"
      className={cn('prelude-textarea', 'ui-field-control', className)}
      {...props}
    />
  )
}
export function Field({
  label,
  htmlFor,
  hint,
  children,
}: {
  label: string
  htmlFor: string
  hint?: string
  children: ReactNode
}) {
  return (
    <div className="grid gap-sm" data-slot="field">
      <label className="type-label" data-slot="field-label" htmlFor={htmlFor}>
        {label}
      </label>
      {children}
      {hint && (
        <span className="type-meta" data-slot="field-description">
          {hint}
        </span>
      )}
    </div>
  )
}

/** A control with trailing actions inside its own box. The gutter the control
 *  reserves is counted from the actions it is given, so the two cannot disagree. */
export function FieldActions({ actions, children }: { actions: ReactNode[]; children: ReactNode }) {
  return (
    <div className={`field-actions-${actions.length}`} data-slot="field-actions">
      {children}
      <div className="absolute inset-y-0 inset-e-(--ui-control-inset) flex items-center">
        {actions.map((action, index) => (
          <Fragment key={index}>{action}</Fragment>
        ))}
      </div>
    </div>
  )
}

/** An icon action that sits inside a field's control box. One label drives both the
 *  tooltip and the accessible name, so they can never drift apart. */
export function FieldAction({
  label,
  icon,
  onClick,
}: {
  label: string
  icon: ReactNode
  onClick: () => void
}) {
  return (
    <IconTooltip label={label}>
      <button
        type="button"
        className="field-action ui-action ui-action-icon"
        aria-label={label}
        onClick={onClick}
      >
        {icon}
      </button>
    </IconTooltip>
  )
}
