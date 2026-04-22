import { ElMessage } from 'element-plus'

export type PageNoticeType = 'success' | 'warning' | 'error' | 'info'

const NOTICE_DURATION = 2000

export function usePageNotice() {
  function showNotice(message: string, type: PageNoticeType = 'info') {
    ElMessage.closeAll()
    ElMessage({
      message,
      type,
      grouping: true,
      showClose: false,
      duration: NOTICE_DURATION,
      offset: 28,
      customClass: `page-notice page-notice--${type}`,
    })
  }

  return { showNotice }
}
