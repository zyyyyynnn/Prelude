import type { VariantProps } from 'class-variance-authority'
import { cva } from 'class-variance-authority'

export { default as Button } from './Button.vue'

export const buttonVariants = cva(
  'ui-action inline-flex items-center justify-center gap-2 whitespace-nowrap rounded-md border border-transparent text-sm font-medium font-serif transition-colors [transition-duration:var(--motion-duration-base)] [transition-timing-function:var(--motion-ease-standard)] disabled:pointer-events-none disabled:opacity-50 [&_svg]:pointer-events-none [&_svg]:size-4 [&_svg]:shrink-0',
  {
    variants: {
      variant: {
        default: 'ui-action-primary bg-primary !text-[var(--color-surface)] hover:bg-primary/90',
        destructive:
          'ui-action-destructive bg-destructive text-destructive-foreground hover:bg-destructive/90',
        outline:
          'ui-action-outline border-input bg-background hover:bg-accent hover:text-accent-foreground',
        secondary:
          'ui-action-secondary bg-secondary text-secondary-foreground hover:bg-secondary/80',
        ghost: 'ui-action-ghost hover:bg-accent hover:text-accent-foreground',
        link: 'ui-action-link text-primary underline-offset-4 hover:underline',
      },
      size: {
        default: 'h-[var(--ui-height-base)] px-4 py-1.5',
        sm: 'h-[var(--ui-height-base)] rounded-md px-3',
        compact: 'h-[var(--ui-height-compact)] rounded-md px-2',
        icon: 'size-[var(--ui-height-base)]',
        'icon-sm': 'size-[var(--ui-height-base)]',
        'icon-compact': 'size-[var(--ui-height-compact)]',
      },
    },
    defaultVariants: {
      variant: 'default',
      size: 'default',
    },
  },
)

export type ButtonVariants = VariantProps<typeof buttonVariants>
