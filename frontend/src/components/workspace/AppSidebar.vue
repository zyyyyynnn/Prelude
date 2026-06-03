<script setup lang="ts">
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useInterviewWorkspace } from '../../composables/useInterviewWorkspace'
import BrandMetaballs from '../BrandMetaballs.vue'
import { ElMessageBox } from 'element-plus'
import { usePageNotice } from '../../composables/usePageNotice'

const props = defineProps<{
  collapsed: boolean
}>()

const emit = defineEmits<{
  (e: 'update:collapsed', value: boolean): void
  (e: 'open-global-settings'): void
}>()

const route = useRoute()
const router = useRouter()
const { showNotice } = usePageNotice()
const {
  activeSessionId,
  primarySessionList,
  finishedSessionList,
  startNewInterview,
  loadSession,
  togglePinSession,
  deleteSessionLocal,
  isSessionPinned
} = useInterviewWorkspace()

function togglePin(sessionId: number) {
  togglePinSession(sessionId)
  showNotice(isSessionPinned(sessionId) ? '会话已置顶' : '已取消置顶', 'success')
}

async function confirmDelete(sessionId: number, targetPosition?: string) {
  try {
    await ElMessageBox.confirm(
      `确定要删除与“${targetPosition || '未命名岗位'}”的面试会话吗？`,
      '提示',
      {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'warning',
      }
    )
    deleteSessionLocal(sessionId)
    showNotice('会话已删除', 'success')
  } catch (error) {
    // cancelled
  }
}

const interviewMenuActive = computed(() => route.path === '/interview')
const resumesMenuActive = computed(() => route.path === '/resumes')
const analyticsMenuActive = computed(() => route.path === '/analytics')

function toggleCollapse() {
  emit('update:collapsed', !props.collapsed)
}

function handleStartNew() {
  if (route.path !== '/interview') {
    void router.push('/interview')
  }
  startNewInterview()
}

async function handleSessionClick(sessionId: number) {
  if (route.path !== '/interview') {
    await router.push('/interview')
  }
  await loadSession(sessionId)
}

function navigateTo(path: string) {
  if (route.path !== path) {
    void router.push(path)
  }
}

</script>

