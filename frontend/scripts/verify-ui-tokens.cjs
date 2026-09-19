#!/usr/bin/env node
'use strict'

const fs = require('node:fs')
const path = require('node:path')

const root = path.resolve(__dirname, '..')
const schema = JSON.parse(fs.readFileSync(path.join(root, 'tokens', 'ui-tokens.json'), 'utf8'))
const stylesRoot = path.join(root, 'src', 'shared', 'styles')
const sourceRoot = path.join(root, 'src')
const css = fs
  .readdirSync(stylesRoot)
  .filter((name) => name.endsWith('.css'))
  .map((name) => fs.readFileSync(path.join(stylesRoot, name), 'utf8'))
  .join('\n')
const declared = new Map(
  [...css.matchAll(/(--[\w-]+)\s*:\s*([^;]+);/g)].map((match) => [match[1], match[2].trim()]),
)
const violations = []
const catalogued = new Set(
  Object.values(schema.categories).flatMap((category) =>
    category.tokens.map((token) => `--${token}`),
  ),
)

function walkFiles(directory, extensionPattern) {
  return fs.readdirSync(directory, { withFileTypes: true }).flatMap((entry) => {
    const target = path.join(directory, entry.name)
    if (entry.isDirectory()) return walkFiles(target, extensionPattern)
    return extensionPattern.test(entry.name) ? [target] : []
  })
}

function walkCss(directory) {
  return walkFiles(directory, /\.css$/)
}

function extractBlock(source, selector) {
  const selectorStart = source.indexOf(selector)
  const openingBrace = source.indexOf('{', selectorStart)
  if (selectorStart < 0 || openingBrace < 0) return ''
  let depth = 0
  for (let index = openingBrace; index < source.length; index += 1) {
    if (source[index] === '{') depth += 1
    if (source[index] !== '}') continue
    depth -= 1
    if (depth === 0) return source.slice(openingBrace + 1, index)
  }
  return ''
}

for (const category of Object.values(schema.categories)) {
  for (const token of category.tokens) {
    if (!declared.has(`--${token}`)) violations.push(`missing declaration --${token}`)
  }
}
for (const [token, value] of Object.entries(schema.design_lock_values)) {
  if (declared.get(`--${token}`) !== value) {
    violations.push(`locked token --${token} must remain ${value}`)
  }
}
const rootBlock = extractBlock(css, '\n:root {')
// A scan that silently reads the wrong block is worse than no scan at all: assert the
// block we are about to audit actually looks like the token sheet.
if (rootBlock.length < 2000) {
  console.error(
    `UI token verification: FAIL — could not locate the :root token block (got ${rootBlock.length} chars)`,
  )
  process.exit(1)
}
for (const match of rootBlock.matchAll(/(--[\w-]+)\s*:/g)) {
  if (!catalogued.has(match[1])) violations.push(`uncatalogued root token ${match[1]}`)
}
const zValues = [...declared.entries()]
  .filter(([name]) => name.startsWith('--z-index-'))
  .map(([, value]) => value)
if (new Set(zValues).size !== zValues.length) violations.push('z-index token values must be unique')

