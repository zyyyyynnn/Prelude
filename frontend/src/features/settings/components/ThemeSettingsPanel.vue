<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import type { ThemePreference } from '../model/types'
import { storeToRefs } from 'pinia'
import { useUserProfileStore } from '../stores/userProfileStore'
import { usePageNotice } from '@/shared/ui/sonner/usePageNotice'
import { withMinDelay } from '@/shared/lib/utils'
import { getErrorMessage } from '@/shared/lib/errors'
import { applyThemePreference, storeThemePreference } from '../model/theme'
import { InlineAsyncError } from '@/shared/ui/inline-async-error'
import { Skeleton } from '@/shared/ui/skeleton'

const profileStore = useUserProfileStore()
const { profile, status, error } = storeToRefs(profileStore)
const loading = computed(() => status.value === 'idle' || status.value === 'loading')
const saving = ref(false)
const { showNotice } = usePageNotice()

const initial = ref<ThemePreference | null>(null)
const state = ref<ThemePreference | null>(null)
const dirty = computed(() => state.value !== null && state.value !== initial.value)

const themeOptions: Array<{ value: ThemePreference; label: string; desc: string }> = [
  { value: 'light', label: '浅色', desc: '暖色纸面' },
  { value: 'dark', label: '暗色', desc: '低亮度阅读' },
  { value: 'system', label: '跟随系统', desc: '自动同步' },
]

async function loadTheme() {
  try {
    await profileStore.ensureLoaded()
  } catch {
    // The inline error surface owns the initial-load failure.
  }
}

function selectTheme(value: ThemePreference) {
  if (status.value !== 'success') return
  state.value = value
  applyThemePreference(value)
}

async function saveTheme() {
  if (!state.value || state.value === initial.value) {
    showNotice('未检测到主题变更', 'warning')
    return
  }

  saving.value = true
  try {
    const result = await withMinDelay(profileStore.updateProfile({ themePreference: state.value }))
    state.value = result.themePreference || state.value
    initial.value = state.value
    storeThemePreference(state.value)
    applyThemePreference(state.value)
    showNotice('主题已保存', 'success')
  } catch (error) {
    state.value = initial.value
    if (initial.value) applyThemePreference(initial.value)
    showNotice(getErrorMessage(error), 'error')
  } finally {
    saving.value = false
  }
}

function syncFromStore() {
  if (status.value === 'success' && profile.value && !dirty.value) {
    const preference = profile.value.themePreference || 'system'
    state.value = preference
    initial.value = preference
    storeThemePreference(preference)
    applyThemePreference(preference)
  } else if (!profile.value && status.value !== 'success') {
    state.value = null
    initial.value = null
  }
}

function retry() {
  void profileStore.ensureLoaded(true).catch(() => undefined)
}

watch([profile, status], syncFromStore, { immediate: true })

onMounted(() => {
  void loadTheme()
})

defineExpose({ submit: saveTheme, saving, loading, dirty })
</script>

<template>
  <div class="panel-content-wrapper" :aria-busy="loading">
    <div v-if="loading" class="theme-loading" aria-busy="true">
      <Skeleton class="theme-loading__card" />
      <Skeleton class="theme-loading__card" />
      <Skeleton class="theme-loading__card" />
    </div>
    <InlineAsyncError v-else-if="status === 'error'" :message="error" @retry="retry" />
    <div v-else-if="status === 'success' && state">
      <InlineAsyncError v-if="error" :message="error" @retry="retry" />
      <div class="theme-grid">
        <button
          v-for="option in themeOptions"
          :key="option.value"
          type="button"
          :disabled="loading || saving"
          :class="[
            'theme-option ui-action ui-action-selectable',
            { 'is-active': state === option.value },
          ]"
          @click="selectTheme(option.value)"
        >
          <span class="theme-option__preview" :data-theme-preview="option.value">
            <span />
            <span />
          </span>
          <span class="theme-option__copy">
            <span class="theme-option__label">{{ option.label }}</span>
            <span class="theme-option__desc">{{ option.desc }}</span>
          </span>
        </button>
      </div>
    </div>
    <div v-else class="theme-loading" aria-busy="true">
      <Skeleton class="theme-loading__card" />
    </div>
  </div>
</template>

<style scoped>
.panel-content-wrapper {
  display: flex;
  flex-direction: column;
  gap: var(--spacing-md);
}

.theme-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: var(--spacing-sm);
}

.theme-loading {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: var(--spacing-sm);
}

.theme-loading__card {
  min-block-size: calc(var(--layout-settings-dialog-min-block-size) / 4);
  border-radius: var(--radius-md);
}

.theme-option {
  display: grid;
  gap: var(--spacing-sm);
  border: 1px solid transparent;
  border-radius: var(--radius-md);
  padding: var(--spacing-sm);
  background: var(--color-surface);
  color: var(--color-text-primary);
  text-align: left;
  cursor: pointer;
  transition:
    background-color var(--motion-duration-base) var(--motion-ease-standard),
    border-color var(--motion-duration-base) var(--motion-ease-standard);
}

.theme-option:hover,
.theme-option.is-active {
  background: var(--color-surface-hover);
  border-color: var(--color-ring);
}

.theme-option__preview {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: var(--spacing-xs);
  height: var(--ui-height-base);
}

.theme-option__preview span {
  border-radius: var(--radius-sm);
  background: var(--color-surface-muted);
}

.theme-option__preview[data-theme-preview='dark'] span {
  background: var(--color-text-secondary);
}

.theme-option__preview[data-theme-preview='system'] span:first-child {
  background: var(--color-surface-muted);
}

.theme-option__preview[data-theme-preview='system'] span:last-child {
  background: var(--color-text-secondary);
}

.theme-option__copy {
  display: grid;
  gap: var(--spacing-0-5);
}

.theme-option__label {
  font-family: var(--font-serif);
  font-size: var(--font-size-sm);
  font-weight: 600;
}

.theme-option__desc {
  margin: 0;
  color: var(--color-text-tertiary);
  font-family: var(--font-serif);
  font-size: var(--font-size-sm);
}
</style>
