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

/** Reasoning levels come from the backend capability catalog — never guessed. */
export type ReasoningLevel = 'AUTO' | 'LOW' | 'MEDIUM' | 'HIGH'

export type LlmConfigPayload = {
  provider: string
  model: string
  customEndpointUrl?: string
  apiKey?: string
  reasoningLevel?: ReasoningLevel | null
  fallbackModels?: string[]
}

export type LlmConfigResponse = {
  provider: string
  model: string
  customEndpointUrl: string | null
  hasApiKey: boolean
  apiKeyMasked: string | null
  reasoningLevel: ReasoningLevel
  fallbackModels: string[]
  reasoningSupported: boolean
  supportedReasoningLevels: ReasoningLevel[]
}

export type LlmModelDiscoveryPayload = {
  baseUrl: string
  apiKey?: string
}

export type LlmModelDiscoveryResponse = {
  baseUrl: string
  models: string[]
}

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

export type ThemePreference = 'light' | 'dark' | 'system'
