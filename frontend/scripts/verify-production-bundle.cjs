#!/usr/bin/env node
'use strict'

const fs = require('node:fs')
const path = require('node:path')

const dist = path.resolve(__dirname, '..', 'dist')
if (!fs.existsSync(path.join(dist, 'index.html'))) {
  throw new Error('Production bundle is missing')
}
const src = path.resolve(__dirname, '..', 'src')
const assetsDir = path.join(dist, 'assets')
const chunks = fs
  .readdirSync(assetsDir)
  .filter((name) => /\.(?:js|css)$/.test(name))
  .map((name) => ({ name, text: fs.readFileSync(path.join(assetsDir, name), 'utf8') }))

// Minifiers rename local identifiers, so the dev-only gallery is detected by its literal markers.
const LAB_MARKERS = [
  { text: '/components-lab', source: 'app/main.tsx' },
  { text: 'Component Lab', source: 'app/lab/ComponentLab.tsx' },
]

// Missing source markers make the scan vacuous, so renames must fail the gate.
for (const marker of LAB_MARKERS) {
  const sourceFile = path.join(src, marker.source)
  if (!fs.existsSync(sourceFile)) {
    throw new Error(
      `verify-production-bundle: leak marker ${JSON.stringify(marker.text)} expects ${marker.source}, which no longer exists`,
    )
  }
  if (!fs.readFileSync(sourceFile, 'utf8').includes(marker.text)) {
    throw new Error(
      `verify-production-bundle: leak marker ${JSON.stringify(marker.text)} not found in ${marker.source}; the markers are stale and the gate is disarmed`,
    )
  }
}

const leaked = chunks.filter((chunk) =>
  LAB_MARKERS.some((marker) => chunk.text.includes(marker.text)),
)

if (leaked.length) {
  throw new Error(
    `Development route found in production bundle: ${leaked.map((chunk) => chunk.name).join(', ')} ` +
      `carries ${LAB_MARKERS.map((marker) => JSON.stringify(marker.text)).join(' or ')}`,
  )
}

if (chunks.length === 0) {
  throw new Error('Production bundle has no assets to inspect')
}

const nginx = fs.readFileSync(path.resolve(__dirname, '..', 'nginx.conf'), 'utf8')
if (!nginx.includes('proxy_set_header X-Forwarded-For $remote_addr;')) {
  throw new Error('Production proxy must overwrite X-Forwarded-For with the client address')
}
for (const header of ['Forwarded', 'X-Forwarded-Host', 'X-Forwarded-Port', 'X-Forwarded-Prefix']) {
  if (!nginx.includes(`proxy_set_header ${header} "";`)) {
    throw new Error(`Production proxy must clear the client-supplied ${header} header`)
  }
}

console.log(
  `Production bundle verification: PASS (${chunks.length} chunks, no component-lab chunk, trusted proxy headers)`,
)
