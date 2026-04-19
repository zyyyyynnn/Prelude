<script setup lang="ts">
import { computed } from 'vue'
import { RouterLink, RouterView, useRoute, useRouter } from 'vue-router'
import { useAuthStore } from './stores/auth'

const authStore = useAuthStore()
const route = useRoute()
const router = useRouter()

const showShell = computed(() => route.path !== '/login')
const navItems = [
  { label: '面试', to: '/interview' },
  { label: 'LLM 设置', to: '/settings/llm' },
]

function logout() {
  authStore.clearSession()
  void router.replace('/login')
}
</script>

<template>
  <div class="app-shell">
    <header class="app-shell__header">
      <div class="app-shell__brand">
        <span class="app-shell__brand-mark">I</span>
        <div>
          <p class="app-shell__eyebrow">Interview Platform</p>
          <h1 class="app-shell__title">模拟面试系统</h1>
        </div>
      </div>

      <nav v-if="showShell" class="app-shell__nav" aria-label="主导航">
        <RouterLink
          v-for="item in navItems"
          :key="item.to"
          :to="item.to"
          class="app-shell__nav-link"
          active-class="is-active"
        >
          {{ item.label }}
        </RouterLink>
      </nav>

      <div v-if="authStore.isLoggedIn" class="app-shell__actions">
        <span class="ui-badge">已登录</span>
        <button class="ui-button ui-button--secondary" type="button" @click="logout">退出</button>
      </div>
    </header>

    <main class="app-shell__content">
      <RouterView />
    </main>
  </div>
</template>