for (const file of walkCss(sourceRoot)) {
  const relative = path.relative(root, file).replaceAll('\\', '/')
  for (const [index, line] of fs.readFileSync(file, 'utf8').split(/\r?\n/).entries()) {
    const shadow = line.match(/^\s*box-shadow\s*:\s*(.+?);\s*$/)
    if (shadow) {
      const value = shadow[1].trim()
      if (!/^none$/i.test(value) && !/var\(--shadow-/.test(value)) {
        violations.push(`${relative}:${index + 1}: raw box-shadow ${value}`)
      }
    }
    if (/^\s*font-weight\s*:\s*\d+/.test(line) || /^\s*font\s*:\s*\d+/.test(line)) {
      violations.push(`${relative}:${index + 1}: raw font weight`)
    }
    if (/^\s*border(?:-[\w-]+)?\s*:\s*1px\s+(?:solid|dashed)/.test(line)) {
      violations.push(`${relative}:${index + 1}: raw standard border width`)
    }
  }
}

// ---------------------------------------------------------------- geometry values
// An absolute length (px/rem) outside the token blocks is a design decision that has
// escaped the token system. Relative units (%, em, vh, vw) stay legal. A rule may opt
// out with a `geometry-exempt: <reason>` marker anywhere inside its block, which keeps
// technique-bound values (forced-colors outlines, the sr-only 1px clip) auditable.
{
  const absoluteLength = /\d*\.?\d+(?:px|rem)\b/
  for (const file of walkCss(sourceRoot)) {
    const relative = path.relative(root, file).replaceAll('\\', '/')
    const lines = fs.readFileSync(file, 'utf8').split(/\r?\n/)
    let inTokenBlock = false
    let braceDepth = 0
    let blockHasExemption = false
    lines.forEach((line, index) => {
      if (/^(?:@theme|:root[^{]*)\s*\{/.test(line) && braceDepth === 0) inTokenBlock = true
      if (line.includes('geometry-exempt')) blockHasExemption = true
      if (braceDepth === 0 && /\{/.test(line)) {
        blockHasExemption = line.includes('geometry-exempt')
      }
      for (const character of line) {
        if (character === '{') braceDepth += 1
        else if (character === '}') {
          braceDepth -= 1
          if (braceDepth === 0) inTokenBlock = false
        }
      }
      if (inTokenBlock || blockHasExemption) return
      const declaration = line.match(/^\s*([a-z-]+)\s*:\s*([^;{}]+);/)
      if (!declaration) return
      const [, property, value] = declaration
      if (property.startsWith('--')) return
      if (!absoluteLength.test(value) || value.includes('var(--')) {
        return
      }
      violations.push(
        `${relative}:${index + 1}: raw ${property}: ${value.trim()} — use a token or mark the rule geometry-exempt`,
      )
    })
  }
}

// A catalogued token nobody consumes is inventory, not a design system: it keeps
// `verify:tokens` green while the vocabulary drifts away from what screens use.
{
  const indexCss = fs.readFileSync(path.join(sourceRoot, 'shared', 'styles', 'index.css'), 'utf8')
  const rootBlock = indexCss.match(/\n:root\s*\{([\s\S]*?)\n\}/)
  const cssText = walkFiles(sourceRoot, /\.css$/)
    .map((file) => fs.readFileSync(file, 'utf8'))
    .join('\n')
  const codeText = [
    ...walkFiles(sourceRoot, /\.(ts|tsx)$/),
    ...(fs.existsSync(path.join(root, 'index.html')) ? [path.join(root, 'index.html')] : []),
  ]
    .map((file) => fs.readFileSync(file, 'utf8'))
    .join('\n')
  const count = (haystack, needle) => haystack.split(needle).length - 1
  // The shadcn bridge is consumed through the component class vocabulary, not by our
  // own rules, so those tokens are exported on purpose.
  const bridge = new Set(
    (schema.categories['component-tailwind-theme']?.tokens ?? []).map((token) => `--${token}`),
  )
  for (const match of rootBlock ? rootBlock[1].matchAll(/^ {2}(--[a-z0-9-]+):/gm) : []) {
    const token = match[1]
    if (bridge.has(token)) continue
    // The declaration itself is one CSS occurrence; anything beyond it is a reference.
    const used =
      count(cssText, `var(${token})`) > 0 || count(codeText, token) > 0 || count(cssText, token) > 1
    if (!used)
      violations.push(`src/shared/styles/index.css: ${token} is declared but never consumed`)
  }
}

// ---------------------------------------------------------------- reference check
// A `var(--x)` or an `atom-(--x)` shorthand whose name nothing ever declares is
// invalid at computed-value time: the declaration disappears without a trace.
{
  const declaredAnywhere = new Set(declared.keys())
  const sources = [...walkFiles(sourceRoot, /\.(ts|tsx|css)$/)]
  for (const file of sources) {
    for (const match of fs.readFileSync(file, 'utf8').matchAll(/(--[\w-]+)['"]?\s*:/g)) {
      declaredAnywhere.add(match[1])
    }
  }
  // Base UI writes these onto its positioner at runtime, so no source declares them.
  const runtimeProvided = new Set(['--available-height', '--anchor-width', '--transform-origin'])
  for (const file of sources) {
    const relative = path.relative(root, file).replaceAll('\\', '/')
    const text = fs.readFileSync(file, 'utf8')
    const references = new Set([
      ...[...text.matchAll(/var\(\s*(--[\w-]+)/g)].map((match) => match[1]),
      ...[...text.matchAll(/\(\s*(--[\w-]+)\s*\)/g)].map((match) => match[1]),
    ])
    for (const token of references) {
      if (declaredAnywhere.has(token) || runtimeProvided.has(token)) continue
      violations.push(`${relative}: ${token} is referenced but never declared`)
    }
  }
}

if (violations.length) {
  console.error(`UI token verification: FAIL (${violations.length})`)
  for (const violation of violations) console.error(`  ${violation}`)
  process.exit(1)
}
console.log(`UI token verification: PASS (${declared.size} declarations)`)
