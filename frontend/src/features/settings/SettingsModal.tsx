import { SettingsNavigation } from './components/settings-navigation'
import { Suspense, useRef, useState, type ReactNode } from 'react'
import { useNavigate } from 'react-router'
import { useAuth } from '@/features/auth'
import { LlmSettingsPanel } from './components/LlmSettingsPanel'
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
import { RiLogoutBoxLine } from '@remixicon/react'
import { Dialog, HiddenFileInput, Icon, LoadingState } from '@/shared/ui'

type ResourcePanelRenderer = (request: SettingsRequest) => ReactNode

export function SettingsProvider({
  children,
  renderResourcePanel,
  renderProfilePanel,
}: {
  children: ReactNode
  renderResourcePanel: ResourcePanelRenderer
  renderProfilePanel: (title: string) => ReactNode
}) {
  const [open, setOpen] = useState(false)
  const [request, setRequest] = useState<SettingsRequest>({ section: 'profile', requestId: 0 })
  const nextRequestId = useRef(0)
  const resumeInput = useRef<HTMLInputElement>(null)

  function openSettings(next: SettingsOpenRequest = {}) {
    if (next.intent === 'upload-resume') {
      setRequest({
        section: 'resumes',
        requestId: ++nextRequestId.current,
      })
      setOpen(true)
      resumeInput.current?.click()
      return
    }
    setRequest({
      section: next.section ?? 'profile',
      provider: next.provider,
      intent: next.intent,
      requestId: ++nextRequestId.current,
    })
    setOpen(true)
  }

  return (
    <SettingsContext.Provider value={{ openSettings }}>
      <HiddenFileInput
        id="settings-resume-intake"
        label="选择简历上传文件"
        accept="application/pdf"
        hiddenFromAccessibility
        inputRef={resumeInput}
        onFiles={(files) => {
          const uploadFile = files[0]
          if (!uploadFile) return
          setRequest({ section: 'resumes', requestId: ++nextRequestId.current, uploadFile })
          setOpen(true)
        }}
      />
      {children}
      {open && (
        <SettingsModal
          open={open}
          section={request.section}
          provider={request.provider}
          intent={request.intent}
          requestId={request.requestId}
          uploadFile={request.uploadFile}
          onSectionChange={(section) =>
            setRequest((current) => ({
              ...current,
              section,
              intent: undefined,
              uploadFile: undefined,
            }))
          }
          onOpenChange={setOpen}
          renderResourcePanel={renderResourcePanel}
          renderProfilePanel={renderProfilePanel}
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
  uploadFile,
  onSectionChange,
  onOpenChange,
  renderResourcePanel,
  renderProfilePanel,
}: {
  open: boolean
  section: SettingsSection
  provider?: string
  intent?: SettingsIntent
  requestId: number
  uploadFile?: File
  onSectionChange: (section: SettingsSection) => void
  onOpenChange: (value: boolean) => void
  renderResourcePanel: ResourcePanelRenderer
  renderProfilePanel: (title: string) => ReactNode
}) {
  const auth = useAuth()
  const navigate = useNavigate()
  return (
    <Dialog open={open} onOpenChange={onOpenChange} title="全局设置" layout="workspace">
      <div className="flex size-full min-h-0 overflow-hidden rounded-lg elevated-modal">
        <SettingsNavigation
          items={sections.map(({ key, icon: Glyph }) => ({
            key,
            label: sectionTitles[key],
            icon: <Icon as={Glyph} />,
          }))}
          active={section}
          onSelect={onSectionChange}
          danger={{
            label: '退出登录',
            icon: <Icon as={RiLogoutBoxLine} aria-hidden="true" />,
            onSelect: () => {
              onOpenChange(false)
              void auth.signOut().then(() => navigate('/login'))
            },
          }}
        />
        <main className="flex min-w-0 flex-1 flex-col" data-slot="settings-main">
          <Suspense fallback={<LoadingState message="正在加载设置…" />}>
            {section === 'profile' && renderProfilePanel(sectionTitles.profile)}
            {(section === 'resumes' || section === 'positions') &&
              renderResourcePanel({ section, provider, intent, requestId, uploadFile })}
            {section === 'llm' && <LlmSettingsPanel providerKey={provider} />}
            {section === 'theme' && <ThemePanel key={auth.accountId} />}
          </Suspense>
        </main>
      </div>
    </Dialog>
  )
}
