<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { RouterView, useRoute, useRouter } from 'vue-router'
import { useAuthStore } from './stores/auth'

const authStore = useAuthStore()
const route = useRoute()
const router = useRouter()
const menuOpen = ref(false)
const menuAnchor = ref<HTMLElement | null>(null)

const showHeader = computed(() => route.path !== '/login')
const mainMenuActive = computed(
  () =>
    route.path === '/interview' ||
    route.path === '/resumes' ||
    route.path === '/analytics' ||
    route.path.startsWith('/interview/replay/'),
)
const profileMenuActive = computed(() => route.path === '/settings/profile')
const llmMenuActive = computed(() => route.path === '/settings/llm')

function navigateTo(path: string) {
  menuOpen.value = false
  if (route.path !== path) {
    void router.push(path)
  }
}

function logout() {
  menuOpen.value = false
  authStore.clearSession()
  void router.replace('/login')
}

function toggleMenu() {
  menuOpen.value = !menuOpen.value
}

function closeMenu() {
  menuOpen.value = false
}

function handleDocumentPointerDown(event: PointerEvent) {
  const target = event.target
  if (!(target instanceof Node)) {
    return
  }

  if (menuAnchor.value?.contains(target)) {
    return
  }

  menuOpen.value = false
}

onMounted(() => {
  window.addEventListener('pointerdown', handleDocumentPointerDown)
})

onBeforeUnmount(() => {
  window.removeEventListener('pointerdown', handleDocumentPointerDown)
})
</script>

<template>
  <div class="app-shell">
    <header v-if="showHeader" class="app-shell__header">
      <div class="app-shell__brand">
        <span class="app-shell__brand-mark">I</span>
        <div>
          <p class="app-shell__eyebrow">Interview Platform</p>
          <h1 class="app-shell__title">模拟面试系统</h1>
        </div>
      </div>

      <div
        v-if="authStore.isLoggedIn"
        ref="menuAnchor"
        class="app-shell__actions"
        @keydown.escape="closeMenu"
      >
        <button
          :class="['app-shell__menu-trigger', { 'is-open': menuOpen }]"
          type="button"
          aria-label="打开菜单"
          aria-haspopup="menu"
          :aria-expanded="menuOpen"
          @click="toggleMenu"
        >
          <svg
            class="app-shell__menu-icon"
            viewBox="0 0 24 24"
            aria-hidden="true"
            focusable="false"
          >
            <path
              class="app-shell__menu-icon-line app-shell__menu-icon-line--top"
              d="M5 6.5H19"
              fill="none"
              stroke="currentColor"
              stroke-linecap="round"
              stroke-width="1.7"
            />
            <path
              class="app-shell__menu-icon-line app-shell__menu-icon-line--middle"
              d="M8 12H19"
              fill="none"
              stroke="currentColor"
              stroke-linecap="round"
              stroke-width="1.7"
            />
            <path
              class="app-shell__menu-icon-line app-shell__menu-icon-line--bottom"
              d="M5 17.5H16"
              fill="none"
              stroke="currentColor"
              stroke-linecap="round"
              stroke-width="1.7"
            />
          </svg>
        </button>

        <div v-if="menuOpen" class="app-shell__action-menu-popper">
          <div class="app-shell__action-menu" role="menu" aria-label="主导航">
            <button
              :class="['app-shell__action-menu-item', { 'is-active': mainMenuActive }]"
              type="button"
              @click="navigateTo('/interview')"
            >
              <span class="app-shell__action-menu-label">主菜单栏</span>
            </button>
            <button
              :class="['app-shell__action-menu-item', { 'is-active': profileMenuActive }]"
              type="button"
              @click="navigateTo('/settings/profile')"
            >
              <span class="app-shell__action-menu-label">用户设置</span>
            </button>
            <button
              :class="['app-shell__action-menu-item', { 'is-active': llmMenuActive }]"
              type="button"
              @click="navigateTo('/settings/llm')"
            >
              <span class="app-shell__action-menu-label">LLM配置</span>
            </button>
            <button
              class="app-shell__action-menu-item"
              type="button"
              @click="logout"
            >
              <span class="app-shell__action-menu-label">退出</span>
            </button>
          </div>
        </div>
      </div>
    </header>
    <main class="app-shell__content">
      <RouterView />
    </main>
  </div>
</template>
