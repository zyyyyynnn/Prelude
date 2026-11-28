import { expect, test } from 'vite-plus/test'
import { buildOptimisticTurn, retryOrSupersedeTarget, shouldAutoStart } from './turn-buffer'

test('a user turn appends optimistic user and assistant rows', () => {
  const built = buildOptimisticTurn([], 'hello', false, 1000)
  expect(built.messages.map((item) => item.role)).toEqual(['user', 'assistant'])
  expect(built.assistantId).toBe(1001)
  expect(built.messages[0].content).toBe('hello')
  expect(built.messages[1].content).toBe('')
})

test('auto-start only reserves the assistant placeholder', () => {
  const built = buildOptimisticTurn([], '', true, 1000)
  expect(built.messages.map((item) => item.role)).toEqual(['assistant'])
})

test('the prompt context drops the empty assistant row and caps the window', () => {
  const visible = Array.from({ length: 30 }, (_, index) => ({
    id: index,
    role: 'user' as const,
    content: `m${index}`,
  }))
  const built = buildOptimisticTurn(visible, 'hello', false, 1000)
  expect(built.context).toHaveLength(20)
  expect(built.context.at(-1)?.content).toBe('hello')
  expect(built.context.every((item) => item.id !== built.assistantId)).toBe(true)
})

test('a retry keeps the accepted user row and only reserves an assistant placeholder', () => {
  const visible = [{ id: 9, role: 'user' as const, content: 'accepted answer' }]
  const built = buildOptimisticTurn(visible, 'ignored', false, 1000, 9)
  expect(built.messages.map((item) => item.role)).toEqual(['user', 'assistant'])
  expect(built.messages[0].id).toBe(9)
  expect(built.context.at(-1)?.id).toBe(9)
})

test('retries identical content and supersedes changed content', () => {
  const accepted = { id: 9, role: 'user' as const, content: 'answer' }
  expect(retryOrSupersedeTarget(accepted, ' answer ')).toEqual({ retryOfMessageId: 9 })
  expect(retryOrSupersedeTarget(accepted, 'corrected answer')).toEqual({ supersedesMessageId: 9 })
  expect(retryOrSupersedeTarget(undefined, 'answer')).toEqual({})
})

test('auto-start runs once for an empty unfinished session', () => {
  expect(shouldAutoStart({ messages: [], status: 'ongoing' }, 51, null)).toBe(true)
  expect(shouldAutoStart({ messages: [], status: 'ongoing' }, 51, 51)).toBe(false)
  expect(shouldAutoStart({ messages: [], status: 'finished' }, 51, null)).toBe(false)
  expect(shouldAutoStart({ messages: [{ id: 1, role: 'user', content: 'x' }] }, 51, null)).toBe(
    false,
  )
  expect(shouldAutoStart(undefined, 51, null)).toBe(false)
})
