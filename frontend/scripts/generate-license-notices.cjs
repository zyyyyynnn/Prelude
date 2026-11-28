'use strict'

const fs = require('node:fs')
const path = require('node:path')
const { loadPolicy } = require('./verify-license-policy.cjs')
const { canonicalLicenseLabels, checkContainerBom } = require('./verify-container-licenses.cjs')

const repositoryRoot = path.resolve(__dirname, '../..')
const backendNotice = path.join(repositoryRoot, 'backend/src/main/resources/licenses/NOTICE.txt')
const frontendNotice = path.join(repositoryRoot, 'frontend/public/licenses/NOTICE.txt')
const reportFile = path.join(repositoryRoot, 'frontend/output/license-report.json')
const checkOnly = process.argv.includes('--check')

function flattenComponents(components, output = []) {
  for (const component of components || []) {
    output.push(component)
    flattenComponents(component.components, output)
  }
  return output
}

function readBom(file) {
  const bom = JSON.parse(fs.readFileSync(file, 'utf8'))
  if (bom?.bomFormat !== 'CycloneDX' || !Array.isArray(bom.components)) {
    throw new Error(`${file} is not a CycloneDX SBOM`)
  }
  return bom
}

function licenseLabel(component, policy) {
  return canonicalLicenseLabels(component, policy).join('; ')
}

function identity(component) {
  return (
    component.purl || `${component.group || ''}:${component.name}@${component.version || 'unknown'}`
  )
}

function mergeComponents(runtimeComponents, imageComponents) {
  const merged = new Map()
  for (const component of [...runtimeComponents, ...imageComponents]) {
    if (component.type === 'file' || component.type === 'operating-system') continue
    const key = `${component.name}@${component.version || 'unknown'}`
    if (!merged.has(key)) merged.set(key, component)
  }
  return [...merged.values()]
}

function compareText(left, right) {
  return left < right ? -1 : left > right ? 1 : 0
}

function noticeFor(label, components, policy) {
  const rows = [...components].sort((left, right) => compareText(identity(left), identity(right)))
  const lines = [
    'Prelude third-party notices',
    `Artifact: ${label}`,
    'Generated from the runtime and container CycloneDX SBOMs; do not edit by hand.',
    '',
    'Prelude is distributed under the MIT license; see the repository LICENSE file.',
    '',
    'Components:',
  ]
  for (const component of rows) {
    lines.push(
      `- ${component.name}@${component.version || 'unknown'} — ${licenseLabel(component, policy)} — ${identity(component)}`,
    )
  }
  if (label === 'frontend') {
    lines.push('Additional vendored notices:')
    lines.push('- beautiful-ui.txt')
    lines.push('- paper-design-shaders.txt')
    lines.push('- shadcn-ui.txt')
  }
  return `${lines.join('\n')}\n`
}

function writeOrCheck(file, content) {
  if (checkOnly) {
    const current = fs.existsSync(file) ? fs.readFileSync(file, 'utf8') : null
    if (current !== content) throw new Error(`${path.relative(repositoryRoot, file)} is stale`)
    return
  }
  fs.mkdirSync(path.dirname(file), { recursive: true })
  fs.writeFileSync(file, content)
}

const policy = loadPolicy()
const backendRuntime = readBom(path.join(repositoryRoot, 'backend/target/bom.json'))
const frontendRuntime = readBom(path.join(repositoryRoot, 'frontend/output/sbom.cdx.json'))
const backendImage = readBom(path.join(repositoryRoot, 'backend/target/image-bom.json'))
const frontendImage = readBom(path.join(repositoryRoot, 'frontend/output/image-sbom.cdx.json'))
const licenseErrors = [
  ...checkContainerBom(backendImage, backendRuntime, policy),
  ...checkContainerBom(frontendImage, frontendRuntime, policy),
]
if (licenseErrors.length) {
  throw new Error(
    `License notices require approved component licenses:\n${licenseErrors.join('\n')}`,
  )
}

const backend = mergeComponents(
  flattenComponents(backendRuntime.components),
  flattenComponents(backendImage.components),
)
const frontend = mergeComponents(
  flattenComponents(frontendRuntime.components),
  flattenComponents(frontendImage.components),
)
const backendText = noticeFor('backend', backend, policy)
const frontendText = noticeFor('frontend', frontend, policy)
const report = {
  backend: backend.map((component) => ({
    id: identity(component),
    name: component.name,
    version: component.version,
    licenses: licenseLabel(component, policy),
  })),
  frontend: frontend.map((component) => ({
    id: identity(component),
    name: component.name,
    version: component.version,
    licenses: licenseLabel(component, policy),
  })),
}

writeOrCheck(backendNotice, backendText)
writeOrCheck(frontendNotice, frontendText)
writeOrCheck(reportFile, `${JSON.stringify(report, null, 2)}\n`)
console.log(
  `License notices: ${checkOnly ? 'PASS' : 'generated'} (${backend.length} backend, ${frontend.length} frontend components)`,
)
