import { expect, test } from 'vite-plus/test'
import { handleInterviewStreamEvent } from './interview-turn-stream'
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

test('message deltas target the assistant placeholder', () => {
  const { captured, cache, callbacks } = harness()
  handleInterviewStreamEvent({ name: 'message', data: '你好' }, 2, 51, cache, callbacks)
  expect(captured.updated).toEqual([{ id: 2, role: 'assistant', content: '你好' }])
})

test('report_ready freezes the session and opens the report', () => {
  const { captured, cache, callbacks } = harness()
  handleInterviewStreamEvent({ name: 'report_ready', data: '{"ok":true}' }, 2, 51, cache, callbacks)
  expect(captured.cacheWrites[0].summaryReport).toBe('{"ok":true}')
  expect(captured.cacheWrites[0].status).toBe('finished')
  expect(captured.reportShown).toEqual([true])
})

test('judge attaches score and hint to the latest user turn', () => {
  const { captured, cache, callbacks } = harness()
  handleInterviewStreamEvent(
    { name: 'judge', data: JSON.stringify({ score: 8, hint: 'clear' }) },
    2,
    51,
    cache,
    callbacks,
  )
  const last = captured.lists.at(-1)
  expect(last?.[0].score).toBe(8)
  expect(last?.[0].hint).toBe('clear')
  expect(last?.[1].score).toBeUndefined()
})

test('malformed judge payload reports a parse failure', () => {
  const { captured, cache, callbacks } = harness()
  handleInterviewStreamEvent({ name: 'judge', data: '{bad' }, 2, 51, cache, callbacks)
  expect(captured.errors).toEqual(['评分数据无法解析'])
})

test('error frames fail the stream so the optimistic turn is discarded', () => {
  const { cache, callbacks } = harness()
  expect(() =>
    handleInterviewStreamEvent({ name: 'error', data: '登录已失效' }, 2, 51, cache, callbacks),
  ).toThrow(/登录已失效/)
})
