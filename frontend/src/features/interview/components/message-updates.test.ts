// @ts-expect-error node strip-types resolves the sibling TypeScript module by extension
import { applyMessageUpdate } from './message-updates.ts'
import assert from 'node:assert/strict'
import { test } from 'node:test'

void test('a new id is appended without touching earlier messages', () => {
  const next = applyMessageUpdate(
    [{ id: 1, role: 'user', content: 'hi' }],
    undefined,
    { id: 2, role: 'assistant', content: 'hello' },
    false,
  )
  assert.equal(next.length, 2)
  assert.equal(next[0].content, 'hi')
  assert.equal(next[1].content, 'hello')
})

void test('append concatenates the delta onto the matching id', () => {
  const next = applyMessageUpdate(
    [{ id: 9, role: 'assistant', content: 'ab' }],
    undefined,
    { id: 9, role: 'assistant', content: 'cd' },
    true,
  )
  assert.equal(next[0].content, 'abcd')
})

void test('replace overwrites the matching id', () => {
  const next = applyMessageUpdate(
    [{ id: 9, role: 'assistant', content: 'ab', score: 1 }],
    undefined,
    { id: 9, role: 'assistant', content: 'final', score: 8, hint: 'ok' },
    false,
  )
  assert.equal(next[0].content, 'final')
  assert.equal(next[0].score, 8)
  assert.equal(next[0].hint, 'ok')
})

void test('fallback seeds the list when local state is empty', () => {
  const next = applyMessageUpdate(
    null,
    [{ id: 1, role: 'user', content: 'seed' }],
    { id: 2, role: 'assistant', content: 'reply' },
    false,
  )
  assert.deepEqual(
    next.map((item) => item.content),
    ['seed', 'reply'],
  )
})
