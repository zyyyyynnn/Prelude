import type { ThemePreference } from '@/api/contracts'

const STORAGE_KEY = 'prelude-theme'

export function resolveThemePreference(value?: string | null): ThemePreference {
  return value === 'light' || value === 'dark' || value === 'system' ? value : 'system'
}

export function getStoredThemePreference(): ThemePreference {
  return resolveThemePreference(localStorage.getItem(STORAGE_KEY))
}

export function storeThemePreference(value: ThemePreference) {
  localStorage.setItem(STORAGE_KEY, value)
}

export function applyThemePreference(value: ThemePreference) {
  const root = document.documentElement
  const prefersDark = window.matchMedia('(prefers-color-scheme: dark)').matches
  const dark = value === 'dark' || (value === 'system' && prefersDark)
  root.classList.toggle('dark', dark)
  root.dataset.theme = value
}
