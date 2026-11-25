'use strict'

const fs = require('node:fs')
const path = require('node:path')
const { computeInputRevision, dirtyInputFiles, hashFile } = require('./surface-manifest.cjs')

const repositoryRoot = path.resolve(__dirname, '../..')
const manifestPath = path.join(repositoryRoot, 'docs', 'screenshots', 'manifest.json')
const screenshotsRoot = path.join(repositoryRoot, 'docs', 'screenshots')

function verifyEvidence(options = {}) {
  const manifestFile = options.manifestPath || manifestPath
  const surfacesRoot = options.screenshotsRoot || screenshotsRoot
  const expectedInputRevision = options.inputRevision
  const errors = []

  if (!expectedInputRevision) errors.push('frontend input revision is unavailable')
  let manifest
  try {
    manifest = JSON.parse(fs.readFileSync(manifestFile, 'utf8'))
  } catch (error) {
    return [...errors, `cannot read ${manifestFile}: ${error.message}`]
  }

  if (manifest.inputRevision !== expectedInputRevision) {
    errors.push(
      `manifest inputRevision ${manifest.inputRevision || '<missing>'} does not match frontend inputs ${expectedInputRevision}`,
    )
  }
  const currentDirtyInputs = options.currentDirtyInputFiles
  if (currentDirtyInputs !== 0) {
    errors.push(`frontend inputs must be clean, received ${String(currentDirtyInputs)} dirty files`)
  }
  if (!Array.isArray(manifest.surfaces) || manifest.surfaces.length === 0) {
    errors.push('manifest surfaces must be a non-empty array')
    return errors
  }
  const hashes = manifest.surfaceHashes
  const validHashes = Boolean(hashes) && typeof hashes === 'object' && !Array.isArray(hashes)
  if (!validHashes) errors.push('manifest surfaceHashes must be an object')

  const seen = new Set()
  const resolvedRoot = path.resolve(surfacesRoot)
  for (const surface of manifest.surfaces) {
    if (
      typeof surface !== 'string' ||
      surface.includes('\\') ||
      path.posix.isAbsolute(surface) ||
      surface.split('/').some((segment) => !segment || segment === '.' || segment === '..') ||
      !surface.endsWith('.png')
    ) {
      errors.push(`invalid surface path: ${String(surface)}`)
      continue
    }
    if (seen.has(surface)) errors.push(`duplicate surface path: ${surface}`)
    seen.add(surface)
    const file = path.resolve(resolvedRoot, ...surface.split('/'))
    if (file !== resolvedRoot && !file.startsWith(`${resolvedRoot}${path.sep}`)) {
      errors.push(`surface escapes screenshot directory: ${surface}`)
      continue
    }
    if (!fs.existsSync(file) || !fs.statSync(file).isFile()) {
      errors.push(`surface file is missing: ${surface}`)
      continue
    }
    if (validHashes) {
      const expected = hashes[surface]
      if (typeof expected !== 'string' || !/^[a-f0-9]{64}$/.test(expected)) {
        errors.push(`surface SHA-256 is missing or invalid: ${surface}`)
        continue
      }
      try {
        if (hashFile(file) !== expected) {
          errors.push(`surface SHA-256 does not match: ${surface}`)
        }
      } catch (error) {
        errors.push(`cannot hash surface ${surface}: ${error.message}`)
      }
    }
  }
  if (validHashes) {
    for (const surface of Object.keys(hashes)) {
      if (!seen.has(surface)) errors.push(`surface hash has no listed surface: ${surface}`)
    }
  }
  return errors
}

function main() {
  let inputs
  try {
    inputs = {
      inputRevision: computeInputRevision(),
      currentDirtyInputFiles: dirtyInputFiles(),
    }
  } catch (error) {
    console.error(`Visual evidence: FAIL\n  - cannot inspect frontend inputs: ${error.message}`)
    process.exit(1)
  }
  const errors = verifyEvidence(inputs)
  if (errors.length) {
    console.error('Visual evidence: FAIL')
    for (const error of errors) console.error(`  - ${error}`)
    process.exit(1)
  }
  console.log('Visual evidence: PASS')
}

if (require.main === module) main()

module.exports = { verifyEvidence }
