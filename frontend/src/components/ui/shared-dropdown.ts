import { cva } from "class-variance-authority"

export const dropdownContentClasses =
  'relative z-[105] overflow-hidden rounded-md border border-transparent bg-surface text-popover-foreground p-0.5 shadow-whisper duration-300 ease-in-out data-[state=open]:animate-in data-[state=closed]:animate-out data-[state=closed]:fade-out-0 data-[state=open]:fade-in-0 data-[side=bottom]:slide-in-from-top-2 data-[side=left]:slide-in-from-right-2 data-[side=right]:slide-in-from-left-2 data-[side=top]:slide-in-from-bottom-2'

export const dropdownItemVariants = cva(
  'relative flex w-full cursor-default select-none items-center rounded-md outline-none focus:bg-accent focus:text-accent-foreground data-[highlighted]:bg-accent data-[highlighted]:text-accent-foreground data-[disabled]:pointer-events-none data-[disabled]:opacity-50',
  {
    variants: {
      size: {
        default: 'h-[34px] pl-8 pr-2 text-sm',
        compact: 'h-[30px] px-2 text-[13px]',
      },
    },
    defaultVariants: {
      size: 'default',
    },
  }
)

export const dropdownTriggerVariants = cva(
  'flex w-full items-center justify-between rounded-md border !border-input bg-surface px-3 ring-offset-background data-[placeholder]:text-muted-foreground !outline-none focus-within:!outline-none disabled:cursor-not-allowed disabled:opacity-50 [&>span]:truncate text-start',
  {
    variants: {
      size: {
        default: 'h-[34px] py-1.5 text-sm',
        compact: 'h-[30px] py-1 text-[13px]',
      },
    },
    defaultVariants: {
      size: 'default',
    },
  }
)
