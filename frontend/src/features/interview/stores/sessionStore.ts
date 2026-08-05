import { computed, ref, shallowRef } from 'vue'
import { defineStore } from 'pinia'
import type { AsyncStatus } from '@/shared/lib/async-status'
import { getErrorMessage } from '@/shared/lib/errors'
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
  const requestedSessionId = ref<number | null>(null)
  const failedSessionId = ref<number | null>(null)
  const replay = shallowRef<InterviewSessionDetailResponse | null>(null)
  const reportMarkdown = ref('')
  const sessionListStatus = ref<AsyncStatus>('idle')
  const sessionListError = ref<string | null>(null)
  const sessionListRefreshing = ref(false)
  const sessionDetailStatus = ref<AsyncStatus>('idle')
  const sessionDetailError = ref<string | null>(null)
  const sessionDetailRefreshing = ref(false)
  let activeAccountScope = ''
  let accountGeneration = 0
  let activeAbortController: AbortController | null = null
  let detailAbortController: AbortController | null = null
  let detailRequestToken = 0
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
    requestedSessionId.value = null
    failedSessionId.value = null
    replay.value = null
    reportMarkdown.value = ''
    sessionListStatus.value = 'idle'
    sessionListError.value = null
    sessionListRefreshing.value = false
    sessionDetailStatus.value = 'idle'
    sessionDetailError.value = null
    sessionDetailRefreshing.value = false
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
      sessionListStatus.value = 'idle'
      sessionListError.value = null
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

    if (sessions.value.length) {
      sessionListRefreshing.value = true
    } else {
      sessionListStatus.value = 'loading'
    }
    sessionListError.value = null

    request.promise = (async () => {
      try {
        const items = await fetchInterviewSessions(controller.signal)
        if (
          !controller.signal.aborted &&
          activeAccountScope === requestScope &&
          accountGeneration === requestGeneration
        ) {
          sessions.value = items
          sessionListStatus.value = 'success'
          sessionListError.value = null
        }
      } catch (error) {
        if (
          controller.signal.aborted ||
          activeAccountScope !== requestScope ||
          accountGeneration !== requestGeneration
        ) {
          return
        }
        sessionListError.value = getErrorMessage(error)
        sessionListStatus.value = sessions.value.length ? 'success' : 'error'
        throw error
      } finally {
        if (sessionListRequest === request) {
          sessionListRequest = null
        }
        if (activeAccountScope === requestScope && accountGeneration === requestGeneration) {
          sessionListRefreshing.value = false
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
    const requestToken = ++detailRequestToken
    const requestScope = activeAccountScope
    const requestGeneration = accountGeneration
    requestedSessionId.value = sessionId
    failedSessionId.value = null
    const controller = new AbortController()
    detailAbortController = controller
    const hasExistingDetail = replay.value !== null
    if (hasExistingDetail) {
      sessionDetailRefreshing.value = true
    } else {
      sessionDetailStatus.value = 'loading'
    }
    sessionDetailError.value = null

    const isCurrentRequest = () =>
      !controller.signal.aborted &&
      detailAbortController === controller &&
      detailRequestToken === requestToken &&
      requestedSessionId.value === sessionId &&
      activeAccountScope === requestScope &&
      accountGeneration === requestGeneration

    try {
      const detail = await fetchInterviewMessages(sessionId, controller.signal)
      if (!isCurrentRequest()) return
      replay.value = detail
      sessionDetailStatus.value = 'success'
      sessionDetailError.value = null
      failedSessionId.value = null
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
      if (!isCurrentRequest()) return
      sessionDetailError.value = getErrorMessage(error)
      failedSessionId.value = sessionId
      sessionDetailStatus.value = hasExistingDetail ? 'success' : 'error'
      if (!silent) throw error
    } finally {
      if (detailAbortController === controller && detailRequestToken === requestToken) {
        detailAbortController = null
        sessionDetailRefreshing.value = false
      }
    }
  }

  function startNewInterview() {
    abortActiveStream()
    abortSessionDetail()
    detailRequestToken++
    activeSessionId.value = null
    requestedSessionId.value = null
    failedSessionId.value = null
    replay.value = null
    reportMarkdown.value = ''
    sessionDetailStatus.value = 'idle'
    sessionDetailError.value = null
    sessionDetailRefreshing.value = false
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
    requestedSessionId,
    failedSessionId,
    replay,
    reportMarkdown,
    sessionListStatus,
    sessionListError,
    sessionListRefreshing,
    sessionDetailStatus,
    sessionDetailError,
    sessionDetailRefreshing,
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
