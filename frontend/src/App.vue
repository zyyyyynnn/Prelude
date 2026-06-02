<script setup lang="ts">
import { computed, ref } from 'vue'
import { RouterView, useRoute } from 'vue-router'
import { useAuthStore } from './stores/auth'
import AppSidebar from './components/workspace/AppSidebar.vue'
import LlmSettingsDialog from './components/workspace/LlmSettingsDialog.vue'

const authStore = useAuthStore()
const route = useRoute()
const isSidebarCollapsed = ref(false)
const showLlmSettings = ref(false)

const showSidebar = computed(() => route.path !== '/login' && authStore.isLoggedIn)
</script>

<template>
  <div class="app-layout">
    <AppSidebar
      v-if="showSidebar"
      v-model:collapsed="isSidebarCollapsed"
      @open-llm-settings="showLlmSettings = true"
    />
    <div class="app-layout__main">
      <RouterView />
    </div>
  </div>
  <LlmSettingsDialog v-model:visible="showLlmSettings" />
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
