import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import {
  readSessionPreferences,
  writeSessionPreferences,
  type SessionPreferences,
} from '../model/sessionPreferences'

export const useSessionPreferencesStore = defineStore('interview-session-preferences', () => {
  const pinnedIds = ref<number[]>([])
  const hiddenIds = ref<number[]>([])
  const hydrated = ref(false)
  const activeAccountScope = ref('')
  let storage: Storage | null = null

  const snapshot = computed<SessionPreferences>(() => ({
    pinnedIds: pinnedIds.value,
    hiddenIds: hiddenIds.value,
  }))

  function clearInMemory() {
    pinnedIds.value = []
    hiddenIds.value = []
    hydrated.value = false
  }

  function activate(accountScope: string, target?: Storage) {
    const normalizedScope = accountScope.trim()
    if (activeAccountScope.value === normalizedScope && hydrated.value) return

    activeAccountScope.value = normalizedScope
    if (!normalizedScope) {
      storage = null
      clearInMemory()
      return
    }

    storage = target ?? localStorage
    const preferences = readSessionPreferences(storage, normalizedScope)
    pinnedIds.value = preferences.pinnedIds
    hiddenIds.value = preferences.hiddenIds
    writeSessionPreferences(storage, normalizedScope, preferences)
    hydrated.value = true
  }

  function persist() {
    if (storage && activeAccountScope.value) {
      writeSessionPreferences(storage, activeAccountScope.value, snapshot.value)
    }
  }

  function togglePin(sessionId: number) {
    pinnedIds.value = pinnedIds.value.includes(sessionId)
      ? pinnedIds.value.filter((id) => id !== sessionId)
      : [...pinnedIds.value, sessionId]
    persist()
  }

  function hide(sessionId: number) {
    if (!hiddenIds.value.includes(sessionId)) {
      hiddenIds.value = [...hiddenIds.value, sessionId]
      persist()
    }
  }

  function unhide(sessionId: number) {
    if (hiddenIds.value.includes(sessionId)) {
      hiddenIds.value = hiddenIds.value.filter((id) => id !== sessionId)
      persist()
    }
  }

  function isPinned(sessionId: number) {
    return pinnedIds.value.includes(sessionId)
  }

  return {
    pinnedIds,
    hiddenIds,
    hydrated,
    activeAccountScope,
    activate,
    togglePin,
    hide,
    unhide,
    isPinned,
  }
})
