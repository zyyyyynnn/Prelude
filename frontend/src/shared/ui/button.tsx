import { Button as ButtonPrimitive } from '@base-ui/react/button'
import type { ButtonHTMLAttributes } from 'react'
import { cn } from '@/shared/lib/cn'

export function Button({
  className,
  variant = 'primary',
  size = 'default',
  loading = false,
  pressed,
  children,
  disabled,
  ...props
}: ButtonHTMLAttributes<HTMLButtonElement> & {
  variant?: 'primary' | 'secondary' | 'ghost' | 'danger'
  size?: 'compact' | 'default' | 'icon' | 'icon-compact' | 'action' | 'hold'
  loading?: boolean
  pressed?: boolean
}) {
  return (
    <ButtonPrimitive
      data-slot="button"
      className={cn(
        'prelude-button',
        `prelude-button--${variant}`,
        `prelude-button--${size}`,
        'ui-action',
        className,
      )}
      disabled={disabled || loading}
      aria-busy={loading || undefined}
      data-loading={loading || undefined}
      data-pressed={pressed || undefined}
      aria-pressed={pressed}
      {...props}
    >
      {loading && <span className="prelude-button__spinner" aria-hidden="true" />}
      <span className="prelude-button__content">{children}</span>
    </ButtonPrimitive>
  )
}
