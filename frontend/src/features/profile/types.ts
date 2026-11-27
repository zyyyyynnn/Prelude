export type ThemePreference = 'light' | 'dark' | 'system'

export type UserProfilePayload = {
  username?: string
  email?: string
  oldPassword?: string
  newPassword?: string
  themePreference?: ThemePreference
  expectedRevision: number
  operationId: string
}

export type UserProfileResponse = {
  accountId: number
  username?: string
  email?: string
  avatarUrl?: string
  themePreference?: ThemePreference
  revision: number
}
