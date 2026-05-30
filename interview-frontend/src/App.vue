<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { RouterView, useRoute } from 'vue-router'
import { useAuthStore } from './stores/auth'
import AppSidebar from './components/workspace/AppSidebar.vue'

const authStore = useAuthStore()
const route = useRoute()
const isSidebarCollapsed = ref(false)

const showSidebar = computed(() => route.path !== '/login' && authStore.isLoggedIn)

watch(
  () => route.fullPath,
  (fullPath) => {
    if (fullPath.startsWith('/interview/replay/')) {
      localStorage.setItem('recentReplayPath', fullPath)
    }
  },
  { immediate: true },
)
</script>

<template>
  <div class="app-layout">
    <AppSidebar v-if="showSidebar" v-model:collapsed="isSidebarCollapsed" />
    <div class="app-layout__main">
      <RouterView />
    </div>
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
.app-shell__content {
  width: 100%;
  max-width: 1260px;
  margin: 0 auto;
  padding: 30px 40px 44px;
}
</style>
