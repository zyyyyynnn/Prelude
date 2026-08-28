import type { ButtonHTMLAttributes } from 'react'
import { classNames } from '@/shared/lib/class-names'

export function Button({
  className,
  variant = 'primary',
  size = 'default',
  loading = false,
  children,
  disabled,
  ...props
}: ButtonHTMLAttributes<HTMLButtonElement> & {
  variant?: 'primary' | 'secondary' | 'ghost' | 'danger'
  size?: 'compact' | 'default' | 'icon'
  loading?: boolean
}) {
  return (
    <button
      className={classNames(
        'prelude-button',
        `prelude-button--${variant}`,
        `prelude-button--${size}`,
        'ui-action',
        className,
      )}
      disabled={disabled || loading}
      aria-busy={loading || undefined}
      {...props}
    >
      {loading && <span className="button-spinner" aria-hidden="true" />}
      {children}
    </button>
  )
}
