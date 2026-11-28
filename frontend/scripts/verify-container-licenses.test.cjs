'use strict'

const { test } = require('node:test')
const assert = require('node:assert/strict')
const { canonicalLicenseLabels, checkContainerBom } = require('./verify-container-licenses.cjs')

const policy = {
  allowedLicenses: ['MIT', 'Apache-2.0', 'LGPL-2.1-only', 'GPL-2.0-with-classpath-exception'],
  allowedLicenseNames: [],
  allowedLicenseExceptions: [],
  deniedLicenseFamilies: ['agpl-', 'gpl-'],
  licenseAliases: {},
  licenseSelections: [],
  containerToolchainComponents: [
    { component: 'tool@1.0.0', license: 'Apache-2.0', reason: 'test toolchain' },
  ],
  allowedContainerToolchainLicenses: ['Apache-2.0'],
  exceptions: [],
}
const bom = (components) => ({ bomFormat: 'CycloneDX', components })
const library = (name, version, licenses, purl = `pkg:npm/${name}@${version}`) => ({
  type: 'library',
  name,
  version,
  purl,
  licenses,
})

test('uses runtime license metadata for container application dependencies', () => {
  const image = bom([library('app-lib', '1.0.0', [{ license: { id: 'NOASSERTION' } }])])
  const runtime = bom([library('app-lib', '1.0.0', [{ license: { id: 'MIT' } }])])
  assert.deepEqual(checkContainerBom(image, runtime, policy), [])
})

test('rejects unclassified container application dependencies', () => {
  const image = bom([library('unknown-lib', '1.0.0', [{ license: { id: 'MIT' } }])])
  assert.match(
    checkContainerBom(image, bom([]), policy).join('\n'),
    /absent from the runtime license inventory/,
  )
})

test('rejects known OS licenses outside the explicit container allowlist', () => {
  const gpl = library(
    'os-tool',
    '1.0.0',
    [{ license: { id: 'GPL-3.0-only' } }],
    'pkg:deb/debian/os-tool@1.0.0',
  )
  const artistic = library(
    'os-tool',
    '1.0.0',
    [{ license: { id: 'Artistic-2.0' } }],
    'pkg:deb/debian/os-tool@1.0.0',
  )
  assert.match(
    checkContainerBom(bom([gpl]), bom([]), policy).join('\n'),
    /container license GPL-3.0-only is not allowed/,
  )
  assert.match(
    checkContainerBom(bom([artistic]), bom([]), policy).join('\n'),
    /container license Artistic-2.0 is not allowed/,
  )
  assert.deepEqual(
    checkContainerBom(bom([gpl]), bom([]), {
      ...policy,
      allowedContainerToolchainLicenses: ['GPL-3.0-only'],
    }),
    [],
  )
})

test('rejects network-copyleft OS licenses', () => {
  const agpl = library(
    'os-tool',
    '1.0.0',
    [{ license: { id: 'AGPL-3.0-only' } }],
    'pkg:deb/debian/os-tool@1.0.0',
  )
  assert.match(
    checkContainerBom(bom([agpl]), bom([]), policy).join('\n'),
    /denied container license/,
  )
})

test('rejects denied licenses inside OS license expressions', () => {
  const mixed = library(
    'os-tool',
    '1.0.0',
    [{ expression: 'MIT AND AGPL-3.0-only' }],
    'pkg:deb/debian/os-tool@1.0.0',
  )
  assert.match(
    checkContainerBom(bom([mixed]), bom([]), policy).join('\n'),
    /denied container license/,
  )
})

test('rejects unknown OS toolchain licenses without a policy entry', () => {
  const unknown = library(
    'os-tool',
    '1.0.0',
    [{ license: { id: 'NOASSERTION' } }],
    'pkg:deb/debian/os-tool@1.0.0',
  )
  assert.match(
    checkContainerBom(bom([unknown]), bom([]), policy).join('\n'),
    /container license NOASSERTION is not allowed/,
  )
})

test('rejects scancode unknown OS licenses without a policy entry', () => {
  const unknown = library(
    'os-tool',
    '1.0.0',
    [{ license: { id: 'LicenseRef-scancode-unknown' } }],
    'pkg:deb/debian/os-tool@1.0.0',
  )
  assert.match(
    checkContainerBom(bom([unknown]), bom([]), policy).join('\n'),
    /container license LicenseRef-scancode-unknown is not allowed/,
  )
})

test('accepts explicitly governed toolchain components', () => {
  const image = bom([library('tool', '1.0.0', [{ license: { id: 'NOASSERTION' } }])])
  assert.deepEqual(checkContainerBom(image, bom([]), policy), [])
})

test('uses canonical licenses for ambiguous OS metadata', () => {
  const image = bom([
    library(
      'os-tool',
      '1.0.0',
      [
        { license: { id: 'GPL' } },
        { license: { id: 'GPL-3.0-only' } },
        { license: { id: 'public-domain' } },
      ],
      'pkg:deb/debian/os-tool@1.0.0',
    ),
  ])
  const governed = {
    ...policy,
    allowedContainerToolchainLicenses: ['GPL-3.0-only', 'Public-Domain'],
    licenseAliases: { 'public-domain': 'Public-Domain' },
  }
  assert.deepEqual(checkContainerBom(image, bom([]), governed), [])
  assert.deepEqual(canonicalLicenseLabels(image.components[0], governed), [
    'GPL-3.0-only',
    'Public-Domain',
  ])
})

test('requires every canonical toolchain license to be allowed', () => {
  const governed = {
    ...policy,
    containerToolchainComponents: [
      {
        component: 'tool@1.0.0',
        licenses: ['Apache-2.0', 'MIT'],
        reason: 'test multi-license toolchain',
      },
    ],
  }
  const image = bom([library('tool', '1.0.0', [{ license: { id: 'NOASSERTION' } }])])
  assert.match(
    checkContainerBom(image, bom([]), governed).join('\n'),
    /container toolchain license policy is not allowed/,
  )
})

test('does not let a toolchain mapping hide a denied license', () => {
  const governed = {
    ...policy,
    containerToolchainComponents: [
      { component: 'tool@1.0.0', license: 'Apache-2.0', reason: 'test toolchain' },
    ],
  }
  const image = bom([
    library('tool', '1.0.0', [{ license: { id: 'AGPL-3.0-only' } }], 'pkg:deb/debian/tool@1.0.0'),
  ])
  assert.match(checkContainerBom(image, bom([]), governed).join('\n'), /denied container license/)
})
