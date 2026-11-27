#!/usr/bin/env node
'use strict'

const fs = require('node:fs')
const path = require('node:path')
const { parseSync } = require('oxc-parser')

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

const jsChunks = chunks.filter((chunk) => chunk.name.endsWith('.js'))
const chunkByName = new Map(jsChunks.map((chunk) => [chunk.name, chunk]))
const staticImports = (chunk) => {
  const parsed = parseSync(chunk.name, chunk.text, { sourceType: 'module' })
  if (parsed.errors.length) {
    throw new Error(`Cannot inspect production chunk ${chunk.name}: ${parsed.errors[0].message}`)
  }
  return parsed.program.body.flatMap((statement) => {
    if (statement.type !== 'ImportDeclaration' || statement.source?.type !== 'Literal') return []
    const target = path.posix.normalize(
      path.posix.join(path.posix.dirname(chunk.name), statement.source.value),
    )
    const dependency = chunkByName.get(target)
    return dependency ? [dependency] : []
  })
}

const forbiddenModules = [
  {
    name: 'InterviewPage',
    source: 'features/interview/InterviewPage.tsx',
    chunk: /^InterviewPage-/,
  },
  {
    name: 'InterviewSetupComposer',
    source: 'features/interview/components/InterviewSetup.tsx',
    marker: '准备开始一场沉浸式模拟面试',
  },
  {
    name: 'ProfilePanel',
    source: 'features/profile/ProfilePanel.tsx',
    marker: '修改密码',
  },
  {
    name: 'ResumeManagementPanel',
    source: 'features/resume/ResumeManagementPanel.tsx',
    marker: '已上传简历',
  },
  {
    name: 'PositionManagementPanel',
    source: 'features/position/PositionManagementPanel.tsx',
    marker: '创建岗位',
  },
]
for (const module of forbiddenModules) {
  const source = fs.readFileSync(path.join(src, module.source), 'utf8')
  if (module.marker && !source.includes(module.marker)) {
    throw new Error(`Production chunk marker for ${module.name} is stale in ${module.source}`)
  }
  const present = jsChunks.some(
    (chunk) =>
      (module.chunk && module.chunk.test(chunk.name)) ||
      (module.marker && chunk.text.includes(module.marker)),
  )
  if (!present)
    throw new Error(`Production bundle does not contain ${module.name}; chunk gate is vacuous`)
}

const shellChunk = jsChunks.find((chunk) => /^AppShell-/.test(chunk.name))
const analyticsChunk = jsChunks.find((chunk) => /^analytics-/.test(chunk.name))
const entryChunk = jsChunks.find((chunk) => /^index-/.test(chunk.name))
for (const [name, chunk] of [
  ['entry', entryChunk],
  ['AppShell', shellChunk],
  ['analytics', analyticsChunk],
]) {
  if (!chunk) throw new Error(`Production bundle is missing the ${name} chunk`)
  const visited = new Set()
  const pending = [{ chunk, path: [chunk.name] }]
  while (pending.length) {
    const current = pending.pop()
    if (visited.has(current.chunk.name)) continue
    visited.add(current.chunk.name)
    const forbidden = forbiddenModules.find(
      (module) =>
        (module.chunk && module.chunk.test(current.chunk.name)) ||
        (module.marker && current.chunk.text.includes(module.marker)),
    )
    if (forbidden) {
      throw new Error(
        `${name} statically depends on ${forbidden.name}: ${[...current.path, current.chunk.name].join(' -> ')}`,
      )
    }
    for (const dependency of staticImports(current.chunk)) {
      pending.push({ chunk: dependency, path: [...current.path, current.chunk.name] })
    }
  }
}

const server = fs.readFileSync(path.resolve(__dirname, '..', 'server.go'), 'utf8')
if (
  !server.includes('httputil.NewSingleHostReverseProxy') ||
  !server.includes('request.Header.Del(header)')
) {
  throw new Error('Production proxy must sanitize client-supplied forwarding headers')
}
for (const header of [
  'Forwarded',
  'X-Forwarded-For',
  'X-Forwarded-Host',
  'X-Forwarded-Port',
  'X-Forwarded-Prefix',
]) {
  if (!server.includes(`"${header}"`)) {
    throw new Error(`Production proxy must clear the client-supplied ${header} header`)
  }
}

console.log(
  `Production bundle verification: PASS (${chunks.length} chunks, no component-lab chunk, trusted proxy headers)`,
)
