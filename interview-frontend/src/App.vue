<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { RouterView, useRoute, useRouter } from 'vue-router'
import BrandMetaballs from './components/BrandMetaballs.vue'
import { useAuthStore } from './stores/auth'

const authStore = useAuthStore()
const route = useRoute()
const router = useRouter()
const menuOpen = ref(false)
const menuAnchor = ref<HTMLElement | null>(null)
const recentReplayPath = ref(localStorage.getItem('recentReplayPath') || '')

const showHeader = computed(() => route.path !== '/login')
const interviewMenuActive = computed(() => route.path === '/interview')
const resumesMenuActive = computed(() => route.path === '/resumes')
const analyticsMenuActive = computed(() => route.path === '/analytics')
const replayMenuActive = computed(() => route.path.startsWith('/interview/replay/'))
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

function navigateToRecentReplay() {
  if (recentReplayPath.value) {
    navigateTo(recentReplayPath.value)
  }
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

watch(
  () => route.fullPath,
  (fullPath) => {
    if (fullPath.startsWith('/interview/replay/')) {
      recentReplayPath.value = fullPath
      localStorage.setItem('recentReplayPath', fullPath)
    }
  },
  { immediate: true },
)

onBeforeUnmount(() => {
  window.removeEventListener('pointerdown', handleDocumentPointerDown)
})
</script>

<template>
  <div class="app-shell">
    <header v-if="showHeader" class="app-shell__header">
      <div class="app-shell__brand">
        <BrandMetaballs class="app-shell__brand-logo" />
        <div>
          <h1 class="app-shell__title">模拟面试系统</h1>
          <p class="app-shell__eyebrow">LLM Moke Interview System</p>
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
          <span class="app-shell__menu-trigger-label">菜单</span>
        </button>

        <div v-if="menuOpen" class="app-shell__action-menu-popper">
          <div class="app-shell__action-menu" role="menu" aria-label="主导航">
            <section class="app-shell__action-menu-section" aria-label="工作区">
              <p class="app-shell__action-menu-heading">工作区</p>
              <button
                :class="['app-shell__action-menu-item', { 'is-active': interviewMenuActive }]"
                type="button"
                role="menuitem"
                @click="navigateTo('/interview')"
              >
                <span class="app-shell__action-menu-label">主工作台</span>
              </button>
              <button
                :class="['app-shell__action-menu-item', { 'is-active': resumesMenuActive }]"
                type="button"
                role="menuitem"
                @click="navigateTo('/resumes')"
              >
                <span class="app-shell__action-menu-label">简历管理</span>
              </button>
              <button
                :class="['app-shell__action-menu-item', { 'is-active': analyticsMenuActive }]"
                type="button"
                role="menuitem"
                @click="navigateTo('/analytics')"
              >
                <span class="app-shell__action-menu-label">数据看板</span>
              </button>
              <button
                :class="['app-shell__action-menu-item', { 'is-active': replayMenuActive }]"
                :disabled="!recentReplayPath"
                type="button"
                role="menuitem"
                @click="navigateToRecentReplay"
              >
                <span class="app-shell__action-menu-label">最近回放</span>
              </button>
            </section>

            <section class="app-shell__action-menu-section" aria-label="设置">
              <p class="app-shell__action-menu-heading">设置</p>
              <button
                :class="['app-shell__action-menu-item', { 'is-active': llmMenuActive }]"
                type="button"
                role="menuitem"
                @click="navigateTo('/settings/llm')"
              >
                <span class="app-shell__action-menu-label">LLM 配置</span>
              </button>
              <button
                :class="['app-shell__action-menu-item', { 'is-active': profileMenuActive }]"
                type="button"
                role="menuitem"
                @click="navigateTo('/settings/profile')"
              >
                <span class="app-shell__action-menu-label">用户设置</span>
              </button>
            </section>

            <section class="app-shell__action-menu-section" aria-label="账户">
              <p class="app-shell__action-menu-heading">账户</p>
              <button
                class="app-shell__action-menu-item"
                type="button"
                role="menuitem"
                @click="logout"
              >
                <span class="app-shell__action-menu-label">退出登录</span>
              </button>
            </section>
          </div>
        </div>
      </div>
    </header>
    <main class="app-shell__content">
      <RouterView />
    </main>
  </div>
</template>
