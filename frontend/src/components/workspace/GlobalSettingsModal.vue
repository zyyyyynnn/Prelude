<script setup lang="ts">
import { useRouter } from 'vue-router'
import { ref } from 'vue'
import { Dialog, DialogContent, DialogHeader, DialogTitle, DialogDescription } from '@/components/ui/dialog'
import { useAuthStore } from '../../stores/auth'
import UserProfilePanel from './UserProfilePanel.vue'
import LlmSettingsPanel from './LlmSettingsPanel.vue'
import { Button } from '@/components/ui/button'

const visible = defineModel<boolean>('visible', { default: false })
const activeTab = defineModel<'profile' | 'llm'>('activeTab', { default: 'profile' })

const router = useRouter()
const authStore = useAuthStore()
const profilePanel = ref<InstanceType<typeof UserProfilePanel> | null>(null)
const llmPanel = ref<InstanceType<typeof LlmSettingsPanel> | null>(null)

function handleLogout() {
  authStore.clearSession()
  visible.value = false
  void router.replace('/login')
}
</script>

<template>
  <Dialog v-model:open="visible">
    <DialogContent
      class="max-w-[min(960px,90vw)] p-0 h-[60vh] min-h-[500px] !flex !flex-col overflow-hidden bg-surface border-none dialog-no-close"
    >
      <DialogHeader class="hidden">
        <DialogTitle>全局设置</DialogTitle>
        <DialogDescription>全局设置面板</DialogDescription>
      </DialogHeader>
      <div class="settings-layout">
        <aside class="settings-sidebar">
          <div class="sidebar-menu">
            <button :class="['menu-item', { 'is-active': activeTab === 'profile' }]" @click="activeTab = 'profile'">
              <svg xmlns="http://www.w3.org/2000/svg" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2"></path><circle cx="12" cy="7" r="4"></circle></svg>
              账号资料
            </button>
            <button :class="['menu-item', { 'is-active': activeTab === 'llm' }]" @click="activeTab = 'llm'">
              <svg xmlns="http://www.w3.org/2000/svg" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><polyline points="4 17 10 11 4 5"></polyline><line x1="12" y1="19" x2="20" y2="19"></line></svg>
              LLM 配置
            </button>
          </div>
          <div class="sidebar-footer">
            <button class="menu-item menu-item--danger" @click="handleLogout">
              <svg xmlns="http://www.w3.org/2000/svg" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4"></path><polyline points="16 17 21 12 16 7"></polyline><line x1="21" y1="12" x2="9" y2="12"></line></svg>
              退出登录
            </button>
          </div>
        </aside>

        <main class="settings-main">
          <header class="settings-header">
            <h3 class="settings-header__title">{{ activeTab === 'profile' ? '账号资料' : 'LLM 配置' }}</h3>
            <div class="settings-header__actions">
              <template v-if="activeTab === 'llm'">
                <Button
                  variant="secondary"
                  size="sm"
                  class="!font-serif"
                  :disabled="llmPanel?.saving || llmPanel?.loading || llmPanel?.testing"
                  :loading="llmPanel?.testing"
                  @click="llmPanel?.test()"
                >
                  测试连接
                </Button>
                <Button
                  size="sm"
                  class="!font-serif"
                  :disabled="llmPanel?.saving"
                  :loading="llmPanel?.saving"
                  @click="llmPanel?.submit()"
                >
                  保存设置
                </Button>
              </template>
              <template v-else-if="activeTab === 'profile'">
                <Button
                  size="sm"
                  class="!font-serif"
                  :disabled="profilePanel?.saving || profilePanel?.loading"
                  :loading="profilePanel?.saving"
                  @click="profilePanel?.submit()"
                >
                  保存设置
                </Button>
              </template>
            </div>
          </header>
          <div class="settings-content scrollable">
            <UserProfilePanel v-if="activeTab === 'profile'" ref="profilePanel" />
            <LlmSettingsPanel v-else-if="activeTab === 'llm'" ref="llmPanel" />
          </div>
        </main>
      </div>
    </DialogContent>
  </Dialog>
</template>

<style scoped>
/* 双栏布局 */
.settings-layout {
  display: flex;
  width: 100%;
  height: 100%;
  min-height: 0;
  border-radius: var(--radius-lg);
  overflow: hidden;
  box-shadow: var(--shadow-modal);
}
.settings-sidebar {
  width: 220px;
  background: var(--color-surface-muted);
  display: flex;
  flex-direction: column;
  padding: var(--spacing-md) 0;
  border-right: 1px solid var(--color-border);
}
.sidebar-menu {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: var(--spacing-sm);
  padding: 0 var(--spacing-sm);
}
.sidebar-footer {
  padding: 0 var(--spacing-sm);
  margin-top: auto;
}
.menu-item {
  display: flex;
  align-items: center;
  gap: var(--spacing-sm);
  width: 100%;
  text-align: left;
  padding: 0 14px;
  height: var(--ui-height-md);
  border-radius: var(--radius-sm);
  font-size: 14px;
  font-weight: 500;
  font-family: var(--font-serif);
  color: var(--color-text-secondary);
  border: none;
  background: transparent;
  cursor: pointer;
  transition: background-color 0.3s ease-in-out, color 0.3s ease-in-out, box-shadow 0.3s ease-in-out;
}
.menu-item:hover {
  background: color-mix(in srgb, var(--color-text-primary) 5%, transparent);
  color: var(--color-text-primary);
}
.menu-item.is-active {
  background: var(--color-surface);
  color: var(--color-text-primary);
  box-shadow: 0 1px 3px rgba(0,0,0,0.05);
}
.menu-item--danger {
  color: var(--color-error);
}
.menu-item--danger:hover {
  background: color-mix(in srgb, var(--color-error) 10%, transparent);
}

.settings-main {
  flex: 1;
  background: var(--color-surface);
  display: flex;
  flex-direction: column;
  min-width: 0;
}
.settings-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: var(--spacing-md) var(--spacing-lg);
  border-bottom: 1px solid var(--color-border);
}
.settings-header__title {
  margin: 0;
  font-size: 16px;
  font-weight: 500;
  font-family: var(--font-serif);
  color: var(--color-text-primary);
}
.settings-header__actions {
  display: flex;
  align-items: center;
  gap: var(--spacing-sm);
}
.settings-content {
  flex: 1;
  overflow-y: auto;
  padding: var(--spacing-md) var(--spacing-lg);
}
.placeholder {
  color: var(--color-text-tertiary);
  text-align: center;
  padding: 40px;
}
</style>
