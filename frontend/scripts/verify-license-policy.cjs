'use strict'

const fs = require('node:fs')
const path = require('node:path')

const policyPath = path.join(__dirname, 'license-policy.json')

function normalized(value) {
  return value.trim().toLowerCase()
}

function loadPolicy(filePath = policyPath) {
  const policy = JSON.parse(fs.readFileSync(filePath, 'utf8'))
  for (const key of [
    'allowedLicenses',
    'allowedLicenseNames',
    'allowedLicenseExceptions',
    'deniedLicenseFamilies',
    'licenseSelections',
    'containerToolchainComponents',
    'exceptions',
  ]) {
    if (!Array.isArray(policy[key])) throw new Error(`license policy ${key} must be an array`)
  }
  if (!policy.licenseAliases || typeof policy.licenseAliases !== 'object') {
    throw new Error('license policy licenseAliases must be an object')
  }
  validateExceptions(policy.exceptions)
  validateLicenseSelections(policy.licenseSelections)
  validateContainerToolchainComponents(policy.containerToolchainComponents)
  return policy
}

function validateContainerToolchainComponents(components) {
  const seen = new Set()
  for (const component of components) {
    if (
      !component.component ||
      component.component.includes('*') ||
      !component.license ||
      !component.reason?.trim()
    ) {
      throw new Error(
        'container toolchain components require an exact artifact, license, and reason',
      )
    }
    if (seen.has(component.component)) {
      throw new Error(`container toolchain component ${component.component} is duplicated`)
    }
    seen.add(component.component)
  }
}

function validateLicenseSelections(selections) {
  const components = new Set()
  for (const selection of selections) {
    if (!selection.component || selection.component === '*' || selection.component.includes('*')) {
      throw new Error('license selections require an exact component purl; wildcards are forbidden')
    }
    if (!selection.license || selection.license === '*' || selection.license.includes('*')) {
      throw new Error('license selections require an exact license')
    }
    if (!selection.reason?.trim()) {
      throw new Error(`license selection for ${selection.component} requires a reason`)
    }
    if (components.has(selection.component)) {
      throw new Error(`license selection for ${selection.component} is duplicated`)
    }
    components.add(selection.component)
  }
}

function validateExceptions(exceptions) {
  for (const exception of exceptions) {
    if (!exception.component || exception.component === '*' || exception.component.includes('*')) {
      throw new Error('license exceptions require an exact component purl; wildcards are forbidden')
    }
    if (!exception.license || exception.license === '*') {
      throw new Error(
        'license exceptions require an exact license; blanket exceptions are forbidden',
      )
    }
    if (!exception.reason?.trim() || !exception.approvedBy?.trim()) {
      throw new Error(`license exception for ${exception.component} requires reason and approvedBy`)
    }
    if (!/^\d{4}-\d{2}-\d{2}$/.test(exception.expiresOn || '')) {
      throw new Error(
        `license exception for ${exception.component} requires expiresOn in YYYY-MM-DD format`,
      )
    }
    const expiration = Date.parse(`${exception.expiresOn}T00:00:00Z`)
    if (
      !Number.isFinite(expiration) ||
      new Date(expiration).toISOString().slice(0, 10) !== exception.expiresOn
    ) {
      throw new Error(`license exception for ${exception.component} has an invalid expiration date`)
    }
  }
}

function classifyLicense(rawLicense, policy) {
  const alias = policy.licenseAliases[normalized(rawLicense)] || rawLicense
  const license = normalized(alias)
  if (policy.allowedLicenses.some((allowed) => normalized(allowed) === license)) {
    return { state: 'allowed', license: alias }
  }
  if (
    policy.allowedLicenseNames.some((allowed) => normalized(allowed) === normalized(rawLicense))
  ) {
    return { state: 'allowed', license: rawLicense }
  }
  if (policy.deniedLicenseFamilies.some((family) => license.startsWith(normalized(family)))) {
    return { state: 'denied', license: alias }
  }
  return { state: 'unknown', license: rawLicense }
}

