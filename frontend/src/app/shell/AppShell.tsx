import { Outlet } from 'react-router'
import { Sidebar } from './Sidebar'
import { SettingsProvider, useSettings } from '@/features/settings'

export function AppShell() {
  return (
    <SettingsProvider>
      <AppShellContent />
    </SettingsProvider>
  )
}

function AppShellContent() {
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
