'use strict'

const fs = require('node:fs')
const { checkBom, evaluateExpression, loadPolicy } = require('./verify-license-policy.cjs')

const deniedContainerFamilies = [
  'agpl-',
  'busl-',
  'commons-clause-',
  'elastic-',
  'polyform-',
  'sspl-',
]

function flatten(components, output = []) {
  for (const component of components || []) {
    output.push(component)
    flatten(component.components, output)
  }
  return output
}

function artifactKey(component) {
  return `${component.name}@${component.version || 'unknown'}`
}

function normalized(value) {
  return value.trim().toLowerCase()
}

function toolchainEntry(component, policy) {
  return policy.containerToolchainComponents.find(
    (item) => item.component === artifactKey(component),
  )
}

function entryLicenses(entry) {
  return entry.licenses || (entry.license ? [entry.license] : [])
}

function canonicalLicenseLabels(component, policy) {
  const entry = toolchainEntry(component, policy)
  if (entry) return entryLicenses(entry)
  const labels = licenseLabels(component).map(
    (label) => policy.licenseAliases[normalized(label)] || label,
  )
  let filtered = labels
  for (const family of ['GPL', 'LGPL', 'GFDL']) {
    if (filtered.includes(family) && filtered.some((label) => label.startsWith(`${family}-`))) {
      filtered = filtered.filter((label) => label !== family)
    }
  }
  return [...new Set(filtered)]
}

function allowedContainerLicense(label, policy) {
  const alias = policy.licenseAliases[normalized(label)] || label
  if (
    policy.allowedContainerToolchainLicenses.some(
      (allowed) => normalized(allowed) === normalized(alias),
    )
  ) {
    return true
  }
  return (
    evaluateExpression(alias, {
      ...policy,
      allowedLicenses: policy.allowedContainerToolchainLicenses,
      allowedLicenseNames: [],
      deniedLicenseFamilies: deniedContainerFamilies,
    }).state === 'allowed'
  )
}

function checkToolchainEntry(component, policy, errors) {
  const entry = toolchainEntry(component, policy)
  if (!entry) return false
  const licenses = entryLicenses(entry)
  if (!licenses.length || licenses.some((license) => !allowedContainerLicense(license, policy))) {
    errors.push(`${artifactKey(component)}: container toolchain license policy is not allowed`)
  }
  return true
}

function licenseLabels(component) {
  const entries =
    Array.isArray(component.licenses) && component.licenses.length ? component.licenses : [null]
  return entries.map(
    (entry) => entry?.expression || entry?.license?.id || entry?.license?.name || 'NOASSERTION',
  )
}

function checkContainerBom(imageBom, runtimeBom, policy, today = new Date()) {
  if (imageBom?.bomFormat !== 'CycloneDX' || runtimeBom?.bomFormat !== 'CycloneDX') {
    throw new Error('container license policy requires CycloneDX JSON SBOMs')
  }
  const runtime = new Map(
    flatten(runtimeBom.components).map((component) => [artifactKey(component), component]),
  )
  const errors = []
  for (const component of flatten(imageBom.components)) {
    if (['file', 'operating-system'].includes(component.type)) continue
    const labels = licenseLabels(component)
    const denied = labels.find((label) => {
      const tokens = label.match(/\(|\)|[^\s()]+/g) || []
      return tokens.some(
        (token) =>
          !['AND', 'OR', 'WITH'].includes(token) &&
          deniedContainerFamilies.some((family) => token.toLowerCase().startsWith(family)),
      )
    })
    if (denied) {
      errors.push(`${artifactKey(component)}: denied container license ${denied}`)
      continue
    }
    if (
      component.purl?.startsWith('pkg:deb/') ||
      component.purl?.startsWith('pkg:apk/') ||
      component.purl?.startsWith('pkg:golang/')
    ) {
      if (checkToolchainEntry(component, policy, errors)) continue
      const rejected = canonicalLicenseLabels(component, policy).filter(
        (label) => !allowedContainerLicense(label, policy),
      )
      if (rejected.length) {
        errors.push(
          `${artifactKey(component)}: container license ${rejected.join(', ')} is not allowed`,
        )
      }
      continue
    }
    const key = artifactKey(component)
    const governed = runtime.get(key)
    if (governed) {
      errors.push(...checkBom({ bomFormat: 'CycloneDX', components: [governed] }, policy, today))
      continue
    }
    if (!toolchainEntry(component, policy)) {
      errors.push(
        `${artifactKey(component)}: container application dependency is absent from the runtime license inventory`,
      )
      continue
    }
    checkToolchainEntry(component, policy, errors)
  }
  return errors
}

function main() {
  const [backendRuntime, frontendRuntime, backendImage, frontendImage] = process.argv.slice(2)
  if (!backendRuntime || !frontendRuntime || !backendImage || !frontendImage) {
    throw new Error(
      'Usage: node scripts/verify-container-licenses.cjs <backend-runtime> <frontend-runtime> <backend-image> <frontend-image>',
    )
  }
  const policy = loadPolicy()
  const errors = [
    ...checkContainerBom(
      JSON.parse(fs.readFileSync(backendImage, 'utf8')),
      JSON.parse(fs.readFileSync(backendRuntime, 'utf8')),
      policy,
    ),
    ...checkContainerBom(
      JSON.parse(fs.readFileSync(frontendImage, 'utf8')),
      JSON.parse(fs.readFileSync(frontendRuntime, 'utf8')),
      policy,
    ),
  ]
  if (errors.length) {
    console.error('Container license policy: FAIL')
    for (const error of errors) console.error(`  - ${error}`)
    process.exit(1)
  }
  console.log('Container license policy: PASS (2 image SBOMs)')
}

if (require.main === module) main()

module.exports = { canonicalLicenseLabels, checkContainerBom }
