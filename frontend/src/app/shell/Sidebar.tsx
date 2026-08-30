import { useEffect, useRef, useState } from 'react'
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
import { NavLink, useLocation, useNavigate, useSearchParams } from 'react-router'
import { BrandMetaballs } from '@/shared/brand/BrandMetaballs'
import {
  fetchSessions,
  fetchSession,
  groupSessions,
  readSessionPreferences,
  writeSessionPreferences,
  type SessionPreferences,
  type InterviewSessionItem,
} from '@/features/interview'
import { useAuth } from '@/features/auth'
import { IconTooltip } from '@/shared/ui'
import { useFeedback } from '@/shared/ui/feedback'

export function Sidebar({ onOpenSettings }: { onOpenSettings: () => void }) {
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
  const sessionGroups = [
    { label: '进行中', items: grouped.active, finished: false },
    { label: '已完成', items: grouped.finished, finished: true },
  ]

  return (
    <aside className={`app-sidebar${collapsed ? ' is-collapsed' : ''}`}>
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
            {collapsed ? <ChevronRight size={18} /> : <ChevronLeft size={18} />}
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
            className={`app-sidebar__sessions scrollable${collapsed ? '' : ' is-visible'}`}
            aria-hidden={collapsed}
          >
            {sessions.isPending && <p className="session-group__empty">正在加载会话</p>}
            {!sessions.isPending &&
              sessionGroups.map((group) => (
                <section className="session-group" key={group.label} aria-label={group.label}>
                  <p className="session-group__label">{group.label}</p>
                  {group.items.length ? (
                    <ul className="session-list">
                      {group.items.map((session) => {
                        const pinned = preferences.pinnedIds.includes(session.sessionId)
                        const active =
                          activeId === session.sessionId && location.pathname === '/interview'
                        const loading = loadingSessionId === session.sessionId
                        const failed = failedSessionId === session.sessionId
                        return (
                          <li className="session-item-wrapper" key={session.sessionId}>
                            <button
                              className={`session-item-btn ui-action ui-action-nav${active ? ' is-active' : ''}${loading ? ' is-loading' : ''}${failed ? ' is-error' : ''}`}
                              aria-label={`${failed ? '重试打开会话' : group.finished ? '打开已结束会话' : '打开会话'} ${session.targetPosition || session.positionName || '未命名岗位'}`}
                              aria-busy={loading || undefined}
                              onClick={() => {
                                // eslint-disable-next-line react-hooks/refs -- This runs only after a user click.
                                sessionRequest.current?.abort()
                                const controller = new AbortController()
                                sessionRequest.current = controller
                                void openSession(session, controller)
                              }}
                            >
                              <span className="session-item__name">
                                {session.targetPosition || session.positionName || '未命名岗位'}
                              </span>
                              {(loading || failed) && (
                                <span className="session-item__state">
                                  {loading ? '加载中' : '加载失败'}
                                </span>
                              )}
                            </button>
                            {pinned && (
                              <Pin
                                className="pin-indicator"
                                size={12}
                                fill="currentColor"
                                aria-hidden="true"
                              />
                            )}
                            <div className="session-item-actions">
                              <IconTooltip label={pinned ? '取消置顶' : '置顶会话'}>
                                <button
                                  className="action-btn ui-action ui-action-icon"
                                  aria-label={pinned ? '取消置顶' : '置顶会话'}
                                  onClick={() => togglePin(session.sessionId)}
                                >
                                  <Pin size={14} fill={pinned ? 'currentColor' : 'none'} />
                                </button>
                              </IconTooltip>
                              <IconTooltip label="删除会话">
                                <button
                                  className="action-btn delete-btn ui-action ui-action-danger"
                                  aria-label="删除会话"
                                  onClick={() => void removeSession(session)}
                                >
                                  <Trash2 size={14} />
                                </button>
                              </IconTooltip>
                            </div>
                          </li>
                        )
                      })}
                    </ul>
                  ) : (
                    <p className="session-group__empty">暂无会话</p>
                  )}
                </section>
              ))}
          </div>

          <div
            className={`app-sidebar__collapsed-actions${collapsed ? ' is-visible' : ''}`}
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
  icon: React.ReactNode
  collapsed: boolean
}) {
  const link = (
    <NavLink
      className={({ isActive }) =>
        `app-sidebar__btn app-sidebar__btn--tool ui-action ui-action-nav${isActive ? ' is-active' : ''}`
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
