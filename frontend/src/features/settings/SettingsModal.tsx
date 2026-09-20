import { LogOut } from 'lucide-react'
import { Suspense, useState, type ReactNode } from 'react'
import { useNavigate } from 'react-router'
import { useAuth } from '@/features/auth'
import { NavItem } from '@/shared/ui/navigation'
import { Dialog } from '@/shared/ui/overlay'
import { LlmSettingsPanel } from './components/LlmSettingsPanel'
import { ProfilePanel } from './components/ProfilePanel'
import { ThemePanel } from './components/ThemePanel'
import {
  sections,
  SettingsContext,
  sectionTitles,
  type SettingsIntent,
  type SettingsOpenRequest,
  type SettingsRequest,
  type SettingsSection,
} from './settings-context'

type ResourcePanelRenderer = (request: SettingsRequest) => ReactNode

export function SettingsProvider({
  children,
  renderResourcePanel,
}: {
  children: ReactNode
  renderResourcePanel: ResourcePanelRenderer
}) {
  const [open, setOpen] = useState(false)
  const [request, setRequest] = useState<SettingsRequest>({ section: 'profile', requestId: 0 })

  function openSettings(next: SettingsOpenRequest = {}) {
    setRequest({
      section: next.section ?? 'profile',
      provider: next.provider,
      intent: next.intent,
      requestId: Date.now(),
    })
    setOpen(true)
  }

  return (
    <SettingsContext.Provider value={{ openSettings }}>
      {children}
      {open && (
        <SettingsModal
          open={open}
          section={request.section}
          provider={request.provider}
          intent={request.intent}
          requestId={request.requestId}
          onSectionChange={(section) =>
            setRequest((current) => ({ ...current, section, intent: undefined }))
          }
          onOpenChange={setOpen}
          renderResourcePanel={renderResourcePanel}
        />
      )}
    </SettingsContext.Provider>
  )
}

export function SettingsModal({
  open,
  section,
  provider,
  intent,
  requestId,
  onSectionChange,
  onOpenChange,
  renderResourcePanel,
}: {
  open: boolean
  section: SettingsSection
  provider?: string
  intent?: SettingsIntent
  requestId: number
  onSectionChange: (section: SettingsSection) => void
  onOpenChange: (value: boolean) => void
  renderResourcePanel: ResourcePanelRenderer
}) {
  const auth = useAuth()
  const navigate = useNavigate()
  return (
    <Dialog open={open} onOpenChange={onOpenChange} title="全局设置" layout="workspace">
      <div className="flex size-full min-h-0 overflow-hidden rounded-lg elevated-modal">
        <aside
          className="flex w-(--layout-settings-sidebar-inline-size) flex-col border-e border-e-border py-md"
          data-slot="settings-sidebar"
        >
          <nav className="flex flex-1 flex-col gap-sm px-sm" aria-label="设置分类">
            {sections.map(({ key, icon: Icon }) => (
              <NavItem
                key={key}
                active={section === key}
                icon={<Icon aria-hidden="true" />}
                label={sectionTitles[key]}
                onClick={() => onSectionChange(key)}
              />
            ))}
          </nav>
          <div className="mt-auto px-sm">
            <NavItem
              label="退出登录"
              tone="danger"
              icon={<LogOut aria-hidden="true" />}
              onClick={() => {
                onOpenChange(false)
                void auth.signOut().then(() => navigate('/login'))
              }}
            />
          </div>
        </aside>
        <main className="flex min-w-0 flex-1 flex-col" data-slot="settings-main">
          <Suspense
            fallback={
              <div className="empty-state" role="status">
                正在加载设置…
              </div>
            }
          >
            {section === 'profile' && <ProfilePanel />}
            {(section === 'resumes' || section === 'positions') &&
              renderResourcePanel({ section, provider, intent, requestId })}
            {section === 'llm' && <LlmSettingsPanel providerKey={provider} />}
            {section === 'theme' && <ThemePanel />}
          </Suspense>
        </main>
      </div>
    </Dialog>
  )
}
