import { Dialog } from '@base-ui/react'
import { useCallback, useMemo, useState, type ReactNode } from 'react'
import { CheckCircle2, Info, Loader2, OctagonX, TriangleAlert, X } from 'lucide-react'
import { Toaster, toast } from 'sonner'
import { Button } from './button'
import { FeedbackContext, type ConfirmOptions, type NoticeTone } from './feedback-context'

export { useFeedback } from './feedback-context'
export type { ConfirmOptions, FeedbackApi, NoticeTone } from './feedback-context'

const NOTICE_DURATION = 2000

export function FeedbackProvider({ children }: { children: ReactNode }) {
  const [confirmation, setConfirmation] = useState<{
    options: ConfirmOptions
    resolve: (value: boolean) => void
  } | null>(null)

  const notify = useCallback((message: string, tone: NoticeTone = 'info') => {
    toast[tone](message, { duration: NOTICE_DURATION })
  }, [])

  const confirm = useCallback((options: ConfirmOptions) => {
    return new Promise<boolean>((resolve) => setConfirmation({ options, resolve }))
  }, [])

  const api = useMemo(() => ({ notify, confirm }), [confirm, notify])
  const settle = (accepted: boolean) => {
    confirmation?.resolve(accepted)
    setConfirmation(null)
  }

  return (
    <FeedbackContext.Provider value={api}>
      {children}
      <Toaster
        position="top-center"
        theme="light"
        closeButton
        className="prelude-toaster"
        icons={{
          success: <CheckCircle2 size={16} />,
          info: <Info size={16} />,
          warning: <TriangleAlert size={16} />,
          error: <OctagonX size={16} />,
          loading: <Loader2 className="prelude-toast__loader" size={16} />,
          close: <X />,
        }}
        toastOptions={{
          closeButtonAriaLabel: '关闭系统提示',
          classNames: {
            toast: 'prelude-toast',
            description: 'prelude-toast__description',
            actionButton: 'prelude-toast__action',
            cancelButton: 'prelude-toast__cancel',
            closeButton: 'prelude-toast__close ui-action ui-action-icon',
          },
        }}
      />
      <Dialog.Root
        open={Boolean(confirmation)}
        onOpenChange={(open) => {
          if (!open) settle(false)
        }}
      >
        <Dialog.Portal>
          <Dialog.Backdrop className="prelude-dialog__backdrop" />
          <Dialog.Viewport className="prelude-dialog__viewport">
            <Dialog.Popup className="prelude-dialog confirm-dialog">
              <Dialog.Title className="confirm-dialog__title">
                {confirmation?.options.title ?? '确认操作'}
              </Dialog.Title>
              <Dialog.Description className="confirm-dialog__description">
                {confirmation?.options.message}
              </Dialog.Description>
              <div className="confirm-dialog__actions">
                <Button variant="secondary" onClick={() => settle(false)}>
                  取消
                </Button>
                <Button
                  variant={confirmation?.options.danger ? 'danger' : 'primary'}
                  onClick={() => settle(true)}
                >
                  {confirmation?.options.confirmText ?? '确认'}
                </Button>
              </div>
            </Dialog.Popup>
          </Dialog.Viewport>
        </Dialog.Portal>
      </Dialog.Root>
    </FeedbackContext.Provider>
  )
}
