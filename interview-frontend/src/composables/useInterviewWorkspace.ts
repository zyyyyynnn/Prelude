import { computed, ref, shallowRef } from 'vue'
import { fetchInterviewSessions, fetchInterviewMessages } from '../api/interview'
import type { InterviewSessionItem, InterviewReplayResponse } from '../api/contracts'

const sessions = ref<InterviewSessionItem[]>([])
const activeSessionId = ref<number | null>(null)
const replay = shallowRef<InterviewReplayResponse | null>(null)
const reportMarkdown = ref('')
const sessionLoading = ref(false)

const pinnedSessionIds = ref<number[]>(JSON.parse(localStorage.getItem('pinnedSessionIds') || '[]'))
const deletedSessionIds = ref<number[]>(JSON.parse(localStorage.getItem('deletedSessionIds') || '[]'))

export function useInterviewWorkspace() {
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
    sessionLoading.value = true
    try {
      const detail = await fetchInterviewMessages(sessionId)
      replay.value = detail
      activeSessionId.value = sessionId
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
    isSessionPinned
  }
}
