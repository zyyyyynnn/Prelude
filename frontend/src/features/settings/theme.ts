import type { ThemePreference } from './types'

const STORAGE_KEY = 'prelude-theme-preference'

export function readTheme(): ThemePreference {
  const value = localStorage.getItem(STORAGE_KEY)
  return value === 'light' || value === 'dark' ? value : 'system'
}

export function applyTheme(value: ThemePreference) {
  localStorage.setItem(STORAGE_KEY, value)
  const dark =
    value === 'dark' || (value === 'system' && matchMedia('(prefers-color-scheme: dark)').matches)
  document.documentElement.classList.toggle('dark', dark)
}

export function initializeTheme() {
  applyTheme(readTheme())
}
