import { computed, ref, shallowRef } from 'vue'
import { fetchInterviewSessions, fetchInterviewMessages } from '../api/interview'
import type { InterviewSessionItem, InterviewSessionDetailResponse } from '../api/contracts'

const sessions = ref<InterviewSessionItem[]>([])
const activeSessionId = ref<number | null>(null)
const replay = shallowRef<InterviewSessionDetailResponse | null>(null)
const reportMarkdown = ref('')
const sessionLoading = ref(false)

const pinnedSessionIds = ref<number[]>(JSON.parse(localStorage.getItem('pinnedSessionIds') || '[]'))
const deletedSessionIds = ref<number[]>(JSON.parse(localStorage.getItem('deletedSessionIds') || '[]'))

let activeAbortController: AbortController | null = null

export function useInterviewWorkspace() {
  function abortActiveStream() {
    if (activeAbortController) {
      activeAbortController.abort()
      activeAbortController = null
    }
  }

  function getNewAbortSignal() {
    abortActiveStream()
    activeAbortController = new AbortController()
    return activeAbortController.signal
  }

  const primarySessionList = computed(() => {
    return sessions.value
      .filter((item) => item.status !== 'finished' && !deletedSessionIds.value.includes(item.sessionId))
      .sort((a, b) => {
        const aPinned = pinnedSessionIds.value.includes(a.sessionId)
        const bPinned = pinnedSessionIds.value.includes(b.sessionId)
        if (aPinned && !bPinned) return -1
        if (!aPinned && bPinned) return 1
        return 0
      })
  })

  const finishedSessionList = computed(() => {
    return sessions.value
      .filter((item) => item.status === 'finished' && !deletedSessionIds.value.includes(item.sessionId))
      .sort((a, b) => {
        const aPinned = pinnedSessionIds.value.includes(a.sessionId)
        const bPinned = pinnedSessionIds.value.includes(b.sessionId)
        if (aPinned && !bPinned) return -1
        if (!aPinned && bPinned) return 1
        return 0
      })
  })

  async function refreshSessionList() {
    sessions.value = await fetchInterviewSessions()
  }

  async function loadSession(sessionId: number, silent = false) {
    abortActiveStream()
    sessionLoading.value = true
    try {
      const detail = await fetchInterviewMessages(sessionId)
      replay.value = detail
      activeSessionId.value = sessionId
      // 清理缓存冲突：如果新创建的 ID 命中了本地删除缓存（常见于后端数据库重置后的自增冲突），将其放行
      if (deletedSessionIds.value.includes(sessionId)) {
        deletedSessionIds.value = deletedSessionIds.value.filter(id => id !== sessionId)
        localStorage.setItem('deletedSessionIds', JSON.stringify(deletedSessionIds.value))
      }
      // 兜底补齐：避免刚创建的会话因为后端列表同步延迟而无法显示在侧边栏
      if (!sessions.value.find(s => s.sessionId === sessionId)) {
        sessions.value = [detail as any, ...sessions.value]
      }
      reportMarkdown.value = detail.summaryReport || ''
    } catch (error) {
      if (!silent) {
        throw error
      }
    } finally {
      sessionLoading.value = false
    }
  }

  function startNewInterview() {
    abortActiveStream()
    activeSessionId.value = null
    replay.value = null
    reportMarkdown.value = ''
  }

  function togglePinSession(sessionId: number) {
    const list = [...pinnedSessionIds.value]
    const idx = list.indexOf(sessionId)
    if (idx === -1) {
      list.push(sessionId)
    } else {
      list.splice(idx, 1)
    }
    pinnedSessionIds.value = list
    localStorage.setItem('pinnedSessionIds', JSON.stringify(list))
  }

  function deleteSessionLocal(sessionId: number) {
    abortActiveStream()
    const list = [...deletedSessionIds.value]
    if (!list.includes(sessionId)) {
      list.push(sessionId)
      deletedSessionIds.value = list
      localStorage.setItem('deletedSessionIds', JSON.stringify(list))
    }
    if (activeSessionId.value === sessionId) {
      activeSessionId.value = null
      replay.value = null
      reportMarkdown.value = ''
    }
  }

  function isSessionPinned(sessionId: number) {
    return pinnedSessionIds.value.includes(sessionId)
  }

  return {
    sessions,
    activeSessionId,
    replay,
    reportMarkdown,
    sessionLoading,
    primarySessionList,
    finishedSessionList,
    refreshSessionList,
    loadSession,
    startNewInterview,
    togglePinSession,
    deleteSessionLocal,
    isSessionPinned,
    abortActiveStream,
    getNewAbortSignal
  }
}
