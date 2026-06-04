<script setup lang="ts">
import { computed, ref } from 'vue'
import { RouterView, useRoute } from 'vue-router'
import { useAuthStore } from './stores/auth'
import AppSidebar from './components/workspace/AppSidebar.vue'
import GlobalSettingsModal from './components/workspace/GlobalSettingsModal.vue'
import GlobalConfirmDialog from './components/GlobalConfirmDialog.vue'
import { Toaster } from '@/components/ui/sonner'

const authStore = useAuthStore()
const route = useRoute()
const isSidebarCollapsed = ref(false)
const showGlobalSettings = ref(false)
const activeSettingsTab = ref<'profile' | 'llm'>('profile')

function handleOpenSettings(tab?: 'profile' | 'llm') {
  activeSettingsTab.value = tab || 'profile'
  showGlobalSettings.value = true
}

const showSidebar = computed(() => route.path !== '/login' && authStore.isLoggedIn)
</script>

<template>
  <div class="app-layout" :class="{ 'is-dark': false }">
    <AppSidebar
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
