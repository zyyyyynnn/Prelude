import { createContext, useContext } from 'react'

export type SettingsSection = 'profile' | 'resumes' | 'positions' | 'llm' | 'theme'
export type SettingsIntent = 'upload-resume' | 'create-position'

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
