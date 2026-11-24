import { Dialog as BaseDialog, Tooltip } from '@base-ui/react'
import { OVERLAY_OFFSET } from './positioning'
import type { ReactNode } from 'react'
import { cn } from '@/shared/lib/cn'
import { RiCloseLine } from '@remixicon/react'
import { Icon } from './Icon'

export function IconTooltip({ label, children }: { label: string; children: ReactNode }) {
  return (
    <Tooltip.Root>
      <Tooltip.Trigger render={children as React.ReactElement} />
      <Tooltip.Portal>
        <Tooltip.Positioner className="ui-tooltip-positioner" sideOffset={OVERLAY_OFFSET.tooltip}>
          <Tooltip.Popup className="ui-tooltip">{label}</Tooltip.Popup>
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
  const workspace = layout === 'workspace'
  return (
    <BaseDialog.Root open={open} onOpenChange={onOpenChange}>
      <BaseDialog.Portal>
        <BaseDialog.Backdrop className="ui-dialog__backdrop" />
        <BaseDialog.Viewport className="ui-dialog__viewport">
          <BaseDialog.Popup
            className={cn('ui-dialog', workspace && 'ui-dialog--workspace', className)}
          >
            <BaseDialog.Title className="sr-only">{title}</BaseDialog.Title>
            {/* A workspace shell is full-bleed, so its own header owns the dismiss
                affordance; a floating close button here would overlap caller content. */}
            {showClose && !workspace && (
              <BaseDialog.Close
                className="ui-dialog__close ui-action ui-action-icon"
                aria-label="关闭"
              >
                <Icon as={RiCloseLine} />
              </BaseDialog.Close>
            )}
            {children}
          </BaseDialog.Popup>
        </BaseDialog.Viewport>
      </BaseDialog.Portal>
    </BaseDialog.Root>
  )
}
