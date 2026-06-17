<script setup lang="ts">
import { useRouter } from 'vue-router'
import { ref } from 'vue'
import { Dialog, DialogContent, DialogHeader, DialogTitle, DialogDescription } from '@/components/ui/dialog'
import { useAuthStore } from '../../stores/auth'
import UserProfilePanel from './UserProfilePanel.vue'
import LlmSettingsPanel from './LlmSettingsPanel.vue'
import ThemeSettingsPanel from './ThemeSettingsPanel.vue'
import { Button } from '@/components/ui/button'
import { Palette } from '@lucide/vue'

const visible = defineModel<boolean>('visible', { default: false })
const activeTab = defineModel<'profile' | 'theme' | 'llm'>('activeTab', { default: 'profile' })

const router = useRouter()
const authStore = useAuthStore()
const profilePanel = ref<InstanceType<typeof UserProfilePanel> | null>(null)
const themePanel = ref<InstanceType<typeof ThemeSettingsPanel> | null>(null)
const llmPanel = ref<InstanceType<typeof LlmSettingsPanel> | null>(null)

const settingsTitle = {
  profile: '账号资料',
  theme: '主题',
  llm: 'LLM 配置',
} as const

function handleLogout() {
  authStore.clearSession()
  visible.value = false
  void router.replace('/login')
}
</script>

<template>
  <Dialog v-model:open="visible">
    <DialogContent
      class="max-w-[min(960px,90vw)] p-0 h-[60vh] min-h-[500px] !flex !flex-col overflow-hidden bg-background border-none dialog-no-close"
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
            <button :class="['menu-item', { 'is-active': activeTab === 'theme' }]" @click="activeTab = 'theme'">
              <Palette class="h-4 w-4" />
              主题
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
            <h3 class="settings-header__title">{{ settingsTitle[activeTab] }}</h3>
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
              <template v-else-if="activeTab === 'theme'">
                <Button
                  size="sm"
                  class="!font-serif"
                  :disabled="themePanel?.saving || themePanel?.loading"
                  :loading="themePanel?.saving"
                  @click="themePanel?.submit()"
                >
                  保存主题
                </Button>
              </template>
            </div>
          </header>
          <div class="settings-content scrollable">
            <UserProfilePanel v-if="activeTab === 'profile'" ref="profilePanel" />
            <ThemeSettingsPanel v-else-if="activeTab === 'theme'" ref="themePanel" />
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
  background: var(--color-surface);
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
  padding: 0 var(--spacing-md);
  height: var(--ui-height-md);
  border-radius: var(--radius-sm);
  font-size: var(--font-size-sm);
  font-weight: 500;
  font-family: var(--font-serif);
  color: var(--color-text-secondary);
  border: none;
  background: transparent;
  cursor: pointer;
  transition: background-color var(--motion-duration-base) var(--motion-ease-standard), color var(--motion-duration-base) var(--motion-ease-standard), box-shadow var(--motion-duration-base) var(--motion-ease-standard);
}
.menu-item:hover {
  background: var(--color-surface-hover);
  color: var(--color-text-primary);
}
.menu-item.is-active {
  background: var(--color-surface-muted);
  color: var(--color-brand);
  box-shadow: 0 1px 3px color-mix(in srgb, var(--color-text-primary) 5%, transparent);
}
.menu-item--danger {
  color: var(--color-error);
}
.menu-item--danger:hover {
  background: color-mix(in srgb, var(--color-error) 10%, transparent);
}

.settings-main {
  flex: 1;
  background: var(--color-background);
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
  background: var(--color-surface);
}
.settings-header__title {
  margin: 0;
  font-size: var(--font-size-md);
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
  padding: var(--spacing-2xl);
}
</style>
