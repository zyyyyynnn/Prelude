'use strict'

const { test } = require('node:test')
const assert = require('node:assert/strict')
const fs = require('node:fs')
const os = require('node:os')
const path = require('node:path')
const crypto = require('node:crypto')
const { verifyEvidence } = require('./verify-evidence.cjs')

const inputRevision = 'a'.repeat(64)

function fixture(run) {
  const root = fs.mkdtempSync(path.join(os.tmpdir(), 'prelude-evidence-'))
  try {
    const screenshotsRoot = path.join(root, 'screenshots')
    const manifestPath = path.join(screenshotsRoot, 'manifest.json')
    fs.mkdirSync(path.join(screenshotsRoot, 'light', 'interview'), { recursive: true })
    fs.writeFileSync(path.join(screenshotsRoot, 'light', 'interview', 'login.png'), 'png')
    fs.writeFileSync(
      manifestPath,
      JSON.stringify({
        inputRevision,
        surfaces: ['light/interview/login.png'],
        surfaceHashes: {
          'light/interview/login.png': crypto.createHash('sha256').update('png').digest('hex'),
        },
      }),
    )
    run({ root, screenshotsRoot, manifestPath })
  } finally {
    fs.rmSync(root, { recursive: true, force: true })
  }
}

function verify(options) {
  return verifyEvidence({ inputRevision, currentDirtyInputFiles: 0, ...options })
}

test('accepts current evidence whose listed surfaces exist and match their hashes', () => {
  fixture((options) => assert.deepEqual(verify(options), []))
})

test('blocks a surface whose bytes no longer match the manifest', () => {
  fixture(({ manifestPath, screenshotsRoot }) => {
    fs.writeFileSync(path.join(screenshotsRoot, 'light', 'interview', 'login.png'), 'changed')
    assert.match(verify({ manifestPath, screenshotsRoot }).join('\n'), /SHA-256 does not match/)
  })
})

test('requires a valid hash for every listed surface and no unlisted hashes', () => {
  fixture(({ manifestPath, screenshotsRoot }) => {
    const manifest = JSON.parse(fs.readFileSync(manifestPath, 'utf8'))
    delete manifest.surfaceHashes['light/interview/login.png']
    fs.writeFileSync(manifestPath, JSON.stringify(manifest))
    assert.match(
      verify({ manifestPath, screenshotsRoot }).join('\n'),
      /SHA-256 is missing or invalid/,
    )

    manifest.surfaceHashes['light/interview/login.png'] = 'not-a-sha256'
    fs.writeFileSync(manifestPath, JSON.stringify(manifest))
    assert.match(
      verify({ manifestPath, screenshotsRoot }).join('\n'),
      /SHA-256 is missing or invalid/,
    )

    manifest.surfaceHashes['light/interview/login.png'] = 'a'.repeat(64)
    manifest.surfaceHashes['light/interview/unlisted.png'] = 'b'.repeat(64)
    fs.writeFileSync(manifestPath, JSON.stringify(manifest))
    const errors = verify({ manifestPath, screenshotsRoot }).join('\n')
    assert.match(errors, /SHA-256 does not match/)
    assert.match(errors, /hash has no listed surface/)
  })
})

test('blocks stale input revision and missing surfaces', () => {
  fixture(({ manifestPath, screenshotsRoot }) => {
    const manifest = JSON.parse(fs.readFileSync(manifestPath, 'utf8'))
    manifest.inputRevision = 'b'.repeat(64)
    manifest.surfaces.push('dark/interview/login.png')
    fs.writeFileSync(manifestPath, JSON.stringify(manifest))
    const errors = verify({ manifestPath, screenshotsRoot }).join('\n')
    assert.match(errors, /does not match frontend inputs/)
    assert.match(errors, /surface file is missing/)
  })
})

test('rejects a dirty frontend worktree', () => {
  fixture((options) => {
    assert.match(
      verify({ ...options, currentDirtyInputFiles: 1 }).join('\n'),
      /frontend inputs must be clean/,
    )
  })
})

test('rejects empty manifests and paths that escape the screenshot directory', () => {
  fixture(({ manifestPath, screenshotsRoot }) => {
    fs.writeFileSync(manifestPath, JSON.stringify({ inputRevision, surfaces: [] }))
    assert.match(verify({ manifestPath, screenshotsRoot }).join('\n'), /non-empty array/)
    fs.writeFileSync(
      manifestPath,
      JSON.stringify({
        inputRevision,
        surfaces: ['../secret.png'],
      }),
    )
    assert.match(verify({ manifestPath, screenshotsRoot }).join('\n'), /invalid surface path/)
  })
})

test('fails when the manifest does not exist', () => {
  fixture(({ root, screenshotsRoot }) => {
    assert.match(
      verify({ manifestPath: path.join(root, 'missing.json'), screenshotsRoot }).join('\n'),
      /cannot read/,
    )
  })
})
