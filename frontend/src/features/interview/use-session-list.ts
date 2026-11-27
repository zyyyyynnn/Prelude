import { useFeedback } from '@/shared/ui'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { useEffect, useRef, useState } from 'react'
import { useLocation, useNavigate, useSearchParams } from 'react-router'
import { deleteSession, fetchSession, fetchSessions, setSessionPinned } from './api'
import { interviewQueryKeys } from './query-keys'
import { groupSessions } from './session-groups'
import type { SessionListRow } from './session-list'
import type { InterviewSessionItem } from './types'

export interface SessionListGroup {
  label: string
  finished: boolean
  rows: SessionListRow[]
}

export function useSessionList() {
  const feedback = useFeedback()
  const client = useQueryClient()
  const navigate = useNavigate()
  const location = useLocation()
  const [params] = useSearchParams()
  const sessionRequest = useRef<AbortController | null>(null)
  const [loadingSessionId, setLoadingSessionId] = useState<number | null>(null)
  const [failedSessionId, setFailedSessionId] = useState<number | null>(null)
  const activeId = Number(params.get('session')) || null
  const sessions = useQuery({
    queryKey: interviewQueryKeys.sessions(),
    queryFn: ({ signal }) => fetchSessions(signal),
  })
  const grouped = groupSessions(sessions.data ?? [])

  useEffect(() => () => sessionRequest.current?.abort(), [])

  async function togglePin(session: InterviewSessionItem) {
    const pinned = !session.pinned
    try {
      await setSessionPinned(session.sessionId, pinned)
      await client.invalidateQueries({ queryKey: interviewQueryKeys.sessions() })
      feedback.notify(pinned ? '会话已置顶' : '已取消置顶', 'success')
    } catch (error) {
      feedback.notify(error instanceof Error ? error.message : '置顶状态更新失败', 'error')
    }
  }

  async function openSession(session: InterviewSessionItem, controller: AbortController) {
    setLoadingSessionId(session.sessionId)
    setFailedSessionId(null)
    try {
      await client.fetchQuery({
        queryKey: interviewQueryKeys.session(session.sessionId),
        queryFn: ({ signal }) =>
          fetchSession(session.sessionId, AbortSignal.any([signal, controller.signal])),
      })
      if (controller.signal.aborted) return
      setLoadingSessionId(null)
      await navigate(`/interview?session=${session.sessionId}`)
    } catch (error) {
      if (controller.signal.aborted) return
      setLoadingSessionId(null)
      setFailedSessionId(session.sessionId)
      feedback.notify(error instanceof Error ? error.message : '会话加载失败', 'error')
    }
  }

  async function removeSession(session: InterviewSessionItem) {
    const sessionName = session.targetPosition || session.positionName || '未命名岗位'
    const accepted = await feedback.confirm({
      title: '删除会话',
      message: `确认删除“${sessionName}”？删除后无法恢复。`,
      confirmText: '删除',
      danger: true,
    })
    if (!accepted) return
    try {
      await deleteSession(session.sessionId)
      await client.invalidateQueries({ queryKey: interviewQueryKeys.sessions() })
      if (activeId === session.sessionId) void navigate('/interview')
      feedback.notify('会话已删除', 'success')
    } catch (error) {
      feedback.notify(error instanceof Error ? error.message : '会话删除失败', 'error')
    }
  }

  const handleSelectSession = (session: InterviewSessionItem) => {
    sessionRequest.current?.abort()
    const controller = new AbortController()
    sessionRequest.current = controller
    void openSession(session, controller)
  }

  const groups: SessionListGroup[] = [
    { label: '进行中', finished: false, rows: [] },
    { label: '已完成', finished: true, rows: [] },
  ].map((group) => ({
    label: group.label,
    finished: group.finished,
    rows: (group.finished ? grouped.finished : grouped.active).map((session) => ({
      key: session.sessionId,
      name: session.targetPosition || session.positionName || '未命名岗位',
      finished: group.finished,
      pinned: session.pinned ?? false,
      state:
        activeId === session.sessionId && location.pathname === '/interview'
          ? 'active'
          : loadingSessionId === session.sessionId
            ? 'loading'
            : failedSessionId === session.sessionId
              ? 'error'
              : 'idle',
      onOpen: () => handleSelectSession(session),
      onTogglePin: () => void togglePin(session),
      onRemove: () => removeSession(session),
    })),
  }))

  return { groups, isPending: sessions.isPending }
}
