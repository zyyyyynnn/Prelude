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
  let storage: Storage | null = null

  const snapshot = computed<SessionPreferences>(() => ({
    pinnedIds: pinnedIds.value,
    hiddenIds: hiddenIds.value,
  }))

  function hydrate(target: Storage = localStorage) {
    if (hydrated.value) return
    storage = target
    const preferences = readSessionPreferences(target)
    pinnedIds.value = preferences.pinnedIds
    hiddenIds.value = preferences.hiddenIds
    writeSessionPreferences(target, preferences)
    hydrated.value = true
  }

  function persist() {
    if (storage) writeSessionPreferences(storage, snapshot.value)
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

  return { pinnedIds, hiddenIds, hydrated, hydrate, togglePin, hide, unhide, isPinned }
})
