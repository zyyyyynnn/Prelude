import { createContext, useContext } from 'react'

export type NoticeTone = 'info' | 'success' | 'warning' | 'error'
export type ConfirmOptions = {
  title?: string
  message: string
  confirmText?: string
  danger?: boolean
}

export type FeedbackApi = {
  notify: (message: string, tone?: NoticeTone) => void
  confirm: (options: ConfirmOptions) => Promise<boolean>
}

export const FeedbackContext = createContext<FeedbackApi | null>(null)

export function useFeedback() {
  const value = useContext(FeedbackContext)
  if (!value) throw new Error('FeedbackProvider is missing')
  return value
}
