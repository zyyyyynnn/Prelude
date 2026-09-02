/** Protocol-level endpoint conventions only. Model IDs/capabilities come from the backend. */
export const customProviderProtocol = {
  'openai-responses': {
    endpointSuffix: '/responses',
    modelDiscovery: true,
    placeholder: '例如：https://api.openai.com/v1',
  },
  'openai-chat-completions': {
    endpointSuffix: '/chat/completions',
    modelDiscovery: true,
    placeholder: '例如：https://api.openai.com/v1',
  },
  'anthropic-messages': {
    endpointSuffix: '/messages',
    modelDiscovery: true,
    placeholder: '例如：https://api.anthropic.com/v1',
  },
} as const

export type CustomProviderKey = keyof typeof customProviderProtocol

export function isCustomProvider(providerKey: string): providerKey is CustomProviderKey {
  return providerKey in customProviderProtocol
}

export function normalizeCustomBaseUrl(baseUrl: string, providerKey: string): string {
  const value = (baseUrl || '').trim().replace(/\/+$/, '')
  if (!isCustomProvider(providerKey)) {
    return value
  }
  const suffix = customProviderProtocol[providerKey].endpointSuffix
  if (value.endsWith(suffix)) {
    return value.slice(0, -suffix.length).replace(/\/+$/, '')
  }
  return value
}

export function getCustomProviderMeta(providerKey: string) {
  return isCustomProvider(providerKey) ? customProviderProtocol[providerKey] : null
}
