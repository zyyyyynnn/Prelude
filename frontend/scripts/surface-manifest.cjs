'use strict'

const crypto = require('node:crypto')
const fs = require('node:fs')
const path = require('node:path')
const { execFileSync } = require('node:child_process')

const repositoryRoot = path.resolve(__dirname, '../..')

function git(args, options = {}) {
  return execFileSync('git', args, {
    cwd: repositoryRoot,
    encoding: 'utf8',
    ...options,
  })
}

function computeInputRevision() {
  const files = git([
    'ls-files',
    '-z',
    '--cached',
    '--others',
    '--exclude-standard',
    '--',
    'frontend',
  ])
    .split('\0')
    .filter(Boolean)
    .sort()
  if (!files.length) throw new Error('No frontend inputs are available')
  const hashes = git(['hash-object', '--stdin-paths'], {
    input: `${files.join('\n')}\n`,
  })
    .trim()
    .split('\n')
  if (hashes.length !== files.length) throw new Error('Could not hash every frontend input')
  const digest = crypto.createHash('sha256')
  for (let index = 0; index < files.length; index += 1) {
    digest.update(files[index]).update('\0').update(hashes[index]).update('\n')
  }
  return digest.digest('hex')
}

function dirtyInputFiles() {
  return git(['status', '--porcelain', '--untracked-files=all', '--', 'frontend'])
    .split(/\r?\n/)
    .filter((line) => line.trim()).length
}

function hashFile(file) {
  return crypto.createHash('sha256').update(fs.readFileSync(file)).digest('hex')
}

function hashSurfaces(surfacesRoot, surfaces) {
  return Object.fromEntries(
    surfaces.map((surface) => [
      surface,
      hashFile(path.resolve(surfacesRoot, ...surface.split('/'))),
    ]),
  )
}

function sameSurfaceSet(previous, current) {
  if (!Array.isArray(previous) || previous.length !== current.length) return false
  const previousSet = new Set(previous)
  const currentSet = new Set(current)
  return (
    previousSet.size === currentSet.size &&
    [...currentSet].every((surface) => previousSet.has(surface))
  )
}

function buildSurfaceManifest({
  previousManifest,
  screenshotsRoot,
  inputRevision,
  capturedAt = new Date().toISOString(),
  themes,
  framesPerTheme,
  surfaces,
}) {
  const sortedSurfaces = [...surfaces].sort()
  const surfaceHashes = hashSurfaces(screenshotsRoot, sortedSurfaces)
  const preserveCapturedAt =
    previousManifest?.inputRevision === inputRevision &&
    sameSurfaceSet(previousManifest.surfaces, sortedSurfaces) &&
    sortedSurfaces.every(
      (surface) => previousManifest.surfaceHashes?.[surface] === surfaceHashes[surface],
    ) &&
    typeof previousManifest.capturedAt === 'string'

  return {
    inputRevision,
    capturedAt: preserveCapturedAt ? previousManifest.capturedAt : capturedAt,
    layout: '<theme>/<area>/<name>.png',
    themes: [...themes],
    framesPerTheme,
    surfaces: sortedSurfaces,
    surfaceHashes,
  }
}

module.exports = {
  buildSurfaceManifest,
  computeInputRevision,
  dirtyInputFiles,
  hashFile,
  hashSurfaces,
}
