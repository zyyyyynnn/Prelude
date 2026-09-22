import { SidebarAction, SidebarBrand, SidebarFrame, SidebarPane } from '@/shared/ui'
import { useState } from 'react'
import { BarChart3, PanelLeft, Plus, Settings } from 'lucide-react'
import { Outlet, useNavigate } from 'react-router'
import { SessionGroup, SessionGroupLabel, useSessionList } from '@/features/interview'
import { useSettings } from '@/features/settings'

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
  const { groups, isPending } = useSessionList()

  const startNewInterview = () => void navigate('/interview')

  return (
    <aside className="app-sidebar">
      <SidebarFrame
        collapsed={collapsed}
        onToggle={() => setCollapsed((value) => !value)}
        brand={<SidebarBrand />}
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
