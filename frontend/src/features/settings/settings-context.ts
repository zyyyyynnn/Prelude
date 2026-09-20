import type { LucideIcon } from 'lucide-react'
import { BriefcaseBusiness, FileText, Palette, SquareTerminal, UserRound } from 'lucide-react'
import { createContext, useContext } from 'react'

export type SettingsSection = 'profile' | 'resumes' | 'positions' | 'llm' | 'theme'
export type SettingsIntent = 'upload-resume' | 'create-position'

/** The one source for the settings sections: order, title and icon. The navigation
 *  column, every panel heading and the component gallery all read this list. */
export const sections: { key: SettingsSection; title: string; icon: LucideIcon }[] = [
  { key: 'profile', title: '账号资料', icon: UserRound },
  { key: 'resumes', title: '简历管理', icon: FileText },
  { key: 'positions', title: '岗位管理', icon: BriefcaseBusiness },
  { key: 'llm', title: '模型管理', icon: SquareTerminal },
  { key: 'theme', title: '主题', icon: Palette },
]

export const sectionTitles = Object.fromEntries(
  sections.map(({ key, title }) => [key, title]),
) as Record<SettingsSection, string>

export type SettingsOpenRequest = {
  section?: SettingsSection
  provider?: string
  intent?: SettingsIntent
}

export type SettingsRequest = SettingsOpenRequest & {
  section: SettingsSection
  requestId: number
}

export type SettingsContextValue = {
  openSettings: (request?: SettingsOpenRequest) => void
}

export const SettingsContext = createContext<SettingsContextValue | null>(null)

export function useSettings() {
  const value = useContext(SettingsContext)
  if (!value) throw new Error('useSettings must be used inside SettingsProvider')
  return value
}
