<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import type { ThemePreference } from '../../api/contracts'
import { fetchUserProfile, updateUserProfile } from '../../api/user'
import { usePageNotice } from '../../composables/usePageNotice'
import { withMinDelay } from '../../lib/utils'
import { getErrorMessage } from '../../utils/errors'
import { applyThemePreference, storeThemePreference } from '../../utils/theme'

const loading = ref(false)
const saving = ref(false)
const { showNotice } = usePageNotice()

const initial = reactive({
  themePreference: 'system' as ThemePreference,
})

const state = reactive({
  themePreference: 'system' as ThemePreference,
})

const themeOptions: Array<{ value: ThemePreference; label: string; desc: string }> = [
  { value: 'light', label: '浅色', desc: '暖色纸面' },
  { value: 'dark', label: '暗色', desc: '低亮度阅读' },
  { value: 'system', label: '跟随系统', desc: '自动同步' },
]

async function loadTheme() {
  loading.value = true
  try {
    const result = await withMinDelay(fetchUserProfile())
    state.themePreference = result.themePreference || 'system'
    initial.themePreference = state.themePreference
    storeThemePreference(state.themePreference)
    applyThemePreference(state.themePreference)
  } catch (error) {
    showNotice(getErrorMessage(error), 'error')
  } finally {
    loading.value = false
  }
}

function selectTheme(value: ThemePreference) {
  state.themePreference = value
  storeThemePreference(value)
  applyThemePreference(value)
}

async function saveTheme() {
  if (state.themePreference === initial.themePreference) {
    showNotice('未检测到主题变更', 'warning')
    return
  }

  saving.value = true
  try {
    const result = await withMinDelay(updateUserProfile({
      themePreference: state.themePreference,
    }))
    state.themePreference = result.themePreference || state.themePreference
    initial.themePreference = state.themePreference
    storeThemePreference(state.themePreference)
    applyThemePreference(state.themePreference)
    showNotice('主题已保存', 'success')
  } catch (error) {
    showNotice(getErrorMessage(error), 'error')
  } finally {
    saving.value = false
  }
}

onMounted(() => {
  void loadTheme()
})

defineExpose({ submit: saveTheme, saving, loading })
</script>

<template>
  <div class="panel-content-wrapper">
    <div class="theme-grid">
      <button
        v-for="option in themeOptions"
        :key="option.value"
        type="button"
        :class="['theme-option', { 'is-active': state.themePreference === option.value }]"
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
  box-shadow: var(--shadow-ring);
  transition:
    background-color var(--motion-duration-base) var(--motion-ease-standard),
    border-color var(--motion-duration-base) var(--motion-ease-standard),
    box-shadow var(--motion-duration-base) var(--motion-ease-standard);
}

.theme-option:hover,
.theme-option:focus-visible,
.theme-option.is-active {
  background: var(--color-surface-hover);
  border-color: var(--color-ring);
  box-shadow: var(--shadow-ring-deep);
  outline: none;
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

.theme-option__preview[data-theme-preview="dark"] span {
  background: var(--color-text-secondary);
}

.theme-option__preview[data-theme-preview="system"] span:first-child {
  background: var(--color-surface-muted);
}

.theme-option__preview[data-theme-preview="system"] span:last-child {
  background: var(--color-text-secondary);
}

.theme-option__copy {
  display: grid;
  gap: calc(var(--spacing-xs) / 2);
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
