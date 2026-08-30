import { Button as ButtonPrimitive } from '@base-ui/react/button'
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
    <ButtonPrimitive
      data-slot="button"
      className={classNames(
        'prelude-button',
        `prelude-button--${variant}`,
        `prelude-button--${size}`,
        'ui-action',
        className,
      )}
      disabled={disabled || loading}
      aria-busy={loading || undefined}
      data-loading={loading || undefined}
      {...props}
    >
      {loading && <span className="button-spinner" aria-hidden="true" />}
      <span className="prelude-button__content">{children}</span>
    </ButtonPrimitive>
  )
}
