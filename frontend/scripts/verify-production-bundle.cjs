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

/* The component gallery is a development-only route: `main.tsx` registers it behind
   `import.meta.env.DEV`, so a production build must not carry it.

   Earlier revisions matched identifiers local to `app/lab/ComponentLab.tsx` (`ComponentLab`,
   `DemoGroup`). Minifiers rename local symbols, so those identifiers never survive into `dist/`;
   the match could not fire and the check stayed green even when the gallery actually shipped.
   The markers below are therefore string literals, which minifiers copy verbatim:

     - `/components-lab` — the route path, registered only inside the DEV-guarded block;
     - `Component Lab`   — the page title, unique to the gallery component.

   A leak scatters these across chunks: the route path lands in the entry chunk, the title in the
   lazily-split gallery chunk. So we assert per chunk with "carries any marker" instead of "all
   markers in one chunk" — the old every() reading was only sound while both identifiers lived in
   the same file. On a clean build neither literal appears in any asset, so there is no false
   positive, and each offending chunk is still named in the failure below. */
const LAB_MARKERS = [
  { text: '/components-lab', source: 'app/main.tsx' },
  { text: 'Component Lab', source: 'app/lab/ComponentLab.tsx' },
]

/* Guard the reader against walking nothing. If the route is renamed or the page retitled, the
   literals above vanish from the source tree, the leak scan below then matches zero chunks, and
   the gate silently passes — the exact disarm this family of checks must never allow. Confirm each
   marker is still declared where we expect it, so a rename turns the gate red instead of quietly
   disarming it. */
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

/* A reader that quietly walks nothing is worse than no reader. */
if (chunks.length === 0) {
  throw new Error('Production bundle has no assets to inspect')
}

console.log(
  `Production bundle verification: PASS (${chunks.length} chunks, no component-lab chunk)`,
)
