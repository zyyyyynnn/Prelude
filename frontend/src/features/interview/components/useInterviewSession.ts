import { useEffect, useRef, useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { fetchSession, finishInterview, streamInterview } from '../api'
import type { InterviewMessageRecord, InterviewSessionDetailResponse } from '../types'
import { applyMessageUpdate } from './message-updates'
import {
  MAX_CONTEXT_MESSAGES,
  handleInterviewStreamEvent,
  type SessionReportCache,
} from './interview-turn-stream'

export function useInterviewSession(sessionId: number, onError: (message: string) => void) {
  const client = useQueryClient()
  const [messages, setMessages] = useState<InterviewMessageRecord[] | null>(null)
  const [showReport, setShowReport] = useState(false)
  const abort = useRef<AbortController | null>(null)
  const autoStartedSessionId = useRef<number | null>(null)
  const session = useQuery({
    queryKey: ['interview-session', sessionId],
    queryFn: ({ signal }) => fetchSession(sessionId, signal),
    refetchInterval: (query) => (query.state.data?.status === 'generating' ? 2000 : false),
  })
  const current = session.data
  const visibleMessages = messages ?? current?.messages ?? []

  useEffect(
    () => () => {
      abort.current?.abort()
    },
    [],
  )

  function updateMessage(message: InterviewMessageRecord, append = false) {
    setMessages((existing) => applyMessageUpdate(existing, current?.messages, message, append))
  }

  const sessionCache: SessionReportCache = {
    setQueryData: (key, updater) => {
      client.setQueryData<InterviewSessionDetailResponse>(key, updater)
    },
  }

  const send = useMutation({
    mutationFn: async ({
      content,
      autoStart = false,
    }: {
      content: string
      autoStart?: boolean
    }) => {
      const optimisticId = Date.now()
      const assistantId = optimisticId + 1
      const base = [...visibleMessages]
      if (!autoStart)
        base.push({ id: optimisticId, role: 'user', content, createdAt: new Date().toISOString() })
      base.push({
        id: assistantId,
        role: 'assistant',
        content: '',
        createdAt: new Date().toISOString(),
      })
      setMessages(base)
      abort.current?.abort()
      abort.current = new AbortController()
      const context = base.filter((item) => item.id !== assistantId).slice(-MAX_CONTEXT_MESSAGES)
      await streamInterview(
        sessionId,
        { content, messages: context },
        (event) =>
          handleInterviewStreamEvent(event, assistantId, sessionId, sessionCache, {
            updateMessage,
            setMessages,
            setShowReport,
            onError,
          }),
        abort.current.signal,
        autoStart,
      )
    },
    onSuccess: async () => {
      await session.refetch()
      setMessages(null)
      await client.invalidateQueries({ queryKey: ['interview-sessions'] })
    },
    onError: async (error) => {
      if (error.name === 'AbortError') return
      onError(error.message)
      await session.refetch()
      setMessages(null)
    },
  })

  const { mutate: appendTurn } = send

  useEffect(() => {
    if (
      !current ||
      current.messages.length ||
      autoStartedSessionId.current === sessionId ||
      current.status === 'finished'
    )
      return
    const timer = window.setTimeout(() => {
      if (autoStartedSessionId.current === sessionId) return
      autoStartedSessionId.current = sessionId
      appendTurn({ content: '', autoStart: true })
    }, 0)
    return () => window.clearTimeout(timer)
    /* `mutate` keeps its identity across renders while the mutation result object does not, so
       depending on `send` would tear this timer down and re-arm it on every render — including
       the renders the auto-start itself causes. */
  }, [current, appendTurn, sessionId])

  const finish = useMutation({
    mutationFn: () => finishInterview(sessionId),
    onSuccess: async (result) => {
      setShowReport(true)
      client.setQueryData<InterviewSessionDetailResponse>(
        ['interview-session', sessionId],
        (old) =>
          old
            ? {
                ...old,
                status: result.status ?? 'generating',
                summaryReport: result.summaryReport || old.summaryReport,
              }
            : old,
      )
      await client.invalidateQueries({ queryKey: ['interview-sessions'] })
    },
    onError: (error) => onError(error.message),
  })
  return {
    session,
    current,
    messages: visibleMessages,
    showReport,
    setShowReport,
    sending: send.isPending,
    finishing: finish.isPending,
    send: (content: string) => send.mutate({ content }),
    finish: () => finish.mutate(),
    updateMessage,
    refresh: () => void session.refetch(),
  }
}
