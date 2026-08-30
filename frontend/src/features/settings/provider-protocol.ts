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
    modelDiscovery: false,
    placeholder: '例如：https://api.anthropic.com/v1',
  },
} as const

export type CustomProviderKey = keyof typeof customProviderProtocol

export function isCustomProvider(providerKey: string): providerKey is CustomProviderKey {
  return providerKey in customProviderProtocol
}

export function normalizeCustomBaseUrl(baseUrl: string, providerKey: string): string {
  let value = (baseUrl || '').trim().replace(/\/+$/, '')
  if (!isCustomProvider(providerKey)) {
    return value
  }
  const suffix = customProviderProtocol[providerKey].endpointSuffix
  if (value.endsWith(suffix)) {
    value = value.slice(0, -suffix.length)
  }
  return value.replace(/\/+$/, '')
}

export function getCustomProviderMeta(providerKey: string) {
  return isCustomProvider(providerKey) ? customProviderProtocol[providerKey] : null
}
