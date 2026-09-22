#!/usr/bin/env node
'use strict'

const fs = require('node:fs')
const path = require('node:path')

const dist = path.resolve(__dirname, '..', 'dist')
if (!fs.existsSync(path.join(dist, 'index.html'))) {
  throw new Error('Production bundle is missing')
}
const assetsDir = path.join(dist, 'assets')
const chunks = fs
  .readdirSync(assetsDir)
  .filter((name) => /\.(?:js|css)$/.test(name))
  .map((name) => ({ name, text: fs.readFileSync(path.join(assetsDir, name), 'utf8') }))

/* The component gallery is a development-only route: `main.tsx` registers it behind
   `import.meta.env.DEV`, so a production build must not carry it.

   The previous form grepped the concatenated bundle for `/components-lab` and
   `Component Lab` — two spellings of one thing, so renaming the route, moving the
   chunk, or minifying the label away silently disarmed the check.

   The identifiers below are both local to `app/lab/ComponentLab.tsx`: `DemoGroup` is a
   function declared there and used nowhere else, and `ComponentLab` is the exported
   component. A chunk carrying both is the gallery. Asserting per chunk (rather than
   over the concatenation) also catches the case where the lab survives only as a
   split point nobody imports. */
const LAB_MARKERS = ['ComponentLab', 'DemoGroup']
const leaked = chunks.filter((chunk) => LAB_MARKERS.every((marker) => chunk.text.includes(marker)))

if (leaked.length) {
  throw new Error(
    `Development route found in production bundle: ${leaked.map((chunk) => chunk.name).join(', ')} ` +
      `carries ${LAB_MARKERS.join(' and ')}`,
  )
}

/* A reader that quietly walks nothing is worse than no reader. */
if (chunks.length === 0) {
  throw new Error('Production bundle has no assets to inspect')
}

console.log(
  `Production bundle verification: PASS (${chunks.length} chunks, no component-lab chunk)`,
)
