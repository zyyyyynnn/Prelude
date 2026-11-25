'use strict'

const { test } = require('node:test')
const assert = require('node:assert/strict')
const crypto = require('node:crypto')
const fs = require('node:fs')
const os = require('node:os')
const path = require('node:path')
const { buildSurfaceManifest } = require('./surface-manifest.cjs')

function fixture(run) {
  const root = fs.mkdtempSync(path.join(os.tmpdir(), 'prelude-surface-manifest-'))
  try {
    const screenshotsRoot = path.join(root, 'screenshots')
    fs.mkdirSync(path.join(screenshotsRoot, 'light', 'interview'), { recursive: true })
    fs.writeFileSync(path.join(screenshotsRoot, 'light', 'interview', 'login.png'), 'login')
    fs.writeFileSync(path.join(screenshotsRoot, 'light', 'interview', 'workspace.png'), 'workspace')
    run({ screenshotsRoot })
  } finally {
    fs.rmSync(root, { recursive: true, force: true })
  }
}

function manifest(options) {
  return buildSurfaceManifest({
    screenshotsRoot: options.screenshotsRoot,
    inputRevision: 'a'.repeat(64),
    capturedAt: '2026-11-25T00:00:00.000Z',
    themes: ['light'],
    framesPerTheme: { light: 2 },
    surfaces: ['light/interview/login.png', 'light/interview/workspace.png'],
    ...options,
  })
}

test('records a SHA-256 hash for every surface', () => {
  fixture(({ screenshotsRoot }) => {
    const result = manifest({ screenshotsRoot })
    assert.equal(
      result.surfaceHashes['light/interview/login.png'],
      crypto.createHash('sha256').update('login').digest('hex'),
    )
    assert.equal(
      result.surfaceHashes['light/interview/workspace.png'],
      crypto.createHash('sha256').update('workspace').digest('hex'),
    )
  })
})

test('preserves capturedAt only when inputs, surface set, and bytes are unchanged', () => {
  fixture(({ screenshotsRoot }) => {
    const previous = manifest({ screenshotsRoot })
    const options = {
      screenshotsRoot,
      previousManifest: previous,
      capturedAt: '2026-11-26T00:00:00.000Z',
    }

    const unchanged = manifest({
      ...options,
      surfaces: [...previous.surfaces].reverse(),
    })
    assert.equal(unchanged.capturedAt, previous.capturedAt)

    fs.writeFileSync(path.join(screenshotsRoot, 'light', 'interview', 'login.png'), 'updated')
    const changedBytes = manifest(options)
    assert.equal(changedBytes.capturedAt, options.capturedAt)
    assert.notEqual(
      changedBytes.surfaceHashes['light/interview/login.png'],
      previous.surfaceHashes['light/interview/login.png'],
    )

    fs.writeFileSync(path.join(screenshotsRoot, 'light', 'interview', 'login.png'), 'login')
    assert.equal(
      manifest({ ...options, inputRevision: 'b'.repeat(64) }).capturedAt,
      options.capturedAt,
    )
    assert.equal(
      manifest({ ...options, surfaces: ['light/interview/login.png'] }).capturedAt,
      options.capturedAt,
    )
  })
})
