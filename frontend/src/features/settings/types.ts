/** Reasoning levels come from the backend capability catalog — never guessed. */
export type ReasoningLevel = 'AUTO' | 'LOW' | 'MEDIUM' | 'HIGH'

export type ModelCapabilityResponse = {
  provider: string
  model: string
  reasoning: boolean
  structuredOutput: boolean
  toolCalling: boolean
  streaming: boolean
  vision: boolean
  multilingual: boolean
  longContext: boolean
  embedding: boolean
  nativeRealtimeVoice: boolean
  supportedReasoningLevels: ReasoningLevel[]
}

export type LlmProviderResponse = {
  providerKey: string
  displayName: string
  customEndpoint: boolean
  models: ModelCapabilityResponse[]
}

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
  capability: ModelCapabilityResponse
}

export type LlmModelDiscoveryPayload = {
  provider: string
  baseUrl: string
  apiKey?: string
}

export type LlmModelDiscoveryResponse = {
  baseUrl: string
  models: ModelCapabilityResponse[]
}

export type LlmCapabilityDiscoveryPayload = {
  provider: string
  baseUrl: string
  apiKey?: string
  model: string
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
