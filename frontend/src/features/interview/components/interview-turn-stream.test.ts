// @ts-expect-error node strip-types resolves the sibling TypeScript module by extension
import { handleInterviewStreamEvent } from './interview-turn-stream.ts'
import assert from 'node:assert/strict'
import { test } from 'node:test'
import type { InterviewMessageRecord, InterviewSessionDetailResponse } from '../types'

type Captured = {
  updated: InterviewMessageRecord[]
  lists: (InterviewMessageRecord[] | null)[]
  reportShown: boolean[]
  errors: string[]
  cacheWrites: InterviewSessionDetailResponse[]
}

function harness() {
  const captured: Captured = {
    updated: [],
    lists: [],
    reportShown: [],
    errors: [],
    cacheWrites: [],
  }
  let list: InterviewMessageRecord[] | null = [
    { id: 1, role: 'user', content: 'question' },
    { id: 2, role: 'assistant', content: '' },
  ]
  let session: InterviewSessionDetailResponse | undefined = {
    sessionId: 51,
    status: 'ongoing',
    targetPosition: '前端工程师',
    currentStage: 'warmup',
    summaryReport: undefined,
    stages: [],
    messages: list,
    attachments: [],
  }
  return {
    captured,
    cache: {
      setQueryData(
        _key: ['interview-session', number],
        updater: (
          old: InterviewSessionDetailResponse | undefined,
        ) => InterviewSessionDetailResponse | undefined,
      ) {
        session = updater(session)
        if (session) captured.cacheWrites.push(session)
      },
    },
    callbacks: {
      updateMessage: (msg: InterviewMessageRecord) => {
        captured.updated.push(msg)
      },
      setMessages: (
        updater: (prev: InterviewMessageRecord[] | null) => InterviewMessageRecord[] | null,
      ) => {
        list = updater(list)
        captured.lists.push(list)
      },
      setShowReport: (show: boolean) => {
        captured.reportShown.push(show)
      },
      onError: (msg: string) => {
        captured.errors.push(msg)
      },
    },
  }
}

void test('message deltas target the assistant placeholder', () => {
  const { captured, cache, callbacks } = harness()
  handleInterviewStreamEvent({ name: 'message', data: '你好' }, 2, 51, cache, callbacks)
  assert.deepEqual(captured.updated, [{ id: 2, role: 'assistant', content: '你好' }])
})

void test('report_ready freezes the session and opens the report', () => {
  const { captured, cache, callbacks } = harness()
  handleInterviewStreamEvent({ name: 'report_ready', data: '{"ok":true}' }, 2, 51, cache, callbacks)
  assert.equal(captured.cacheWrites[0].summaryReport, '{"ok":true}')
  assert.equal(captured.cacheWrites[0].status, 'finished')
  assert.deepEqual(captured.reportShown, [true])
})

void test('judge attaches score and hint to the latest user turn', () => {
  const { captured, cache, callbacks } = harness()
  handleInterviewStreamEvent(
    { name: 'judge', data: JSON.stringify({ score: 8, hint: 'clear' }) },
    2,
    51,
    cache,
    callbacks,
  )
  const last = captured.lists.at(-1)
  assert.equal(last?.[0].score, 8)
  assert.equal(last?.[0].hint, 'clear')
  assert.equal(last?.[1].score, undefined)
})

void test('malformed judge payload reports a parse failure', () => {
  const { captured, cache, callbacks } = harness()
  handleInterviewStreamEvent({ name: 'judge', data: '{bad' }, 2, 51, cache, callbacks)
  assert.deepEqual(captured.errors, ['评分数据无法解析'])
})

void test('error frames fail the stream so the optimistic turn is discarded', () => {
  const { cache, callbacks } = harness()
  assert.throws(
    () =>
      handleInterviewStreamEvent({ name: 'error', data: '登录已失效' }, 2, 51, cache, callbacks),
    /登录已失效/,
  )
})
