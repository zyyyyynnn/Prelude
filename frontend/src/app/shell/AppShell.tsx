import { useEffect, useRef, useState } from 'react'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { BarChart3, PanelLeft, Plus, Settings } from 'lucide-react'
import { Outlet, useLocation, useNavigate, useSearchParams } from 'react-router'
import { BrandMetaballs } from '@/shared/brand/BrandMetaballs'
import {
  deleteSession,
  fetchSession,
  fetchSessions,
  groupSessions,
  setSessionPinned,
  type InterviewSessionItem,
} from '@/features/interview'
import { useSettings } from '@/features/settings'
import { useFeedback } from '@/shared/ui/feedback-context'
import { SessionGroup } from '@/shared/ui/session-row'
import { SidebarAction, SidebarFrame, SidebarPane } from '@/shared/ui/sidebar'

export function AppShell() {
  const { openSettings } = useSettings()
  return (
    <div className="app-layout">
      <Sidebar onOpenSettings={() => openSettings()} />
      <main className="app-layout__main">
        <Outlet />
      </main>
    </div>
  )
}

function Sidebar({ onOpenSettings }: { onOpenSettings: () => void }) {
  const [collapsed, setCollapsed] = useState(false)
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
    queryKey: ['interview-sessions'],
    queryFn: ({ signal }) => fetchSessions(signal),
  })
  const grouped = groupSessions(sessions.data ?? [])

  useEffect(() => () => sessionRequest.current?.abort(), [])

  async function togglePin(session: InterviewSessionItem) {
    const pinned = !session.pinned
    try {
      await setSessionPinned(session.sessionId, pinned)
      await client.invalidateQueries({ queryKey: ['interview-sessions'] })
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
        queryKey: ['interview-session', session.sessionId],
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
      message: `“${sessionName}”的问答记录、评分与报告都会被永久删除，无法恢复。`,
      confirmText: '删除',
      danger: true,
    })
    if (!accepted) return
    try {
      await deleteSession(session.sessionId)
      await client.invalidateQueries({ queryKey: ['interview-sessions'] })
      if (activeId === session.sessionId) void navigate('/interview')
      feedback.notify('会话已删除', 'success')
    } catch (error) {
      feedback.notify(error instanceof Error ? error.message : '会话删除失败', 'error')
    }
  }

  const startNewInterview = () => void navigate('/interview')
  const handleSelectSession = (session: InterviewSessionItem) => {
    // oxlint-disable-next-line react-hooks/refs -- This runs only after a user click.
    sessionRequest.current?.abort()
    const controller = new AbortController()
    sessionRequest.current = controller
    void openSession(session, controller)
  }

  const sessionGroups = [
    { label: '进行中', items: grouped.active, finished: false },
    { label: '已完成', items: grouped.finished, finished: true },
  ]

  return (
    <aside className="app-sidebar">
      <SidebarFrame
        collapsed={collapsed}
        onToggle={() => setCollapsed((value) => !value)}
        brand={
          <>
            <BrandMetaballs className="size-(--ui-height-control) flex-shrink-0 rounded-full" />
            <span className="font-serif text-md font-medium text-text-primary" data-sidebar-label>
              Prelude
            </span>
          </>
        }
        primary={
          <SidebarAction
            collapsed={collapsed}
            label="开始新面试"
            icon={<Plus />}
            tone="primary"
            onClick={startNewInterview}
          />
        }
        footer={
          <SidebarAction
            collapsed={collapsed}
            label="设置"
            icon={<Settings />}
            onClick={onOpenSettings}
          />
        }
      >
        <div className="relative min-h-0 min-w-0 flex-1 overflow-hidden">
          <SidebarPane kind="sessions" visible={!collapsed}>
            {sessions.isPending && <p className="ms-xs text-xs text-text-tertiary">正在加载会话</p>}
            {!sessions.isPending &&
              sessionGroups.map((group) => (
                <SessionGroup
                  key={group.label}
                  label={group.label}
                  emptyLabel="暂无会话"
                  rows={group.items.map((session) => ({
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
                    onRemove: () => void removeSession(session),
                  }))}
                />
              ))}
          </SidebarPane>

          <SidebarPane kind="rail" visible={collapsed}>
            <SidebarAction collapsed label="工作区" to="/interview" icon={<PanelLeft />} />
          </SidebarPane>
        </div>

        <nav className="flex flex-col gap-sm" aria-label="工作区工具">
          <SidebarAction
            collapsed={collapsed}
            label="数据看板"
            to="/analytics"
            icon={<BarChart3 />}
          />
        </nav>
      </SidebarFrame>
    </aside>
  )
}
