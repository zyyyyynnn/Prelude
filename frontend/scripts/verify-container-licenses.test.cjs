'use strict'

const { test } = require('node:test')
const assert = require('node:assert/strict')
const { checkContainerBom } = require('./verify-container-licenses.cjs')

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

test('allows governed OS packages but rejects network-copyleft licenses', () => {
  const gpl = library(
    'os-tool',
    '1.0.0',
    [{ license: { id: 'GPL-3.0-only' } }],
    'pkg:deb/debian/os-tool@1.0.0',
  )
  const agpl = library(
    'os-tool',
    '1.0.0',
    [{ license: { id: 'AGPL-3.0-only' } }],
    'pkg:deb/debian/os-tool@1.0.0',
  )
  assert.deepEqual(checkContainerBom(bom([gpl]), bom([]), policy), [])
  assert.match(
    checkContainerBom(bom([agpl]), bom([]), policy).join('\n'),
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
    /unknown container toolchain license/,
  )
})

test('accepts explicitly governed toolchain components', () => {
  const image = bom([library('tool', '1.0.0', [{ license: { id: 'NOASSERTION' } }])])
  assert.deepEqual(checkContainerBom(image, bom([]), policy), [])
})
