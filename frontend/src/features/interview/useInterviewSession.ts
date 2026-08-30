import { useEffect, useRef, useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { fetchSession, finishInterview, streamInterview } from './api'
import type { InterviewMessageRecord, InterviewSessionDetailResponse } from './types'

const MAX_CONTEXT_MESSAGES = 20

export function useInterviewSession(sessionId: number, onError: (message: string) => void) {
  const client = useQueryClient()
  const [messages, setMessages] = useState<InterviewMessageRecord[] | null>(null)
  const [showReport, setShowReport] = useState(false)
  const [connectionStatus, setConnectionStatus] = useState('')
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
    setMessages((existing) => {
      const list = [...(existing ?? current?.messages ?? [])]
      const index = list.findIndex((item) => item.id === message.id)
      if (index < 0) list.push(message)
      else
        list[index] = {
          ...list[index],
          ...message,
          content: append ? list[index].content + message.content : message.content,
        }
      return list
    })
  }

  const send = useMutation({
    mutationFn: async ({
      content,
      autoStart = false,
    }: {
      content: string
      autoStart?: boolean
    }) => {
      const userId = Date.now()
      const assistantId = userId + 1
      const base = [...visibleMessages]
      if (!autoStart)
        base.push({ id: userId, role: 'user', content, createdAt: new Date().toISOString() })
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
        ({ name, data }) => {
          if (name === 'message')
            updateMessage({ id: assistantId, role: 'assistant', content: data }, true)
          else if (name === 'status')
            setConnectionStatus(
              data.startsWith('reconnecting_')
                ? `连接已断开，正在尝试第 ${data.split('_')[1]} 次重连`
                : data === 'checking'
                  ? '正在核对会话状态'
                  : '',
            )
          else if (name === 'sync') {
            try {
              setMessages(JSON.parse(data) as InterviewMessageRecord[])
            } catch {
              onError('会话同步数据无法解析')
            }
          } else if (name === 'report_ready') {
            client.setQueryData<InterviewSessionDetailResponse>(
              ['interview-session', sessionId],
              (old) => (old ? { ...old, summaryReport: data, status: 'finished' } : old),
            )
            setShowReport(true)
          } else if (name === 'judge') {
            try {
              const result = JSON.parse(data) as { score?: number; hint?: string }
              setMessages((list) => {
                const next = [...(list ?? [])]
                const index = next.findLastIndex((item) => item.role === 'user')
                if (index >= 0)
                  next[index] = { ...next[index], score: result.score, hint: result.hint }
                return next
              })
            } catch {
              onError('评分数据无法解析')
            }
          } else if (name === 'error') throw new Error(data)
        },
        abort.current.signal,
        autoStart,
      )
    },
    onSuccess: async () => {
      setConnectionStatus('')
      await session.refetch()
      setMessages(null)
      await client.invalidateQueries({ queryKey: ['interview-sessions'] })
    },
    onError: async (error) => {
      if (error.name === 'AbortError') return
      onError(error.message)
      await session.refetch()
      setConnectionStatus('')
      setMessages(null)
    },
  })

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
      send.mutate({ content: '', autoStart: true })
    }, 0)
    return () => window.clearTimeout(timer)
  }, [current, send, sessionId])

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
    connectionStatus,
    sending: send.isPending,
    finishing: finish.isPending,
    send: (content: string) => send.mutate({ content }),
    finish: () => finish.mutate(),
    updateMessage,
    refresh: () => void session.refetch(),
  }
}
