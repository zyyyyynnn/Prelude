import { expect, test } from 'vite-plus/test'
import { oauthStartPath, safeLocalRedirect } from './redirect'

test('known local routes retain their query and fragment', () => {
  expect(safeLocalRedirect('/analytics?range=30d#trend')).toBe('/analytics?range=30d#trend')
  expect(safeLocalRedirect('/')).toBe('/')
})

test('external and unknown routes fall back to interview', () => {
  for (const value of [
    'https://evil.example/analytics',
    '//evil.example/analytics',
    '/\\evil.example',
    '/settings',
    '/interview/../analytics',
    '/interview%2f%2fevil.example',
  ]) {
    expect(safeLocalRedirect(value)).toBe('/interview')
  }
})

test('oauth start URLs carry only a sanitized local redirect', () => {
  expect(oauthStartPath('google', '/analytics')).toBe(
    '/auth/oauth/google/start?redirect=%2Fanalytics',
  )
  expect(oauthStartPath('github', 'https://evil.example')).toBe(
    '/auth/oauth/github/start?redirect=%2Finterview',
  )
})
