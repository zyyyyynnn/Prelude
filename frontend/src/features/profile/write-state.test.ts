import { describe, expect, test } from 'vite-plus/test'
import {
  activateProfileAccount,
  beginProfileRevisionRefresh,
  beginProfileWrite,
  createProfileWriteState,
  deactivateProfileAccount,
  isCurrentProfileWrite,
  settleProfileRevisionRefresh,
  settleProfileWrite,
} from './write-state'

describe('profile account write state', () => {
  test('invalidates a late write when the account generation changes', () => {
    const active = activateProfileAccount(createProfileWriteState(), 1)
    const first = beginProfileWrite(active)!
    const nextAccount = activateProfileAccount(first.state, 2)
    expect(isCurrentProfileWrite(nextAccount, first.context)).toBe(false)
    expect(settleProfileWrite(nextAccount, first.context)).toBe(nextAccount)
  })

  test('blocks overlapping writes and holds the lock until revision refresh settles', () => {
    const active = activateProfileAccount(createProfileWriteState(), 1)
    const first = beginProfileWrite(active)!
    expect(beginProfileWrite(first.state)).toBeNull()
    const refreshing = beginProfileRevisionRefresh(first.state, first.context)
    expect(settleProfileWrite(refreshing, first.context)).toBe(refreshing)
    expect(beginProfileWrite(refreshing)).toBeNull()
    const idle = settleProfileRevisionRefresh(refreshing, first.context)
    expect(beginProfileWrite(idle)).not.toBeNull()
  })

  test('rejects callbacks after the panel unmounts', () => {
    const active = activateProfileAccount(createProfileWriteState(), 1)
    const first = beginProfileWrite(active)!
    const inactive = deactivateProfileAccount(first.state)
    expect(isCurrentProfileWrite(inactive, first.context)).toBe(false)
  })
})
