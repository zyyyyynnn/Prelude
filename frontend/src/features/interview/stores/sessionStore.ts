import { computed, ref, shallowRef } from 'vue'
import { defineStore } from 'pinia'
import { fetchInterviewMessages, fetchInterviewSessions } from '../api/interview'
import { groupSessions } from '../model/sessionList'
import type { InterviewSessionDetailResponse, InterviewSessionItem } from '../model/types'
import { useSessionPreferencesStore } from './sessionPreferencesStore'

type SessionListRequest = {
  accountScope: string
  controller: AbortController
  promise: Promise<void>
}

export const useInterviewSessionStore = defineStore('interview-session', () => {
  const preferences = useSessionPreferencesStore()
  const sessions = ref<InterviewSessionItem[]>([])
  const activeSessionId = ref<number | null>(null)
  const replay = shallowRef<InterviewSessionDetailResponse | null>(null)
  const reportMarkdown = ref('')
  const sessionLoading = ref(false)
  let activeAccountScope = ''
  let accountGeneration = 0
  let activeAbortController: AbortController | null = null
  let detailAbortController: AbortController | null = null
  let sessionListRequest: SessionListRequest | null = null

  const groupedSessions = computed(() =>
    groupSessions(sessions.value, {
      pinnedIds: preferences.pinnedIds,
      hiddenIds: preferences.hiddenIds,
    }),
  )
  const primarySessionList = computed(() => groupedSessions.value.active)
  const finishedSessionList = computed(() => groupedSessions.value.finished)

  function abortActiveStream() {
    activeAbortController?.abort()
    activeAbortController = null
  }

  function abortSessionDetail() {
    detailAbortController?.abort()
    detailAbortController = null
  }

  function getNewAbortSignal() {
    abortActiveStream()
    activeAbortController = new AbortController()
    return activeAbortController.signal
  }

  function clearAccountState() {
    abortActiveStream()
    abortSessionDetail()
    sessions.value = []
    activeSessionId.value = null
    replay.value = null
    reportMarkdown.value = ''
    sessionLoading.value = false
  }

  function activateAccount(accountScope: string, storage?: Storage) {
    const normalizedScope = accountScope.trim()
    if (normalizedScope === activeAccountScope) return

    accountGeneration++
    sessionListRequest?.controller.abort()
    sessionListRequest = null
    activeAccountScope = normalizedScope
    clearAccountState()
    preferences.activate(normalizedScope, storage)
  }

  function hydratePreferences(storage?: Storage) {
    if (activeAccountScope) {
      preferences.activate(activeAccountScope, storage)
    }
  }

  function refreshSessionList() {
    if (!activeAccountScope) {
      sessions.value = []
      return Promise.resolve()
    }
    if (sessionListRequest?.accountScope === activeAccountScope) {
      return sessionListRequest.promise
    }

    const requestScope = activeAccountScope
    const requestGeneration = accountGeneration
    const controller = new AbortController()
    const request: SessionListRequest = {
      accountScope: requestScope,
      controller,
      promise: Promise.resolve(),
    }

    request.promise = (async () => {
      try {
        const items = await fetchInterviewSessions(controller.signal)
        if (
          !controller.signal.aborted &&
          activeAccountScope === requestScope &&
          accountGeneration === requestGeneration
        ) {
          sessions.value = items
        }
      } catch (error) {
        if (
          controller.signal.aborted ||
          activeAccountScope !== requestScope ||
          accountGeneration !== requestGeneration
        ) {
          return
        }
        throw error
      } finally {
        if (sessionListRequest === request) {
          sessionListRequest = null
        }
      }
    })()

    sessionListRequest = request
    return request.promise
  }

  async function loadSession(sessionId: number, silent = false) {
    if (!activeAccountScope) return

    abortActiveStream()
    abortSessionDetail()
    const requestScope = activeAccountScope
    const requestGeneration = accountGeneration
    const controller = new AbortController()
    detailAbortController = controller
    sessionLoading.value = true

    try {
      const detail = await fetchInterviewMessages(sessionId, controller.signal)
      if (
        controller.signal.aborted ||
        activeAccountScope !== requestScope ||
        accountGeneration !== requestGeneration
      ) {
        return
      }
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
      if (
        controller.signal.aborted ||
        activeAccountScope !== requestScope ||
        accountGeneration !== requestGeneration
      ) {
        return
      }
      if (!silent) throw error
    } finally {
      if (detailAbortController === controller) {
        detailAbortController = null
        sessionLoading.value = false
      }
    }
  }

  function startNewInterview() {
    abortActiveStream()
    abortSessionDetail()
    activeSessionId.value = null
    replay.value = null
    reportMarkdown.value = ''
    sessionLoading.value = false
  }

  function toggleSessionPin(sessionId: number) {
    preferences.togglePin(sessionId)
  }

  function hideSessionLocally(sessionId: number) {
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
    activateAccount,
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
