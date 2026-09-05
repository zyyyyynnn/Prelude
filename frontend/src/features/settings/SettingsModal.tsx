import {
  BriefcaseBusiness,
  FileText,
  LogOut,
  Palette,
  SquareTerminal,
  UserRound,
} from 'lucide-react'
import type { ReactNode } from 'react'
import { useNavigate } from 'react-router'
import { useAuth } from '@/features/auth'
import { ResumeManagementPanel } from '@/features/resume'
import { PositionManagementPanel } from '@/features/template'
import { Modal } from '@/shared/ui'
import { LlmSettingsPanel } from './LlmSettingsPanel'
import { ProfilePanel } from './ProfilePanel'
import type { SettingsIntent, SettingsSection } from './settings-context'
import { ThemePanel } from './ThemePanel'

const titles: Record<SettingsSection, string> = {
  profile: '账号资料',
  resumes: '简历管理',
  positions: '岗位管理',
  llm: '模型管理',
  theme: '主题',
}

export function SettingsModal({
  open,
  section,
  provider,
  intent,
  requestId,
  onSectionChange,
  onOpenChange,
}: {
  open: boolean
  section: SettingsSection
  provider?: string
  intent?: SettingsIntent
  requestId: number
  onSectionChange: (section: SettingsSection) => void
  onOpenChange: (value: boolean) => void
}) {
  const auth = useAuth()
  const navigate = useNavigate()
  return (
    <Modal
      open={open}
      onOpenChange={onOpenChange}
      title="全局设置"
      className="settings-dialog"
      showClose={false}
    >
      <div className="settings-layout">
        <aside className="settings-sidebar">
          <nav className="sidebar-menu" aria-label="设置分类">
            <TabButton
              active={section === 'profile'}
              onClick={() => onSectionChange('profile')}
              icon={<UserRound aria-hidden="true" />}
            >
              账号资料
            </TabButton>
            <TabButton
              active={section === 'resumes'}
              onClick={() => onSectionChange('resumes')}
              icon={<FileText aria-hidden="true" />}
            >
              简历管理
            </TabButton>
            <TabButton
              active={section === 'positions'}
              onClick={() => onSectionChange('positions')}
              icon={<BriefcaseBusiness aria-hidden="true" />}
            >
              岗位管理
            </TabButton>
            <TabButton
              active={section === 'llm'}
              onClick={() => onSectionChange('llm')}
              icon={<SquareTerminal aria-hidden="true" />}
            >
              模型管理
            </TabButton>
            <TabButton
              active={section === 'theme'}
              onClick={() => onSectionChange('theme')}
              icon={<Palette aria-hidden="true" />}
            >
              主题
            </TabButton>
          </nav>
          <div className="sidebar-footer">
            <button
              className="settings-sidebar__item settings-sidebar__item--danger ui-action ui-action-danger"
              onClick={() => {
                onOpenChange(false)
                void auth.signOut().then(() => navigate('/login'))
              }}
            >
              <LogOut aria-hidden="true" />
              退出登录
            </button>
          </div>
        </aside>
        <main className="settings-main">
          <header className="settings-header">
            <h2 className="settings-header__title">{titles[section]}</h2>
          </header>
          <div className="settings-content scrollable">
            {section === 'profile' && <ProfilePanel />}
            {section === 'resumes' && (
              <ResumeManagementPanel
                uploadRequest={intent === 'upload-resume' ? requestId : undefined}
              />
            )}
            {section === 'positions' && (
              <PositionManagementPanel
                key={intent === 'create-position' ? `create-${requestId}` : 'manage'}
              />
            )}
            {section === 'llm' && <LlmSettingsPanel providerKey={provider} />}
            {section === 'theme' && <ThemePanel />}
          </div>
        </main>
      </div>
    </Modal>
  )
}

function TabButton({
  active,
  onClick,
  icon,
  children,
}: {
  active: boolean
  onClick: () => void
  icon: ReactNode
  children: ReactNode
}) {
  return (
    <button
      className={`settings-sidebar__item ui-action ui-action-nav${active ? ' is-active' : ''}`}
      aria-current={active ? 'page' : undefined}
      onClick={onClick}
    >
      {icon}
      {children}
    </button>
  )
}
