export type LlmProviderResponse = {
  providerKey: string
  displayName: string
  availableModels: string[]
  enabled: number
}

export type LlmProviderOption = {
  providerKey: string
  displayName: string
  models: string[]
}

export type LlmConfigPayload = {
  providerKey: string
  baseUrl?: string
  model: string
  apiKey?: string
  maxTokens?: number
  thinkingDepth?: string | null
}

export type LlmConfigResponse = {
  providerKey: string
  baseUrl: string | null
  model: string
  hasApiKey: boolean
  apiKeyMasked: string | null
  maxTokens: number | null
  thinkingDepth: string | null
}

export type LlmModelDiscoveryPayload = {
  providerKey: string
  baseUrl: string
  apiKey?: string
}

export type LlmModelDiscoveryResponse = {
  providerKey: string
  baseUrl: string
  models: string[]
}

export type LlmConfigTestPayload = {
  providerKey?: string
  baseUrl?: string
  model?: string
  apiKey?: string
  maxTokens?: number
  thinkingDepth?: string | null
}

export type LlmConfigTestResponse = {
  providerKey: string
  model: string
  ok: boolean
  message: string
}

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
