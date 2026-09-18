import { Dialog as BaseDialog, Tooltip } from '@base-ui/react'
import { X } from 'lucide-react'
import type { ReactNode } from 'react'
import { cn } from '@/shared/lib/cn'

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

export function Dialog({
  open,
  onOpenChange,
  title,
  className,
  layout,
  showClose = true,
  children,
}: {
  open: boolean
  onOpenChange: (open: boolean) => void
  title: string
  className?: string
  layout?: 'workspace'
  showClose?: boolean
  children: ReactNode
}) {
  return (
    <BaseDialog.Root open={open} onOpenChange={onOpenChange}>
      <BaseDialog.Portal>
        <BaseDialog.Backdrop className="prelude-dialog__backdrop" />
        <BaseDialog.Viewport className="prelude-dialog__viewport">
          <BaseDialog.Popup
            className={cn(
              'prelude-dialog',
              layout === 'workspace' && 'prelude-dialog--workspace',
              className,
            )}
          >
            <BaseDialog.Title className="sr-only">{title}</BaseDialog.Title>
            {showClose && (
              <BaseDialog.Close
                className="prelude-dialog__close ui-action ui-action-icon"
                aria-label="关闭"
              >
                <X size={18} />
              </BaseDialog.Close>
            )}
            {children}
          </BaseDialog.Popup>
        </BaseDialog.Viewport>
      </BaseDialog.Portal>
    </BaseDialog.Root>
  )
}
