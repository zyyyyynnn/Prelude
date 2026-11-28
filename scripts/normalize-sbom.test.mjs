import { test } from 'node:test'
import assert from 'node:assert/strict'
import { normalizeSbom } from './normalize-sbom.mjs'

test('removes volatile CycloneDX fields and is idempotent', () => {
  const source = JSON.stringify({
    bomFormat: 'CycloneDX',
    serialNumber: 'urn:uuid:first',
    metadata: { timestamp: '2026-11-28T00:00:00Z', component: { name: 'app' } },
    components: [],
  })
  const normalized = normalizeSbom(source)
  assert.equal(normalized, normalizeSbom(normalized))
  assert.deepEqual(JSON.parse(normalized), {
    bomFormat: 'CycloneDX',
    metadata: { component: { name: 'app' } },
    components: [],
  })
})
