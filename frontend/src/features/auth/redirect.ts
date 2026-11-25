import type { OAuthProvider } from './types'

const LOCAL_PATHS = new Set(['/', '/interview', '/analytics'])

export function safeLocalRedirect(value: string | null | undefined, fallback = '/interview') {
  if (
    !value ||
    value.length > 2048 ||
    !value.startsWith('/') ||
    value.startsWith('//') ||
    value.includes('\\') ||
    [...value].some(
      (character) => character.charCodeAt(0) < 0x20 || character.charCodeAt(0) === 0x7f,
    )
  ) {
    return fallback
  }
  const path = value.split(/[?#]/, 1)[0]
  return LOCAL_PATHS.has(path) ? value : fallback
}

export function oauthStartPath(provider: OAuthProvider, redirect: string | null | undefined) {
  const target = safeLocalRedirect(redirect)
  return `/auth/oauth/${provider}/start?redirect=${encodeURIComponent(target)}`
}
