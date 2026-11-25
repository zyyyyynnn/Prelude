'use strict'

const { test } = require('node:test')
const assert = require('node:assert/strict')
const {
  checkBom,
  evaluateExpression,
  loadPolicy,
  validateExceptions,
  validateLicenseSelections,
} = require('./verify-license-policy.cjs')

const policy = loadPolicy()
const today = new Date('2026-11-25T00:00:00Z')

function bom(licenses, purl = 'pkg:npm/example@1.0.0') {
  return {
    bomFormat: 'CycloneDX',
    components: [{ type: 'library', name: 'example', version: '1.0.0', purl, licenses }],
  }
}

function license(value) {
  return value === 'NOASSERTION' ? {} : { license: { id: value } }
}

test('accepts explicit permissive and weak-copyleft licenses', () => {
  assert.deepEqual(checkBom(bom([license('Apache-2.0')]), policy, today), [])
  assert.deepEqual(checkBom(bom([license('EPL-2.0')]), policy, today), [])
})

test('uses OR semantics only inside SPDX expressions', () => {
  assert.deepEqual(
    checkBom(bom([{ expression: '(MIT OR GPL-3.0-only) AND BSD-3-Clause' }]), policy, today),
    [],
  )
  assert.deepEqual(checkBom(bom([{ expression: 'LicenseRef-private OR MIT' }]), policy, today), [])
  assert.match(
    checkBom(bom([{ expression: 'MIT AND GPL-3.0-only' }]), policy, today).join('\n'),
    /denied license GPL-3.0-only/,
  )
})

test('requires every license array entry to be allowed or excepted', () => {
  assert.deepEqual(checkBom(bom([license('AL 2.0'), license('LGPL-2.1-only')]), policy, today), [])
  assert.match(
    checkBom(bom([license('MIT'), license('GPL-3.0-only')]), policy, today).join('\n'),
    /denied license GPL-3.0-only/,
  )
  assert.match(
    checkBom(bom([license('GPL-3.0-only'), license('MIT')]), policy, today).join('\n'),
    /denied license GPL-3.0-only/,
  )
  assert.match(
    checkBom(bom([license('MIT'), license('LicenseRef-private')]), policy, today).join('\n'),
    /unknown license LicenseRef-private/,
  )
})

test('selects an allowed license from an explicitly versioned multi-license artifact', () => {
  const purl = 'pkg:maven/com.rabbitmq/amqp-client@5.37.1'
  const withSelection = {
    ...policy,
    licenseSelections: [
      {
        component: purl,
        license: 'AL 2.0',
        reason: 'Select the permissive option offered by the publisher',
      },
    ],
  }
  assert.deepEqual(
    checkBom(
      bom([license('AL 2.0'), license('GPL-2.0-only'), license('MPL-2.0')], purl),
      withSelection,
      today,
    ),
    [],
  )
  assert.match(
    checkBom(bom([license('GPL-2.0-only'), license('MPL-2.0')], purl), withSelection, today).join(
      '\n',
    ),
    /selected license AL 2.0 is not declared/,
  )
  assert.match(
    checkBom(
      bom(
        [license('AL 2.0'), license('GPL-2.0-only')],
        'pkg:maven/com.rabbitmq/amqp-client@5.38.0',
      ),
      withSelection,
      today,
    ).join('\n'),
    /denied license GPL-2.0-only/,
  )
})

test('checks nested components instead of trusting the top-level package', () => {
  const nested = bom([license('MIT')])
  nested.components[0].components = [
    { type: 'library', name: 'nested', version: '1.0.0', licenses: [license('AGPL-3.0-only')] },
  ]
  assert.match(checkBom(nested, policy, today).join('\n'), /nested@1.0.0: denied license AGPL/)
})

test('blocks missing and denied licenses', () => {
  assert.match(checkBom(bom([]), policy, today).join('\n'), /unknown license NOASSERTION/)
  assert.match(
    checkBom(bom([license('AGPL-3.0-only')]), policy, today).join('\n'),
    /denied license AGPL-3.0-only/,
  )
})

test('requires exact named exceptions with a current expiration', () => {
  const exception = {
    component: 'pkg:npm/example@1.0.0',
    license: 'LicenseRef-private',
    reason: 'Legal review completed for this runtime dependency',
    approvedBy: 'security-owner',
    expiresOn: '2027-01-31',
  }
  const withException = { ...policy, exceptions: [exception] }
  assert.deepEqual(checkBom(bom([license('LicenseRef-private')]), withException, today), [])
  assert.match(
    checkBom(
      bom([license('LicenseRef-private')]),
      withException,
      new Date('2027-02-01T00:00:00Z'),
    ).join('\n'),
    /unknown license LicenseRef-private/,
  )
  assert.match(
    checkBom(
      bom([license('LicenseRef-private'), license('LicenseRef-other')]),
      withException,
      today,
    ).join('\n'),
    /unknown license LicenseRef-other/,
  )
})

test('rejects wildcard, unexplained, and duplicate license selections', () => {
  assert.throws(
    () => validateLicenseSelections([{ component: '*', license: 'MIT', reason: 'review' }]),
    /wildcards are forbidden/,
  )
  assert.throws(
    () =>
      validateLicenseSelections([
        { component: 'pkg:maven/example@1.0.0', license: 'MIT', reason: 'review' },
        { component: 'pkg:maven/example@1.0.0', license: 'MIT', reason: 'review' },
      ]),
    /duplicated/,
  )
  assert.throws(
    () =>
      validateLicenseSelections([
        { component: 'pkg:maven/example@1.0.0', license: 'MIT', reason: '' },
      ]),
    /requires a reason/,
  )
})

test('rejects blanket, unapproved, and undated exceptions', () => {
  assert.throws(
    () => validateExceptions([{ component: '*', license: '*', expiresOn: '2027-01-31' }]),
    /wildcards are forbidden/,
  )
  assert.throws(
    () =>
      validateExceptions([
        { component: 'pkg:npm/example@1.0.0', license: 'MIT', expiresOn: '2027-01-31' },
      ]),
    /reason and approvedBy/,
  )
  assert.throws(
    () =>
      validateExceptions([
        {
          component: 'pkg:npm/example@1.0.0',
          license: 'MIT',
          reason: 'review',
          approvedBy: 'owner',
        },
      ]),
    /expiresOn/,
  )
  assert.throws(
    () =>
      validateExceptions([
        {
          component: 'pkg:npm/example@1.0.0',
          license: 'MIT',
          reason: 'review',
          approvedBy: 'owner',
          expiresOn: '2027-02-30',
        },
      ]),
    /invalid expiration date/,
  )
})

test('rejects unknown SPDX exceptions and malformed expressions', () => {
  assert.equal(evaluateExpression('Apache-2.0 WITH LicenseRef-custom', policy).state, 'unknown')
  assert.equal(evaluateExpression('MIT AND', policy).state, 'unknown')
  assert.equal(evaluateExpression('(MIT OR Apache-2.0', policy).state, 'unknown')
})
