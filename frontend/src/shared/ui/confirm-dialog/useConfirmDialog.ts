import { ref } from 'vue'

interface ConfirmOptions {
  title: string
  message: string
  confirmText?: string
  cancelText?: string
  variant?: 'default' | 'destructive'
}

const isOpen = ref(false)
const options = ref<ConfirmOptions>({ title: '', message: '' })
let resolvePromise: ((value: boolean) => void) | null = null

export function useConfirmDialog() {
  function confirm(opts: ConfirmOptions): Promise<boolean> {
    resolvePromise?.(false)
    options.value = { confirmText: '确定', cancelText: '取消', variant: 'default', ...opts }
    isOpen.value = true
    return new Promise((resolve) => {
      resolvePromise = resolve
    })
  }

  function settle(value: boolean) {
    const resolve = resolvePromise
    resolvePromise = null
    isOpen.value = false
    resolve?.(value)
  }

  function handleConfirm() {
    settle(true)
  }

  function handleCancel() {
    settle(false)
  }

  function handleOpenChange(open: boolean) {
    if (open) return

    queueMicrotask(() => {
      if (isOpen.value) handleCancel()
    })
  }

  return { isOpen, options, confirm, handleConfirm, handleCancel, handleOpenChange }
}
