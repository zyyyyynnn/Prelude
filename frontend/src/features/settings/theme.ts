import type { ThemePreference } from './types'

const STORAGE_KEY = 'prelude-theme-preference'

export function themeStorageKey(accountId?: number | null) {
  return accountId == null ? STORAGE_KEY : `${STORAGE_KEY}:${accountId}`
}

export function readTheme(accountId?: number | null): ThemePreference {
  const value =
    localStorage.getItem(themeStorageKey(accountId)) ??
    (accountId == null ? null : localStorage.getItem(STORAGE_KEY))
  return value === 'light' || value === 'dark' ? value : 'system'
}

export function applyTheme(value: ThemePreference, accountId?: number | null) {
  localStorage.setItem(themeStorageKey(accountId), value)
  const dark =
    value === 'dark' || (value === 'system' && matchMedia('(prefers-color-scheme: dark)').matches)
  // Double rAF after the forced reflow so the transition class only covers the color flip.
  document.documentElement.classList.add('is-theme-transitioning')
  document.documentElement.classList.toggle('dark', dark)
  void document.documentElement.offsetHeight
  requestAnimationFrame(() =>
    requestAnimationFrame(() =>
      document.documentElement.classList.remove('is-theme-transitioning'),
    ),
  )
  window.dispatchEvent(new CustomEvent('prelude-theme-change', { detail: { theme: value, dark } }))
}

export function initializeTheme() {
  applyTheme(readTheme())
}
