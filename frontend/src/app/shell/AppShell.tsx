import { useEffect, useRef, useState, type ReactNode } from 'react'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import {
  BarChart3,
  ChevronLeft,
  ChevronRight,
  PanelLeft,
  Pin,
  Plus,
  Settings,
  Trash2,
} from 'lucide-react'
import { NavLink, Outlet, useLocation, useNavigate, useSearchParams } from 'react-router'
import { BrandMetaballs } from '@/shared/brand/BrandMetaballs'
import {
  fetchSession,
  fetchSessions,
  groupSessions,
  readSessionPreferences,
  writeSessionPreferences,
  type SessionPreferences,
  type InterviewSessionItem,
} from '@/features/interview'
import { useAuth } from '@/features/auth'
import { useSettings } from '@/features/settings'
import { cn } from '@/shared/lib/cn'
import { IconTooltip } from '@/shared/ui/overlay'
import { useFeedback } from '@/shared/ui/feedback-context'

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
  const auth = useAuth()
  const feedback = useFeedback()
  const client = useQueryClient()
  const navigate = useNavigate()
  const location = useLocation()
  const [params] = useSearchParams()
  const sessionRequest = useRef<AbortController | null>(null)
  const [loadingSessionId, setLoadingSessionId] = useState<number | null>(null)
  const [failedSessionId, setFailedSessionId] = useState<number | null>(null)
  const activeId = Number(params.get('session')) || null
  const accountScope = String(auth.accountId ?? '')
  const [preferences, setPreferences] = useState<SessionPreferences>(() =>
    readSessionPreferences(localStorage, accountScope),
  )
  const sessions = useQuery({
    queryKey: ['interview-sessions'],
    queryFn: ({ signal }) => fetchSessions(signal),
  })
  const grouped = groupSessions(sessions.data ?? [], preferences)

  useEffect(() => () => sessionRequest.current?.abort(), [])

  function updatePreferences(next: SessionPreferences) {
    setPreferences(next)
    writeSessionPreferences(localStorage, accountScope, next)
  }

  function togglePin(sessionId: number) {
    const pinned = preferences.pinnedIds.includes(sessionId)
    updatePreferences({
      ...preferences,
      pinnedIds: pinned
        ? preferences.pinnedIds.filter((id) => id !== sessionId)
        : [...preferences.pinnedIds, sessionId],
    })
    feedback.notify(pinned ? '已取消置顶' : '会话已置顶', 'success')
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
    const accepted = await feedback.confirm({
      title: '删除会话',
      message: `确定要从列表中删除“${session.targetPosition || session.positionName || '未命名岗位'}”吗？`,
      confirmText: '删除',
      danger: true,
    })
    if (!accepted) return
    updatePreferences({
      ...preferences,
      hiddenIds: [...new Set([...preferences.hiddenIds, session.sessionId])],
    })
    if (activeId === session.sessionId) void navigate('/interview')
    feedback.notify('会话已删除', 'success')
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
    <aside className={cn('app-sidebar', collapsed && 'is-collapsed')}>
      <header className="app-sidebar__header">
        <div className="app-sidebar__brand">
          <BrandMetaballs className="app-sidebar__logo" />
          <span className="sidebar-label app-sidebar__title">Prelude</span>
        </div>
        <IconTooltip label={collapsed ? '展开侧栏' : '收起侧栏'}>
          <button
            className="app-sidebar__toggle ui-action ui-action-icon"
            aria-label={collapsed ? '展开侧栏' : '收起侧栏'}
            onClick={() => setCollapsed((value) => !value)}
          >
            <span className="app-sidebar__toggle-icons" aria-hidden="true">
              <ChevronLeft className="app-sidebar__toggle-icon app-sidebar__toggle-icon--collapse" />
              <ChevronRight className="app-sidebar__toggle-icon app-sidebar__toggle-icon--expand" />
            </span>
          </button>
        </IconTooltip>
      </header>

      <div className="app-sidebar__main">
        <div className="app-sidebar__actions">
          <IconTooltip label="开始新面试">
            <button
              className="app-sidebar__btn app-sidebar__btn--primary ui-action ui-action-primary"
              aria-label="开始新面试"
              onClick={startNewInterview}
            >
              <Plus size={20} />
              <span className="sidebar-label">开始新面试</span>
            </button>
          </IconTooltip>
        </div>

        <div className="app-sidebar__workspace-area">
          <div
            className={cn('app-sidebar__sessions scrollable', !collapsed && 'is-visible')}
            aria-hidden={collapsed}
          >
            {sessions.isPending && <p className="session-group__empty">正在加载会话</p>}
            {!sessions.isPending &&
              sessionGroups.map((group) => (
                <SidebarSessionSection
                  key={group.label}
                  label={group.label}
                  items={group.items}
                  isFinished={group.finished}
                  activeId={activeId}
                  currentPath={location.pathname}
                  loadingSessionId={loadingSessionId}
                  failedSessionId={failedSessionId}
                  pinnedIds={preferences.pinnedIds}
                  onOpen={handleSelectSession}
                  onTogglePin={togglePin}
                  onRemove={(session) => void removeSession(session)}
                />
              ))}
          </div>

          <div
            className={cn('app-sidebar__collapsed-actions', collapsed && 'is-visible')}
            aria-hidden={!collapsed}
          >
            <SidebarLink collapsed to="/interview" label="工作区" icon={<PanelLeft size={20} />} />
          </div>
        </div>

        <nav className="app-sidebar__tools" aria-label="工作区工具">
          <SidebarLink
            collapsed={collapsed}
            to="/analytics"
            label="数据看板"
            icon={<BarChart3 size={20} />}
          />
        </nav>
      </div>

      <footer className="app-sidebar__footer">
        <IconTooltip label="设置">
          <button
            className="app-sidebar__btn app-sidebar__btn--settings ui-action ui-action-nav"
            aria-label="设置"
            onClick={onOpenSettings}
          >
            <Settings size={20} />
            <span className="sidebar-label">设置</span>
          </button>
        </IconTooltip>
      </footer>
    </aside>
  )
}

