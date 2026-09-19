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
  deleteSession,
  fetchSession,
  fetchSessions,
  groupSessions,
  setSessionPinned,
  type InterviewSessionItem,
} from '@/features/interview'
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
    <aside className={cn('app-sidebar', collapsed && 'is-collapsed')}>
      <header className="flex h-(--layout-sidebar-header-block-size) items-center justify-between p-sm">
        <div
          className="flex items-center gap-sm overflow-hidden whitespace-nowrap"
          data-sidebar-brand
        >
          <BrandMetaballs className="size-(--ui-height-control) flex-shrink-0 rounded-full" />
          <span className="font-serif text-md font-medium text-text-primary" data-sidebar-label>
            Prelude
          </span>
        </div>
        <IconTooltip label={collapsed ? '展开侧栏' : '收起侧栏'}>
          <button
            className="sidebar-toggle ui-action ui-action-icon"
            aria-label={collapsed ? '展开侧栏' : '收起侧栏'}
            onClick={() => setCollapsed((value) => !value)}
          >
            <span data-toggle-icon-stack aria-hidden="true">
              <ChevronLeft data-toggle-icon="collapse" />
              <ChevronRight data-toggle-icon="expand" />
            </span>
          </button>
        </IconTooltip>
      </header>

      <div className="flex min-h-0 flex-1 flex-col overflow-hidden p-sm">
        <div className="border-b border-border pb-md">
          <IconTooltip label="开始新面试">
            <button
              className="sidebar-action sidebar-action-primary ui-action ui-action-primary"
              aria-label="开始新面试"
              onClick={startNewInterview}
            >
              <Plus />
              <span data-sidebar-label>开始新面试</span>
            </button>
          </IconTooltip>
        </div>

        <div className="relative min-h-0 min-w-0 flex-1 overflow-hidden">
          <div
            className={cn('sidebar-pane sidebar-sessions scrollable', !collapsed && 'is-visible')}
            aria-hidden={collapsed}
          >
            {sessions.isPending && <p className="ms-xs text-xs text-text-tertiary">正在加载会话</p>}
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
                  onOpen={handleSelectSession}
                  onTogglePin={(session) => void togglePin(session)}
                  onRemove={(session) => void removeSession(session)}
                />
              ))}
          </div>

          <div
            className={cn(
              'sidebar-pane flex w-full flex-col justify-end pb-sm',
              collapsed && 'is-visible',
            )}
            aria-hidden={!collapsed}
          >
            <SidebarLink collapsed to="/interview" label="工作区" icon={<PanelLeft size={20} />} />
          </div>
        </div>

        <nav className="flex flex-col gap-sm" aria-label="工作区工具">
          <SidebarLink
            collapsed={collapsed}
            to="/analytics"
            label="数据看板"
            icon={<BarChart3 size={20} />}
          />
        </nav>
      </div>

      <footer className="px-sm pb-sm">
        <IconTooltip label="设置">
          <button
            className="sidebar-action ui-action ui-action-nav"
            aria-label="设置"
            onClick={onOpenSettings}
          >
            <Settings />
            <span data-sidebar-label>设置</span>
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
        cn('sidebar-action ui-action ui-action-nav', isActive && 'is-active')
      }
      to={to}
      aria-label={label}
    >
      {icon}
      <span data-sidebar-label>{label}</span>
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
  onTogglePin: (session: InterviewSessionItem) => void
  onRemove: (session: InterviewSessionItem) => void
}) {
  const sessionName = session.targetPosition || session.positionName || '未命名岗位'
  const actionPrefix = isFailed ? '重试打开会话' : isFinished ? '打开已结束会话' : '打开会话'

  return (
    <li className="session-row-host group/row">
      <button
        className={cn(
          'session-row ui-action ui-action-nav',
          isActive && 'is-active',
          isLoading && 'is-loading',
          isFailed && 'is-error',
        )}
        aria-label={`${actionPrefix} ${sessionName}`}
        aria-busy={isLoading || undefined}
        onClick={() => onOpen(session)}
      >
        <span className="min-w-0 truncate">{sessionName}</span>
        {(isLoading || isFailed) && (
          <span className="ms-auto shrink-0 text-xs">{isLoading ? '加载中' : '加载失败'}</span>
        )}
      </button>
      {isPinned && (
        <Pin
          className="pointer-events-none absolute top-1/2 inset-e-sm flex -translate-y-1/2 items-center text-accent-text opacity-80 group-hover/row:hidden group-focus-within/row:hidden"
          size={12}
          fill="currentColor"
          aria-hidden="true"
        />
      )}
      <div className="session-row-actions group-hover/row:opacity-100 group-focus-within/row:opacity-100">
        <IconTooltip label={isPinned ? '取消置顶' : '置顶会话'}>
          <button
            className="row-action ui-action ui-action-icon"
            aria-label={isPinned ? '取消置顶' : '置顶会话'}
            onClick={() => onTogglePin(session)}
          >
            <Pin size={14} fill={isPinned ? 'currentColor' : 'none'} />
          </button>
        </IconTooltip>
        <IconTooltip label="删除会话">
          <button
            className="row-action row-action-danger ui-action ui-action-danger"
            aria-label="删除会话"
            onClick={() => onRemove(session)}
          >
            <Trash2 />
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
  onOpen: (session: InterviewSessionItem) => void
  onTogglePin: (session: InterviewSessionItem) => void
  onRemove: (session: InterviewSessionItem) => void
}) {
  return (
    <section className="session-group" aria-label={label}>
      <p className="mx-sm mb-sm text-xs font-semibold tracking-label text-text-tertiary">{label}</p>
      {items.length ? (
        <ul className="list-plain flex flex-col gap-sm">
          {items.map((session) => (
            <SidebarSessionItem
              key={session.sessionId}
              session={session}
              isFinished={isFinished}
              isActive={activeId === session.sessionId && currentPath === '/interview'}
              isLoading={loadingSessionId === session.sessionId}
              isFailed={failedSessionId === session.sessionId}
              isPinned={session.pinned ?? false}
              onOpen={onOpen}
              onTogglePin={onTogglePin}
              onRemove={onRemove}
            />
          ))}
        </ul>
      ) : (
        <p className="ms-xs text-xs text-text-tertiary">暂无会话</p>
      )}
    </section>
  )
}
