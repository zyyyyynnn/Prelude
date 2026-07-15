import { toast } from 'vue-sonner'

export type PageNoticeType = 'success' | 'warning' | 'error' | 'info'

const NOTICE_DURATION = 2000

export function usePageNotice() {
  function showNotice(message: string, type: PageNoticeType = 'info') {
    if (type === 'success') {
      toast.success(message, { duration: NOTICE_DURATION })
    } else if (type === 'warning') {
      toast.warning(message, { duration: NOTICE_DURATION })
    } else if (type === 'error') {
      toast.error(message, { duration: NOTICE_DURATION })
    } else {
      toast.info(message, { duration: NOTICE_DURATION })
    }
  }

  return { showNotice }
}
