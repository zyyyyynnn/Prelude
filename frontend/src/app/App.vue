<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { RouterView, useRoute } from 'vue-router'
import { useAuthStore } from '@/features/auth'
import { SessionSidebar, useInterviewSessionStore } from '@/features/interview'
import {
  applyThemePreference,
  getStoredThemePreference,
  GlobalSettingsModal,
  resolveThemePreference,
  useUserProfileStore,
  storeThemePreference,
} from '@/features/settings'
import { cleanupInputIntentListener, initInputIntentListener } from '@/shared/lib/input-intent'
import GlobalConfirmDialog from '@/shared/ui/confirm-dialog/GlobalConfirmDialog.vue'
import { Toaster } from '@/shared/ui/sonner'

const authStore = useAuthStore()
const sessionStore = useInterviewSessionStore()
const profileStore = useUserProfileStore()
const route = useRoute()
const isSidebarCollapsed = ref(false)
const showGlobalSettings = ref(false)
const activeSettingsTab = ref<'profile' | 'theme' | 'llm'>('profile')

function handleOpenSettings(tab?: 'profile' | 'theme' | 'llm') {
  activeSettingsTab.value = tab || 'profile'
  showGlobalSettings.value = true
}

const showSidebar = computed(() => route.path !== '/login' && authStore.isLoggedIn)

const mediaQuery = window.matchMedia('(prefers-color-scheme: dark)')

function applyStoredTheme() {
  const stored = getStoredThemePreference()
  applyThemePreference(stored)
  return stored
}

async function synchronizeProfileTheme(scope: string) {
  try {
    const profile = await profileStore.ensureLoaded()
    if (scope !== authStore.accountScope || !profile) return
    const preference = resolveThemePreference(profile.themePreference)
    storeThemePreference(preference)
    applyThemePreference(preference)
  } catch {
    // The cached theme remains authoritative until the profile can be loaded.
  }
}

function handleSystemThemeChange() {
  if (getStoredThemePreference() === 'system') {
    applyThemePreference('system')
  }
}

watch(
  () => authStore.accountScope,
  (accountScope) => {
    sessionStore.activateAccount(accountScope)
    profileStore.activateAccount(accountScope)
    applyStoredTheme()
    if (accountScope) void synchronizeProfileTheme(accountScope)
  },
  { immediate: true, flush: 'sync' },
)

onMounted(() => {
  initInputIntentListener()
  mediaQuery.addEventListener('change', handleSystemThemeChange)
})

onBeforeUnmount(() => {
  cleanupInputIntentListener()
  mediaQuery.removeEventListener('change', handleSystemThemeChange)
})
</script>

<template>
  <div class="app-layout">
    <SessionSidebar
      v-if="showSidebar"
      v-model:collapsed="isSidebarCollapsed"
      @open-global-settings="handleOpenSettings"
    />
    <div class="app-layout__main">
      <RouterView @open-global-settings="handleOpenSettings" />
    </div>
    <GlobalSettingsModal
      v-model:visible="showGlobalSettings"
      v-model:activeTab="activeSettingsTab"
    />
    <GlobalConfirmDialog />
    <Toaster position="top-center" />
  </div>
</template>

<style>
.app-layout {
  display: flex;
  height: 100vh;
  overflow: hidden;
  background: var(--color-bg);
}
.app-layout__main {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  height: 100%;
  overflow: hidden;
}
</style>