<template>
  <aside :class="['app-sidebar', { 'is-collapsed': collapsed }]">
    <div class="app-sidebar__header">
      <div class="app-sidebar__brand">
        <BrandMetaballs class="app-sidebar__logo" />
          <span class="sidebar-label app-sidebar__title">Prelude</span>
      </div>
      <button class="app-sidebar__toggle" @click="toggleCollapse" aria-label="Toggle Sidebar">
        <svg viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor" stroke-width="2">
          <path v-if="collapsed" d="M9 5l7 7-7 7" stroke-linecap="round" stroke-linejoin="round" />
          <path v-else d="M15 5l-7 7 7 7" stroke-linecap="round" stroke-linejoin="round" />
        </svg>
      </button>
    </div>

    <div class="app-sidebar__main">
      <div class="app-sidebar__actions">
        <button
          class="app-sidebar__btn app-sidebar__btn--primary"
          @click="handleStartNew"
          aria-label="开始新面试"
          title="开始新面试"
        >
          <svg viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor" stroke-width="2" style="flex-shrink: 0">
            <path d="M12 5v14M5 12h14" stroke-linecap="round" stroke-linejoin="round" />
          </svg>
          <span class="sidebar-label">开始新面试</span>
        </button>
      </div>

      <div class="app-sidebar__sessions scrollable" :class="{ 'is-hidden': collapsed }">
        <div class="session-group">
          <p class="session-group__title">进行中</p>
          <ul v-if="primarySessionList.length" class="session-list">
            <li v-for="session in primarySessionList" :key="session.sessionId" class="session-item-wrapper">
              <button
                :class="['session-item-btn', { 'is-active': activeSessionId === session.sessionId && interviewMenuActive }]"
                @click="handleSessionClick(session.sessionId)"
              >
                <span class="session-item__name">{{ session.targetPosition || '未命名岗位' }}</span>
              </button>
              
              <!-- Pin indicator when not hovered -->
              <div class="pin-indicator" v-if="isSessionPinned(session.sessionId)">
                <svg viewBox="0 0 24 24" width="12" height="12" fill="currentColor">
                  <path d="M16 12V4h1V2H7v2h1v8l-2 2v2h5.2v6h1.6v-6H18v-2z" />
                </svg>
              </div>

              <!-- Quick actions on hover -->
              <div class="session-item-actions">
                <button class="action-btn" @click.stop="togglePin(session.sessionId)" :title="isSessionPinned(session.sessionId) ? '取消置顶' : '置顶'">
                  <svg viewBox="0 0 24 24" width="14" height="14" fill="currentColor" v-if="isSessionPinned(session.sessionId)">
                    <path d="M16 12V4h1V2H7v2h1v8l-2 2v2h5.2v6h1.6v-6H18v-2z" />
                  </svg>
                  <svg viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" stroke-width="2" v-else>
                    <path d="M16 12V4h1V2H7v2h1v8l-2 2v2h5.2v6h1.6v-6H18v-2z" stroke-linecap="round" stroke-linejoin="round" />
                  </svg>
                </button>
                <button class="action-btn delete-btn" @click.stop="confirmDelete(session.sessionId, session.targetPosition)" title="删除">
                  <svg viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" stroke-width="2">
                    <polyline points="3 6 5 6 21 6" stroke-linecap="round" stroke-linejoin="round"></polyline>
                    <path d="M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6m3 0V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2" stroke-linecap="round" stroke-linejoin="round"></path>
                  </svg>
                </button>
              </div>
            </li>
          </ul>
          <p v-else class="session-group__empty">暂无</p>
        </div>
        <div class="session-group">
          <p class="session-group__title">已完成</p>
          <ul v-if="finishedSessionList.length" class="session-list">
            <li v-for="session in finishedSessionList" :key="session.sessionId" class="session-item-wrapper">
              <button
                :class="['session-item-btn', { 'is-active': activeSessionId === session.sessionId && interviewMenuActive }]"
                @click="handleSessionClick(session.sessionId)"
              >
                <span class="session-item__name">{{ session.targetPosition || '未命名岗位' }}</span>
              </button>

              <!-- Pin indicator when not hovered -->
              <div class="pin-indicator" v-if="isSessionPinned(session.sessionId)">
                <svg viewBox="0 0 24 24" width="12" height="12" fill="currentColor">
                  <path d="M16 12V4h1V2H7v2h1v8l-2 2v2h5.2v6h1.6v-6H18v-2z" />
                </svg>
              </div>

              <!-- Quick actions on hover -->
              <div class="session-item-actions">
                <button class="action-btn" @click.stop="togglePin(session.sessionId)" :title="isSessionPinned(session.sessionId) ? '取消置顶' : '置顶'">
                  <svg viewBox="0 0 24 24" width="14" height="14" fill="currentColor" v-if="isSessionPinned(session.sessionId)">
                    <path d="M16 12V4h1V2H7v2h1v8l-2 2v2h5.2v6h1.6v-6H18v-2z" />
                  </svg>
                  <svg viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" stroke-width="2" v-else>
                    <path d="M16 12V4h1V2H7v2h1v8l-2 2v2h5.2v6h1.6v-6H18v-2z" stroke-linecap="round" stroke-linejoin="round" />
                  </svg>
                </button>
                <button class="action-btn delete-btn" @click.stop="confirmDelete(session.sessionId, session.targetPosition)" title="删除">
                  <svg viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" stroke-width="2">
                    <polyline points="3 6 5 6 21 6" stroke-linecap="round" stroke-linejoin="round"></polyline>
                    <path d="M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6m3 0V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2" stroke-linecap="round" stroke-linejoin="round"></path>
                  </svg>
                </button>
              </div>
            </li>
          </ul>
          <p v-else class="session-group__empty">暂无</p>
        </div>
      </div>
      
      <div class="app-sidebar__collapsed-actions" :class="{ 'is-visible': collapsed }">
        <button
          :class="['app-sidebar__btn app-sidebar__btn--icon', { 'is-active': interviewMenuActive }]"
          @click="navigateTo('/interview')"
          aria-label="工作区"
          title="工作区"
        >
          <svg viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor" stroke-width="2">
            <rect x="3" y="3" width="18" height="18" rx="2" stroke-linecap="round" stroke-linejoin="round" />
            <path d="M3 9h18M9 21V9" stroke-linecap="round" stroke-linejoin="round" />
          </svg>
        </button>
      </div>

      <div class="app-sidebar__tools">
        <button
          :class="['app-sidebar__btn app-sidebar__btn--tool', { 'is-active': resumesMenuActive }]"
          @click="navigateTo('/resumes')"
          aria-label="简历管理"
          title="简历管理"
        >
          <svg viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor" stroke-width="2" style="flex-shrink: 0">
            <path d="M14 2H6a2 2 0 00-2 2v16a2 2 0 002 2h12a2 2 0 002-2V8l-6-6z" stroke-linecap="round" stroke-linejoin="round" />
            <path d="M14 2v6h6M16 13H8M16 17H8M10 9H8" stroke-linecap="round" stroke-linejoin="round" />
          </svg>
          <span class="sidebar-label">简历管理</span>
        </button>
        <button
          :class="['app-sidebar__btn app-sidebar__btn--tool', { 'is-active': analyticsMenuActive }]"
          @click="navigateTo('/analytics')"
          aria-label="数据看板"
          title="数据看板"
        >
          <svg viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor" stroke-width="2" style="flex-shrink: 0">
            <path d="M18 20V10M12 20V4M6 20v-6" stroke-linecap="round" stroke-linejoin="round" />
          </svg>
          <span class="sidebar-label">数据看板</span>
        </button>
      </div>
    </div>

    <div class="app-sidebar__footer">
      <button
        class="app-sidebar__btn app-sidebar__btn--settings"
        @click="emit('open-global-settings')"
        aria-label="设置"
        title="设置"
      >
        <svg viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor" stroke-width="2" style="flex-shrink: 0">
          <path d="M12 15a3 3 0 100-6 3 3 0 000 6z" stroke-linecap="round" stroke-linejoin="round" />
          <path d="M19.4 15a1.65 1.65 0 00.33 1.82l.06.06a2 2 0 010 2.83 2 2 0 01-2.83 0l-.06-.06a1.65 1.65 0 00-1.82-.33 1.65 1.65 0 00-1 1.51V21a2 2 0 01-2 2 2 2 0 01-2-2v-.09A1.65 1.65 0 009 19.4a1.65 1.65 0 00-1.82.33l-.06.06a2 2 0 01-2.83 0 2 2 0 010-2.83l.06-.06a1.65 1.65 0 00.33-1.82 1.65 1.65 0 00-1.51-1H3a2 2 0 01-2-2 2 2 0 012-2h.09A1.65 1.65 0 004.6 9a1.65 1.65 0 00-.33-1.82l-.06-.06a2 2 0 010-2.83 2 2 0 012.83 0l.06.06a1.65 1.65 0 001.82.33H9a1.65 1.65 0 001-1.51V3a2 2 0 012-2 2 2 0 012 2v.09a1.65 1.65 0 001 1.51 1.65 1.65 0 001.82-.33l.06-.06a2 2 0 012.83 0 2 2 0 010 2.83l-.06.06a1.65 1.65 0 00-.33 1.82V9a1.65 1.65 0 001.51 1H21a2 2 0 012 2 2 2 0 01-2 2h-.09a1.65 1.65 0 00-1.51 1z" stroke-linecap="round" stroke-linejoin="round" />
        </svg>
        <span class="sidebar-label">设置</span>
      </button>
    </div>
  </aside>