function SidebarLink({
  to,
  label,
  icon,
  collapsed,
}: {
  to: string
  label: string
  icon: ReactNode
  collapsed: boolean
}) {
  const link = (
    <NavLink
      className={({ isActive }) =>
        cn(
          'app-sidebar__btn app-sidebar__btn--tool ui-action ui-action-nav',
          isActive && 'is-active',
        )
      }
      to={to}
      aria-label={label}
    >
      {icon}
      <span className="sidebar-label">{label}</span>
    </NavLink>
  )
  return collapsed ? <IconTooltip label={label}>{link}</IconTooltip> : link
}

function SidebarSessionItem({
  session,
  isFinished,
  isActive,
  isLoading,
  isFailed,
  isPinned,
  onOpen,
  onTogglePin,
  onRemove,
}: {
  session: InterviewSessionItem
  isFinished: boolean
  isActive: boolean
  isLoading: boolean
  isFailed: boolean
  isPinned: boolean
  onOpen: (session: InterviewSessionItem) => void
  onTogglePin: (sessionId: number) => void
  onRemove: (session: InterviewSessionItem) => void
}) {
  const sessionName = session.targetPosition || session.positionName || '未命名岗位'
  const actionPrefix = isFailed ? '重试打开会话' : isFinished ? '打开已结束会话' : '打开会话'

  return (
    <li className="session-item-wrapper">
      <button
        className={cn(
          'session-item-btn ui-action ui-action-nav',
          isActive && 'is-active',
          isLoading && 'is-loading',
          isFailed && 'is-error',
        )}
        aria-label={`${actionPrefix} ${sessionName}`}
        aria-busy={isLoading || undefined}
        onClick={() => onOpen(session)}
      >
        <span className="session-item__name">{sessionName}</span>
        {(isLoading || isFailed) && (
          <span className="session-item__state">{isLoading ? '加载中' : '加载失败'}</span>
        )}
      </button>
      {isPinned && (
        <Pin className="pin-indicator" size={12} fill="currentColor" aria-hidden="true" />
      )}
      <div className="session-item-actions">
        <IconTooltip label={isPinned ? '取消置顶' : '置顶会话'}>
          <button
            className="action-btn ui-action ui-action-icon"
            aria-label={isPinned ? '取消置顶' : '置顶会话'}
            onClick={() => onTogglePin(session.sessionId)}
          >
            <Pin size={14} fill={isPinned ? 'currentColor' : 'none'} />
          </button>
        </IconTooltip>
        <IconTooltip label="删除会话">
          <button
            className="action-btn delete-btn ui-action ui-action-danger"
            aria-label="删除会话"
            onClick={() => onRemove(session)}
          >
            <Trash2 size={14} />
          </button>
        </IconTooltip>
      </div>
    </li>
  )
}

function SidebarSessionSection({
  label,
  items,
  isFinished,
  activeId,
  currentPath,
  loadingSessionId,
  failedSessionId,
  pinnedIds,
  onOpen,
  onTogglePin,
  onRemove,
}: {
  label: string
  items: InterviewSessionItem[]
  isFinished: boolean
  activeId: number | null
  currentPath: string
  loadingSessionId: number | null
  failedSessionId: number | null
  pinnedIds: number[]
  onOpen: (session: InterviewSessionItem) => void
  onTogglePin: (sessionId: number) => void
  onRemove: (session: InterviewSessionItem) => void
}) {
  return (
    <section className="session-group" aria-label={label}>
      <p className="session-group__label">{label}</p>
      {items.length ? (
        <ul className="session-list">
          {items.map((session) => (
            <SidebarSessionItem
              key={session.sessionId}
              session={session}
              isFinished={isFinished}
              isActive={activeId === session.sessionId && currentPath === '/interview'}
              isLoading={loadingSessionId === session.sessionId}
              isFailed={failedSessionId === session.sessionId}
              isPinned={pinnedIds.includes(session.sessionId)}
              onOpen={onOpen}
              onTogglePin={onTogglePin}
              onRemove={onRemove}
            />
          ))}
        </ul>
      ) : (
        <p className="session-group__empty">暂无会话</p>
      )}
    </section>
  )
}
