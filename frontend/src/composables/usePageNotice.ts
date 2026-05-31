import { ElMessage } from 'element-plus'

export type PageNoticeType = 'success' | 'warning' | 'error' | 'info'

const NOTICE_DURATION = 2000
const NOTICE_OFFSET = 18

export function usePageNotice() {
  function showNotice(message: string, type: PageNoticeType = 'info') {
    ElMessage.closeAll()
    ElMessage({
      message,
      type,
      grouping: true,
      showClose: false,
      duration: NOTICE_DURATION,
      offset: NOTICE_OFFSET,
      customClass: `page-notice page-notice--${type}`,
    })
  }

  return { showNotice }
}
