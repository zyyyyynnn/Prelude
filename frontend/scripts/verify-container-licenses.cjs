'use strict'

const fs = require('node:fs')
const {
  checkBom,
  componentKey,
  evaluateExpression,
  loadPolicy,
} = require('./verify-license-policy.cjs')

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

function runtimeComponentIndex(components, policy) {
  const byKey = new Map()
  const byMavenArtifact = new Map()
  const aliases = new Map(
    (policy.containerRuntimeAliases || []).map((alias) => [alias.image, alias.runtime]),
  )
  for (const component of components) {
    const key = componentKey(component)
    if (!key) continue
    byKey.set(key, component)
    if (!key.startsWith('pkg:maven/')) continue
    const version = key.slice(key.lastIndexOf('@') + 1)
    const artifact = `${component.name}@${version}`
    const matches = byMavenArtifact.get(artifact) || new Set()
    matches.add(key)
    byMavenArtifact.set(artifact, matches)
  }
  return { byKey, byMavenArtifact, aliases }
}

function resolveRuntimeComponent(index, component) {
  const key = componentKey(component)
  if (!key) return undefined
  const direct = index.byKey.get(key)
  if (direct) return direct
  if (!key.startsWith('pkg:maven/')) return undefined
  const aliased = index.aliases.get(key)
  if (aliased) return index.byKey.get(aliased)
  const version = key.slice(key.lastIndexOf('@') + 1)
  const matches = index.byMavenArtifact.get(`${component.name}@${version}`)
  if (matches?.size !== 1) return undefined
  const runtimeKey = [...matches][0]
  const imageNamespace = key.slice('pkg:maven/'.length, key.lastIndexOf('/'))
  const runtimeNamespace = runtimeKey.slice('pkg:maven/'.length, runtimeKey.lastIndexOf('/'))
  if (
    imageNamespace !== component.name &&
    imageNamespace !== `${runtimeNamespace}.${component.name}`
  ) {
    return undefined
  }
  return index.byKey.get(runtimeKey)
}

function normalized(value) {
  return value.trim().toLowerCase()
}

function toolchainEntry(component, policy) {
  const key = componentKey(component)
  return key
    ? policy.containerToolchainComponents.find((item) => item.component === key)
    : undefined
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
    errors.push(`${componentKey(component)}: container toolchain license policy is not allowed`)
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
  const runtimeIndex = runtimeComponentIndex(flatten(runtimeBom.components), policy)
  const errors = []
  for (const component of flatten(imageBom.components)) {
    if (['file', 'operating-system'].includes(component.type)) continue
    const key = componentKey(component)
    if (!key) {
      errors.push(
        `${component.name}@${component.version || 'unknown'}: component identity requires a valid purl`,
      )
      continue
    }
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
      errors.push(`${key}: denied container license ${denied}`)
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
        errors.push(`${key}: container license ${rejected.join(', ')} is not allowed`)
      }
      continue
    }
    const governed = resolveRuntimeComponent(runtimeIndex, component)
    if (governed) {
      errors.push(...checkBom({ bomFormat: 'CycloneDX', components: [governed] }, policy, today))
      continue
    }
    if (!toolchainEntry(component, policy)) {
      errors.push(
        `${key}: container application dependency is absent from the runtime license inventory`,
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

module.exports = {
  canonicalLicenseLabels,
  checkContainerBom,
  resolveRuntimeComponent,
  runtimeComponentIndex,
}
