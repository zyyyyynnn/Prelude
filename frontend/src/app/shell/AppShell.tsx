import {
  Icon,
  SidebarAction,
  SidebarBrand,
  SidebarFrame,
  SidebarPane,
  SegmentedControl,
} from '@/shared/ui'
import { useEffect, useState } from 'react'
import { RiAddLine, RiBarChartLine, RiSettings3Line, RiSideBarLine } from '@remixicon/react'
import { Outlet, useLocation, useNavigate } from 'react-router'
import { SessionGroup, SessionGroupLabel, useSessionList } from '@/features/interview'
import { useResumeConversationList } from '@/features/resume'
import { useSettings } from '@/features/settings'

type WorkspaceMode = 'interview' | 'resume'

const WORKSPACE_STORAGE_KEY = 'prelude-workspace'

function readWorkspace(): WorkspaceMode {
  const stored = window.localStorage.getItem(WORKSPACE_STORAGE_KEY)
  return stored === 'resume' ? 'resume' : 'interview'
}

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
  const navigate = useNavigate()
  const location = useLocation()
  const [storedWorkspace, setStoredWorkspace] = useState<WorkspaceMode>(readWorkspace)
  const workspace: WorkspaceMode = location.pathname.startsWith('/resume')
    ? 'resume'
    : location.pathname.startsWith('/interview')
      ? 'interview'
      : storedWorkspace

  const interviewList = useSessionList()
  const resumeList = useResumeConversationList(
    (() => {
      const raw = new URLSearchParams(location.search).get('conversation')
      return raw == null ? null : Number(raw)
    })(),
    workspace === 'resume',
  )
  const { groups, isPending } = workspace === 'resume' ? resumeList : interviewList

  useEffect(() => {
    window.localStorage.setItem(WORKSPACE_STORAGE_KEY, workspace)
  }, [workspace])

  const switchWorkspace = (mode: WorkspaceMode) => {
    setStoredWorkspace(mode)
    void navigate(mode === 'resume' ? '/resume' : '/interview')
  }

  const primaryLabel = workspace === 'resume' ? '开始新简历' : '开始新面试'
  const startPrimary = () => void navigate(workspace === 'resume' ? '/resume' : '/interview')

  return (
    <aside className="app-sidebar">
      <SidebarFrame
        collapsed={collapsed}
        onToggle={() => setCollapsed((value) => !value)}
        brand={<SidebarBrand />}
        primary={
          <div className="grid gap-sm">
            {!collapsed && (
              <SegmentedControl
                ariaLabel="工作区"
                items={[
                  { value: 'interview', label: '面试' },
                  { value: 'resume', label: '简历' },
                ]}
                value={workspace}
                onValueChange={(value) => switchWorkspace(value)}
              />
            )}
            <SidebarAction
              collapsed={collapsed}
              label={primaryLabel}
              icon={<Icon as={RiAddLine} />}
              tone="primary"
              onClick={startPrimary}
            />
          </div>
        }
        footer={
          <SidebarAction
            collapsed={collapsed}
            label="设置"
            icon={<Icon as={RiSettings3Line} />}
            onClick={onOpenSettings}
          />
        }
      >
        <div className="relative min-h-0 min-w-0 flex-1 overflow-hidden">
          <SidebarPane kind="sessions" visible={!collapsed}>
            {isPending && <SessionGroupLabel>正在加载会话</SessionGroupLabel>}
            {!isPending &&
              groups.map((group) => (
                <SessionGroup
                  key={group.label}
                  label={group.label}
                  emptyLabel="暂无会话"
                  rows={group.rows}
                />
              ))}
          </SidebarPane>

          <SidebarPane kind="rail" visible={collapsed}>
            <SidebarAction
              collapsed
              label="工作区"
              to={workspace === 'resume' ? '/resume' : '/interview'}
              icon={<Icon as={RiSideBarLine} />}
            />
          </SidebarPane>
        </div>

        <nav className="flex flex-col gap-sm" aria-label="工作区工具">
          <SidebarAction
            collapsed={collapsed}
            label="数据看板"
            to="/analytics"
            icon={<Icon as={RiBarChartLine} />}
          />
        </nav>
      </SidebarFrame>
    </aside>
  )
}
