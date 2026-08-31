/**
 * The custom-endpoint provider is one OpenAI-compatible protocol now. The
 * backend capability catalog decides which reasoning levels exist; this file
 * only knows the endpoint conventions for form hints.
 */
export const customProviderProtocol = {
  'openai-compatible': {
    endpointSuffix: '/chat/completions',
    modelDiscovery: true,
    placeholder: '例如：https://api.openai.com/v1',
  },
} as const

export type CustomProviderKey = keyof typeof customProviderProtocol

export function isCustomProvider(providerKey: string): providerKey is CustomProviderKey {
  return providerKey === 'openai-compatible'
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
