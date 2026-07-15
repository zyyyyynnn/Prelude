<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { RouterView, useRoute } from 'vue-router'
import { useAuthStore } from '@/features/auth'
import { SessionSidebar } from '@/features/interview'
import {
  applyThemePreference,
  fetchUserProfile,
  getStoredThemePreference,
  GlobalSettingsModal,
  resolveThemePreference,
  storeThemePreference,
} from '@/features/settings'
import GlobalConfirmDialog from '@/shared/ui/confirm-dialog/GlobalConfirmDialog.vue'
import { Toaster } from '@/shared/ui/sonner'

const authStore = useAuthStore()
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

async function loadThemePreference() {
  const stored = getStoredThemePreference()
  applyThemePreference(stored)
  if (!authStore.isLoggedIn) return
  try {
    const profile = await fetchUserProfile()
    const preference = resolveThemePreference(profile.themePreference)
    storeThemePreference(preference)
    applyThemePreference(preference)
  } catch {
    applyThemePreference(stored)
  }
}

function handleSystemThemeChange() {
  if (getStoredThemePreference() === 'system') {
    applyThemePreference('system')
  }
}

onMounted(() => {
  void loadThemePreference()
  mediaQuery.addEventListener('change', handleSystemThemeChange)
})

onBeforeUnmount(() => {
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