function evaluateExpression(expression, policy) {
  const tokens = expression.match(/\(|\)|[^\s()]+/g) || []
  if (tokens.length === 0) return { state: 'unknown', license: expression }
  let index = 0

  function malformed() {
    return { state: 'unknown', license: expression, malformed: true }
  }

  function primary() {
    const token = tokens[index]
    if (token === '(') {
      index += 1
      const result = disjunction()
      if (tokens[index] !== ')') return malformed()
      index += 1
      return result
    }
    if (!token || ['AND', 'OR', 'WITH', ')'].includes(token)) return malformed()

    index += 1
    const result = classifyLicense(token, policy)
    if (tokens[index] !== 'WITH') return result

    index += 1
    const exception = tokens[index]
    if (!exception || ['AND', 'OR', 'WITH', '(', ')'].includes(exception)) return malformed()
    if (
      !policy.allowedLicenseExceptions.some(
        (allowed) => normalized(allowed) === normalized(exception),
      )
    ) {
      return { state: 'unknown', license: exception }
    }
    index += 1
    if (result.state !== 'allowed') return result
    return {
      state: 'allowed',
      license: token,
      licenses: [result.license],
      exceptions: [exception],
    }
  }

  function conjunction() {
    let result = primary()
    while (tokens[index] === 'AND') {
      index += 1
      const right = primary()
      if (result.malformed || right.malformed) return malformed()
      if (result.state !== 'allowed') continue
      result = right
    }
    return result
  }

  function disjunction() {
    let result = conjunction()
    while (tokens[index] === 'OR') {
      index += 1
      const right = conjunction()
      if (result.malformed || right.malformed) return malformed()
      if (result.state === 'allowed' || right.state === 'allowed') {
        result = result.state === 'allowed' ? result : right
      } else if (result.state !== 'unknown') {
        result = right
      }
    }
    return result
  }

  const result = disjunction()
  if (result.malformed || index !== tokens.length) return { state: 'unknown', license: expression }
  if (result.state !== 'allowed') return result
  return {
    state: 'allowed',
    license: expression,
    licenses: result.licenses ?? [result.license],
    exceptions: result.exceptions ?? [],
  }
}

function evaluateLicenseEntry(entry, policy) {
  if (entry?.expression) return evaluateExpression(entry.expression.trim(), policy)
  const rawLicense = entry?.license?.id || entry?.license?.name
  return rawLicense
    ? classifyLicense(rawLicense, policy)
    : { state: 'unknown', license: 'NOASSERTION' }
}

function licenseLabel(entry) {
  if (entry?.expression) return entry.expression
  return entry?.license?.id || entry?.license?.name || 'NOASSERTION'
}

function componentName(component) {
  return (
    component.purl || `${component.group || ''}:${component.name}@${component.version || 'unknown'}`
  )
}

function activeException(policy, component, license, today) {
  return policy.exceptions.find(
    (exception) =>
      exception.component === component.purl &&
      exception.license === license &&
      Date.parse(`${exception.expiresOn}T00:00:00Z`) >= today.getTime(),
  )
}

function checkBom(bom, policy, today = new Date()) {
  if (bom?.bomFormat !== 'CycloneDX' || !Array.isArray(bom.components)) {
    throw new Error('license policy requires a CycloneDX JSON SBOM with components')
  }
  const errors = []
  const components = []
  const collect = (component) => {
    components.push(component)
    if (Array.isArray(component.components)) component.components.forEach(collect)
  }
  bom.components.forEach(collect)
  for (const component of components) {
    const name = componentName(component)
    const entries =
      Array.isArray(component.licenses) && component.licenses.length ? component.licenses : [null]
    const evaluations = entries.map((entry) => {
      const label = licenseLabel(entry)
      const exception = activeException(policy, component, label, today)
      return exception
        ? { state: 'exception', license: label, exception }
        : evaluateLicenseEntry(entry, policy)
    })

    const selection = policy.licenseSelections.find((item) => item.component === component.purl)
    if (selection) {
      const selected = evaluations.find(
        (_, index) => normalized(licenseLabel(entries[index])) === normalized(selection.license),
      )
      if (!selected) {
        errors.push(`${name}: selected license ${selection.license} is not declared`)
      } else if (selected.state !== 'allowed') {
        errors.push(`${name}: selected license ${selection.license} is ${selected.state}`)
      }
      continue
    }

    const rejected = evaluations.filter(
      (result) => !['allowed', 'exception'].includes(result.state),
    )
    if (rejected.length) {
      const state = rejected.some((result) => result.state === 'unknown') ? 'unknown' : 'denied'
      errors.push(
        `${name}: ${state} license ${rejected.map((result) => result.license).join(', ')}`,
      )
    }
  }
  return errors
}

function main() {
  const files = process.argv.slice(2)
  if (files.length === 0) {
    console.error('Usage: node scripts/verify-license-policy.cjs <sbom.json> [...]')
    process.exit(1)
  }
  const policy = loadPolicy()
  const errors = []
  for (const file of files) {
    try {
      errors.push(...checkBom(JSON.parse(fs.readFileSync(file, 'utf8')), policy))
    } catch (error) {
      errors.push(`${file}: ${error.message}`)
    }
  }
  if (errors.length) {
    console.error('License policy: FAIL')
    for (const error of errors) console.error(`  - ${error}`)
    process.exit(1)
  }
  console.log(`License policy: PASS (${files.length} SBOM${files.length === 1 ? '' : 's'})`)
}

if (require.main === module) main()

module.exports = {
  checkBom,
  evaluateExpression,
  loadPolicy,
  validateExceptions,
  validateLicenseSelections,
}
