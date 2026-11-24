import { expect, test } from 'vite-plus/test'
import { applyMessageUpdate } from './message-updates'

test('a new id is appended without touching earlier messages', () => {
  const next = applyMessageUpdate(
    [{ id: 1, role: 'user', content: 'hi' }],
    undefined,
    { id: 2, role: 'assistant', content: 'hello' },
    false,
  )
  expect(next).toHaveLength(2)
  expect(next[0].content).toBe('hi')
  expect(next[1].content).toBe('hello')
})

test('append concatenates the delta onto the matching id', () => {
  const next = applyMessageUpdate(
    [{ id: 9, role: 'assistant', content: 'ab' }],
    undefined,
    { id: 9, role: 'assistant', content: 'cd' },
    true,
  )
  expect(next[0].content).toBe('abcd')
})

test('replace overwrites the matching id', () => {
  const next = applyMessageUpdate(
    [{ id: 9, role: 'assistant', content: 'ab', score: 1 }],
    undefined,
    { id: 9, role: 'assistant', content: 'final', score: 8, hint: 'ok' },
    false,
  )
  expect(next[0].content).toBe('final')
  expect(next[0].score).toBe(8)
  expect(next[0].hint).toBe('ok')
})

test('fallback seeds the list when local state is empty', () => {
  const next = applyMessageUpdate(
    null,
    [{ id: 1, role: 'user', content: 'seed' }],
    { id: 2, role: 'assistant', content: 'reply' },
    false,
  )
  expect(next.map((item) => item.content)).toEqual(['seed', 'reply'])
})