</template>

<style scoped>
.app-sidebar {
  display: flex;
  flex-direction: column;
  width: 260px;
  height: 100vh;
  background-color: var(--color-surface);
  border-right: 1px solid var(--color-border);
  transition: width 0.2s ease;
  flex-shrink: 0;
  position: sticky;
  top: 0;
  z-index: 100;
}
.app-sidebar.is-collapsed {
  width: calc(var(--ui-height-md) + var(--spacing-sm) * 2); /* 36 + 8*2 = 52px，使 26px 重心绝对居中 */
}
.app-sidebar__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: var(--spacing-sm);
  height: 60px;
}
.app-sidebar.is-collapsed .app-sidebar__header {
  justify-content: center;
}
.app-sidebar__brand {
  display: flex;
  align-items: center;
  gap: var(--spacing-sm);
  overflow: hidden;
  white-space: nowrap;
  transition: opacity 0.2s ease, width 0.2s ease, gap 0.2s ease;
  width: 180px;
  opacity: 1;
}
.app-sidebar.is-collapsed .app-sidebar__brand {
  width: 0;
  opacity: 0;
  margin: 0;
  padding: 0;
  gap: 0;
}
.app-sidebar__logo {
  width: 32px;
  height: 32px;
  flex-shrink: 0;
  border-radius: 50%;
  overflow: hidden;
}
.app-sidebar__title {
  font-family: var(--font-serif);
  font-weight: 500;
  font-size: 16px;
  color: var(--color-text-primary);
}
.sidebar-label {
  white-space: nowrap;
  opacity: 1;
  transition: opacity 0.2s ease, width 0.2s ease;
  display: inline-block;
  overflow: hidden;
}
.app-sidebar.is-collapsed .sidebar-label {
  opacity: 0;
  width: 0;
  margin: 0;
  padding: 0;
}
.app-sidebar__toggle {
  background: transparent;
  border: none;
  cursor: pointer;
  color: var(--color-text-secondary);
  display: flex;
  align-items: center;
  justify-content: center;
  padding: var(--spacing-xs);
  border-radius: var(--radius-sm);
  flex-shrink: 0;
}
.app-sidebar__toggle:hover {
  background-color: var(--color-surface-hover);
}
.app-sidebar__toggle:focus-visible {
  outline: 2px solid var(--color-focus);
  outline-offset: -2px;
}
.app-sidebar__main {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  padding: var(--spacing-sm);
  min-height: 0;
}
.app-sidebar__actions {
  margin-bottom: var(--spacing-sm);
}
.app-sidebar__btn {
  display: flex;
  align-items: center;
  gap: var(--spacing-sm);
  width: 100%;
  height: var(--ui-height-md);
  padding: 0 var(--spacing-sm);
  border: none;
  border-radius: var(--radius-md);
  font-family: var(--font-serif);
  font-size: 15px;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.2s;
  background: transparent;
  color: var(--color-text-secondary);
  white-space: nowrap;
  overflow: hidden;
}
.app-sidebar.is-collapsed .app-sidebar__btn {
  padding: 0 var(--spacing-sm);
  margin: 0;
  gap: 0;
  width: var(--ui-height-md);
  height: var(--ui-height-md);
  justify-content: flex-start;
}
.app-sidebar__btn--primary {
  font-family: var(--font-serif);
  background-color: var(--color-brand);
  color: var(--color-surface);
  box-shadow: 0 2px 8px color-mix(in srgb, var(--color-brand) 30%, transparent);
}
.app-sidebar__btn--primary:hover {
  background-color: color-mix(in srgb, var(--color-brand) 85%, var(--color-surface));
}
.app-sidebar__btn--icon:hover,
.app-sidebar__btn--tool:hover,
.app-sidebar__btn--settings:hover {
  background-color: var(--color-surface-hover);
  color: var(--color-text-primary);
}
.app-sidebar__btn.is-active {
  background-color: var(--color-surface-muted);
  color: var(--color-brand);
  font-weight: 600;
}
.app-sidebar__btn:focus-visible {
  outline: 2px solid var(--color-focus);
  outline-offset: -2px;
}
.app-sidebar__sessions {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  overflow-x: hidden;
  margin-bottom: var(--spacing-sm);
  padding-right: 0;
  transition: opacity 0.2s ease;
  opacity: 1;
  scrollbar-width: thin;
  scrollbar-color: var(--color-ring) transparent;
}
.app-sidebar__sessions.is-hidden {
  opacity: 0;
  pointer-events: none;
}
.app-sidebar__collapsed-actions {
  transition: opacity 0.2s ease, max-height 0.2s ease, margin 0.2s ease;
  opacity: 0;
  pointer-events: none;
  max-height: 0;
  overflow: hidden;
  margin: 0;
}
.app-sidebar__collapsed-actions.is-visible {
  opacity: 1;
  pointer-events: auto;
  max-height: 48px;
  margin-top: var(--spacing-sm);
  margin-bottom: var(--spacing-sm);
}
.session-group {
  margin-bottom: var(--spacing-sm);
  white-space: nowrap;
}
.session-group__title {
  font-size: 12px;
  color: var(--color-text-tertiary);
  margin: 0 0 var(--spacing-sm) var(--spacing-sm);
  font-weight: 400;
  text-transform: uppercase;
  letter-spacing: 0.08em;
}
.session-list {
  list-style: none;
  padding: 0;
  margin: 0;
  display: flex;
  flex-direction: column;
  gap: var(--spacing-sm);
}
.session-item-btn {
  width: 100%;
  text-align: left;
  background: transparent;
  border: none;
  padding: 0 var(--spacing-sm);
  height: var(--ui-height-md);
  min-height: var(--ui-height-md);
  max-height: var(--ui-height-md);
  display: flex;
  align-items: center;
  border-radius: var(--radius-md);
  cursor: pointer;
  color: var(--color-text-secondary);
  font-size: 14px;
  line-height: 1;
  transition: all 0.15s;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.session-item-btn:hover {
  background-color: var(--color-surface-hover);
  color: var(--color-text-primary);
}
.session-item-btn.is-active {
  background-color: var(--color-surface-muted);
  color: var(--color-brand);
  font-weight: 500;
}
.session-item-btn:focus-visible {
  outline: 2px solid var(--color-focus);
  outline-offset: -2px;
}
.session-group__empty {
  font-size: 13px;
  color: var(--color-text-tertiary);
  margin: 0 0 0 var(--spacing-sm);
}
.app-sidebar__tools {
  border-top: 1px solid var(--color-border);
  padding-top: var(--spacing-sm);
  display: flex;
  flex-direction: column;
  gap: var(--spacing-sm);
}
.app-sidebar__footer {
  padding: var(--spacing-sm);
  border-top: 1px solid var(--color-border);
}

.session-item-wrapper {
  position: relative;
}
.session-item-wrapper:hover .session-item-actions {
  opacity: 1;
}
.session-item-actions {
  position: absolute;
  right: 6px;
  top: 50%;
  transform: translateY(-50%);
  display: flex;
  align-items: center;
  gap: 4px;
  opacity: 0;
  transition: opacity 0.15s ease;
  background: linear-gradient(90deg, transparent 0%, var(--color-surface) 25%, var(--color-surface) 100%);
  padding-left: var(--spacing-sm);
}
.session-item-wrapper:has(.session-item-btn.is-active) .session-item-actions {
  background: linear-gradient(90deg, transparent 0%, var(--color-surface-muted) 25%, var(--color-surface-muted) 100%);
}
.session-item-wrapper:hover .session-item-actions {
  background: linear-gradient(90deg, transparent 0%, var(--color-surface-hover) 25%, var(--color-surface-hover) 100%);
}
.session-item-wrapper:hover:has(.session-item-btn.is-active) .session-item-actions {
  background: linear-gradient(90deg, transparent 0%, var(--color-surface-hover) 25%, var(--color-surface-hover) 100%);
}
.session-item-wrapper:hover .pin-indicator {
  display: none;
}
.pin-indicator {
  position: absolute;
  right: var(--spacing-sm);
  top: 50%;
  transform: translateY(-50%);
  color: var(--color-brand);
  opacity: 0.8;
  pointer-events: none;
  display: flex;
  align-items: center;
}
.action-btn {
  background: transparent;
  border: none;
  cursor: pointer;
  padding: var(--spacing-xs);
  border-radius: var(--radius-sm);
  color: var(--color-text-tertiary);
  display: flex;
  align-items: center;
  justify-content: center;
  transition: all 0.15s;
}
.action-btn:focus-visible {
  outline: 2px solid var(--color-focus);
  outline-offset: -2px;
}
.action-btn:hover {
  color: var(--color-text-primary);
  background-color: color-mix(in srgb, var(--color-text-primary) 5%, transparent);
}
.action-btn.delete-btn:hover {
  color: var(--color-error);
  background-color: color-mix(in srgb, var(--color-error) 10%, transparent);
}
</style>
