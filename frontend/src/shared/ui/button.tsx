import { Button as ButtonPrimitive } from '@base-ui/react/button'
import type { ComponentProps, ReactNode } from 'react'
import { cn } from '@/shared/lib/cn'

export function Button({
  className,
  variant = 'primary',
  size = 'default',
  shape,
  loading = false,
  pressed,
  children,
  held,
  disabled,
  ...props
}: ComponentProps<typeof ButtonPrimitive> & {
  variant?: 'primary' | 'secondary' | 'ghost' | 'danger'
  /** One control height; "icon" only makes the box square. */
  size?: 'default' | 'icon'
  /** Content-driven shape that keeps the default height: a fixed action, or press-and-hold. */
  shape?: 'action' | 'hold'
  loading?: boolean
  pressed?: boolean
  /** Press-and-hold only: what shows in place of the words while the control is held. */
  held?: ReactNode
}) {
  return (
    <ButtonPrimitive
      data-slot="button"
      className={cn(
        'prelude-button',
        `prelude-button--${variant}`,
        `prelude-button--${size}`,
        shape && `prelude-button--${shape}`,
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
      <span className="prelude-button__content">
        {shape === 'hold' ? (
          <>
            {/* The words keep their box while held so the control never changes width under
                the finger; `held` is what replaces them. */}
            <span className="prelude-button__label">{children}</span>
            {held}
          </>
        ) : (
          children
        )}
      </span>
    </ButtonPrimitive>
  )
}
