'use strict'

const { test } = require('node:test')
const assert = require('node:assert/strict')
const { verifyRetries } = require('./verify-flaky-policy.cjs')

test('accepts an explicit zero retry policy', () => {
  assert.doesNotThrow(() => verifyRetries('export default { retries: 0 }', 'playwright.config.ts'))
})

test('rejects a nonzero retry policy', () => {
  assert.throws(
    () => verifyRetries('export default { retries: 2 }', 'playwright.config.ts'),
    /remain zero/,
  )
})

test('rejects a missing retry policy', () => {
  assert.throws(() => verifyRetries('export default {}', 'playwright.config.ts'), /explicit zero/)
})

test('rejects a computed retry policy', () => {
  assert.throws(
    () =>
      verifyRetries(
        'export default { retries: Number(process.env.RETRIES) }',
        'playwright.config.ts',
      ),
    /explicit zero/,
  )
})
