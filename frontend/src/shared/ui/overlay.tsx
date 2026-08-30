import { Dialog, Tooltip } from '@base-ui/react'
import { X } from 'lucide-react'
import type { ReactNode } from 'react'
import { classNames } from '@/shared/lib/class-names'

export function IconTooltip({ label, children }: { label: string; children: ReactNode }) {
  return (
    <Tooltip.Root>
      <Tooltip.Trigger render={children as React.ReactElement} />
      <Tooltip.Portal>
        <Tooltip.Positioner className="prelude-tooltip-positioner" sideOffset={8}>
          <Tooltip.Popup className="prelude-tooltip">{label}</Tooltip.Popup>
        </Tooltip.Positioner>
      </Tooltip.Portal>
    </Tooltip.Root>
  )
}

export function Modal({
  open,
  onOpenChange,
  title,
  className,
  showClose = true,
  children,
}: {
  open: boolean
  onOpenChange: (open: boolean) => void
  title: string
  className?: string
  showClose?: boolean
  children: ReactNode
}) {
  return (
    <Dialog.Root open={open} onOpenChange={onOpenChange}>
      <Dialog.Portal>
        <Dialog.Backdrop className="prelude-dialog__backdrop" />
        <Dialog.Viewport className="prelude-dialog__viewport">
          <Dialog.Popup className={classNames('prelude-dialog', className)}>
            <Dialog.Title className="sr-only">{title}</Dialog.Title>
            {showClose && (
              <Dialog.Close
                className="prelude-dialog__close ui-action ui-action-icon"
                aria-label="关闭"
              >
                <X size={18} />
              </Dialog.Close>
            )}
            {children}
          </Dialog.Popup>
        </Dialog.Viewport>
      </Dialog.Portal>
    </Dialog.Root>
  )
}
