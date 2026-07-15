import assert from 'node:assert/strict'
import test from 'node:test'

import { mapProviderResponses } from '../../src/features/settings/model/provider.ts'

void test('maps the backend provider response without inventing display metadata', () => {
  assert.deepEqual(
    mapProviderResponses([
      {
        providerKey: 'openai-responses',
        displayName: 'OpenAI Responses',
        availableModels: ['gpt-4o-mini'],
        enabled: 1,
      },
    ]),
    [
      {
        providerKey: 'openai-responses',
        displayName: 'OpenAI Responses',
        models: ['gpt-4o-mini'],
      },
    ],
  )
})

void test('does not revive legacy models or providerName aliases', () => {
  const legacyPayload = [
    {
      providerKey: 'legacy-provider',
      providerName: 'Legacy Name',
      models: ['legacy-model'],
      enabled: 1,
    },
  ] as never

  assert.throws(() => mapProviderResponses(legacyPayload), /provider response/i)
})

void test('requires the enabled field and string-only availableModels', () => {
  const invalidPayload = [
    {
      providerKey: 'anthropic-messages',
      displayName: 'Anthropic Messages',
      availableModels: ['gpt-4o-mini', 42],
    },
  ] as never

  assert.throws(() => mapProviderResponses(invalidPayload), /provider response/i)
})
