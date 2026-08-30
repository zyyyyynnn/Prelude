import { Input as InputPrimitive } from '@base-ui/react/input'
import type {
  InputHTMLAttributes,
  ReactNode,
  TextareaHTMLAttributes,
} from 'react'
import { classNames } from '@/shared/lib/class-names'

export function Input({ className, ...props }: InputHTMLAttributes<HTMLInputElement>) {
  return (
    <InputPrimitive
      data-slot="input"
      className={classNames('prelude-input', 'ui-field-control', className)}
      {...props}
    />
  )
}
export function Textarea({ className, ...props }: TextareaHTMLAttributes<HTMLTextAreaElement>) {
  return (
    <textarea
      data-slot="textarea"
      className={classNames('prelude-textarea', 'ui-field-control', className)}
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
    <div className="field" data-slot="field">
      <label className="field__label" data-slot="field-label" htmlFor={htmlFor}>
        {label}
      </label>
      {children}
      {hint && (
        <span className="field__hint" data-slot="field-description">
          {hint}
        </span>
      )}
    </div>
  )
}
