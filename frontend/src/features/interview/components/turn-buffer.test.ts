import assert from 'node:assert/strict'
import { test } from 'node:test'
// @ts-expect-error node strip-types resolves the sibling TypeScript module by extension
import { buildOptimisticTurn, shouldAutoStart } from './turn-buffer.ts'

void test('a user turn appends optimistic user and assistant rows', () => {
  const built = buildOptimisticTurn([], 'hello', false, 1000)
  assert.deepEqual(
    built.messages.map((item) => item.role),
    ['user', 'assistant'],
  )
  assert.equal(built.assistantId, 1001)
  assert.equal(built.messages[0].content, 'hello')
  assert.equal(built.messages[1].content, '')
})

void test('auto-start only reserves the assistant placeholder', () => {
  const built = buildOptimisticTurn([], '', true, 1000)
  assert.deepEqual(
    built.messages.map((item) => item.role),
    ['assistant'],
  )
})

void test('the prompt context drops the empty assistant row and caps the window', () => {
  const visible = Array.from({ length: 30 }, (_, index) => ({
    id: index,
    role: 'user' as const,
    content: `m${index}`,
  }))
  const built = buildOptimisticTurn(visible, 'hello', false, 1000)
  assert.equal(built.context.length, 20)
  assert.equal(built.context.at(-1)?.content, 'hello')
  assert.ok(built.context.every((item) => item.id !== built.assistantId))
})

void test('auto-start runs once for an empty unfinished session', () => {
  assert.equal(shouldAutoStart({ messages: [], status: 'ongoing' }, 51, null), true)
  assert.equal(shouldAutoStart({ messages: [], status: 'ongoing' }, 51, 51), false)
  assert.equal(shouldAutoStart({ messages: [], status: 'finished' }, 51, null), false)
  assert.equal(
    shouldAutoStart({ messages: [{ id: 1, role: 'user', content: 'x' }] }, 51, null),
    false,
  )
  assert.equal(shouldAutoStart(undefined, 51, null), false)
})
