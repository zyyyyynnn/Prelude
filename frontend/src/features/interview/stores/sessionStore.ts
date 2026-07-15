import { computed, ref, shallowRef } from 'vue'
import { defineStore } from 'pinia'
import { fetchInterviewMessages, fetchInterviewSessions } from '../api/interview'
import { groupSessions } from '../model/sessionList'
import type { InterviewSessionDetailResponse, InterviewSessionItem } from '../model/types'
import { useSessionPreferencesStore } from './sessionPreferencesStore'

export const useInterviewSessionStore = defineStore('interview-session', () => {
  const preferences = useSessionPreferencesStore()
  const sessions = ref<InterviewSessionItem[]>([])
  const activeSessionId = ref<number | null>(null)
  const replay = shallowRef<InterviewSessionDetailResponse | null>(null)
  const reportMarkdown = ref('')
  const sessionLoading = ref(false)
  let activeAbortController: AbortController | null = null

  const groupedSessions = computed(() =>
    groupSessions(sessions.value, {
      pinnedIds: preferences.pinnedIds,
      hiddenIds: preferences.hiddenIds,
    }),
  )
  const primarySessionList = computed(() => groupedSessions.value.active)
  const finishedSessionList = computed(() => groupedSessions.value.finished)

  function hydratePreferences(storage?: Storage) {
    preferences.hydrate(storage)
  }

  function abortActiveStream() {
    activeAbortController?.abort()
    activeAbortController = null
  }

  function getNewAbortSignal() {
    abortActiveStream()
    activeAbortController = new AbortController()
    return activeAbortController.signal
  }

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
      preferences.unhide(sessionId)
      if (!sessions.value.some((session) => session.sessionId === sessionId)) {
        sessions.value = [
          {
            sessionId: detail.sessionId,
            targetPosition: detail.targetPosition,
            status: detail.status,
            currentStage: detail.currentStage,
            summaryReport: detail.summaryReport,
          },
          ...sessions.value,
        ]
      }
      reportMarkdown.value = detail.summaryReport || ''
    } catch (error) {
      if (!silent) throw error
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

  function toggleSessionPin(sessionId: number) {
    preferences.togglePin(sessionId)
  }

  function hideSessionLocally(sessionId: number) {
    abortActiveStream()
    preferences.hide(sessionId)
    if (activeSessionId.value === sessionId) startNewInterview()
  }

  function isSessionPinned(sessionId: number) {
    return preferences.isPinned(sessionId)
  }

  return {
    sessions,
    activeSessionId,
    replay,
    reportMarkdown,
    sessionLoading,
    primarySessionList,
    finishedSessionList,
    hydratePreferences,
    refreshSessionList,
    loadSession,
    startNewInterview,
    toggleSessionPin,
    hideSessionLocally,
    isSessionPinned,
    abortActiveStream,
    getNewAbortSignal,
  }
})
