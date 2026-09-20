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

const lineOf = (text, index) => text.slice(0, index).split('\n').length

/* Top-level blocks, because that is the unit a `geometry-exempt` marker speaks for. */
function topLevelBlocks(text) {
  const blocks = []
  let cursor = 0
  while (cursor < text.length) {
    const open = text.indexOf('{', cursor)
    if (open < 0) break
    let depth = 0
    let close = open
    for (; close < text.length; close += 1) {
      if (text.startsWith('/*', close)) {
        const end = text.indexOf('*/', close + 2)
        close = end < 0 ? text.length : end + 1
        continue
      }
      if (text[close] === '{') depth += 1
      else if (text[close] === '}' && (depth -= 1) === 0) break
    }
    blocks.push({
      selector: text
        .slice(cursor, open)
        .replace(/\/\*[\s\S]*?\*\//g, ' ')
        .trim(),
      open: open + 1,
      close,
    })
    cursor = close + 1
  }
  return blocks
}

/* Declarations with their line numbers. Scanning one line at a time silently misses any
   value the formatter wrapped across lines, so the sheet's longest geometry values were
   the ones nobody checked. */
function declarationsIn(body, firstLine) {
  const stripped = body.replace(/\/\*[\s\S]*?\*\//g, (comment) => comment.replace(/[^\n]/g, ' '))
  const declarations = []
  let segment = ''
  let line = firstLine
  let segmentLine = firstLine
  let begun = false
  const commit = (terminator) => {
    const source = segment.trim()
    segment = ''
    begun = false
    if (terminator === '{' || !source) return
    const match = source.match(/^([a-zA-Z-]+)\s*:\s*([\s\S]+)$/)
    if (match) declarations.push({ property: match[1], value: match[2].trim(), line: segmentLine })
  }
  for (const character of stripped) {
    if (character === '\n') {
      line += 1
      segment += ' '
      continue
    }
    if (character === '{' || character === '}' || character === ';') {
      commit(character)
      continue
    }
    if (!begun && !/\s/.test(character)) {
      begun = true
      segmentLine = line
    }
    segment += character
  }
  commit(';')
  return declarations
}

let scannedDeclarations = 0

function eachCheckedDeclaration(files, visit) {
  for (const file of files) {
    const relative = path.relative(root, file).replaceAll('\\', '/')
    const text = fs.readFileSync(file, 'utf8')
    for (const block of topLevelBlocks(text)) {
      // The token sheet is where raw values are *supposed* to live.
      if (/^(?:@theme|:root)\b/.test(block.selector)) continue
      const body = text.slice(block.open, block.close)
      if (body.includes('geometry-exempt') || block.selector.includes('geometry-exempt')) continue
      for (const declaration of declarationsIn(body, lineOf(text, block.open))) {
        scannedDeclarations += 1
        visit(relative, declaration)
      }
    }
  }
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
// Custom properties count: a `--something: 20px` inside a component is the same escape
// as a raw declaration, and a `var(--token, 0px)` fallback is a default, not a size.
{
  const absoluteLength = /\d*\.?\d+(?:px|rem)\b/
  const varFallback = /var\(\s*--[\w-]+\s*,[^)]*\)/g
  eachCheckedDeclaration(walkCss(sourceRoot), (relative, declaration) => {
    if (!absoluteLength.test(declaration.value.replace(varFallback, 'var(--fallback)'))) return
    violations.push(
      `${relative}:${declaration.line}: raw ${declaration.property}: ${declaration.value} — use a token or mark the rule geometry-exempt`,
    )
  })
}

// ---------------------------------------------------------------- box sizes
// `--spacing-*` is the gap ladder. A box that happens to measure a glyph tier has to
// name the glyph token, otherwise it quietly follows the spacing ladder whenever a gap
// step moves — which is how an icon box and its glyph stopped agreeing.
{
  const boxSize = /(?:inline|block)-size$|^(?:min-|max-)?(?:width|height)$/
  const glyphByValue = new Map(
    [...declared]
      .filter(([name]) => name.startsWith('--ui-glyph-'))
      .map(([name, value]) => [value.trim(), name]),
  )
  eachCheckedDeclaration(walkCss(sourceRoot), (relative, declaration) => {
    if (!boxSize.test(declaration.property)) return
    // Only a box that *is* one spacing step borrows the ladder; a spacing term inside a
    // derived expression (`calc(control + spacing)`) is a threshold, which is what the
    // ladder is for.
    const match = declaration.value.match(/^var\((--spacing-[\w-]+)\)$/)
    if (!match) return
    const size = declared.get(match[1])?.trim()
    const glyph = size ? glyphByValue.get(size) : undefined
    if (glyph) {
      violations.push(
        `${relative}:${declaration.line}: ${declaration.property} borrows ${match[1]} (${size}); a box that size is ${glyph}`,
      )
    }
  })
}

// A reader that quietly finds nothing is worse than no reader: both scans above share it,
// so assert it actually walked the sheet.
if (scannedDeclarations < 300) {
  console.error(
    `UI token verification: FAIL — the CSS reader only saw ${scannedDeclarations} declarations`,
  )
  process.exit(1)
}

// ---------------------------------------------------------------- derived tokens
// A token that exists to contain or align with another measurement is not free to drift
// away from it. Registering it here keeps the relationship in the contract instead of
// in a comment: the value must be an expression, and it must name every source token.
for (const [token, sources] of Object.entries(schema.derived_tokens ?? {})) {
  const value = declared.get(`--${token}`)
  if (!value) {
    violations.push(`derived token --${token} is not declared`)
    continue
  }
  if (!value.includes('var(--')) {
    violations.push(`--${token} must stay derived from ${sources.join(', ')}, not a literal`)
  }
  for (const source of sources) {
    if (!value.includes(`var(--${source})`)) {
      violations.push(`--${token} must derive from --${source}`)
    }
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
  /* `@theme` re-declares several tokens as `--x: var(--x)` so Tailwind keeps the value in
     sync with the hand-written block. A self-reference is not a consumer: blank those
     lines out before counting, or every mirrored token looks used forever. */
  const cssWithoutSelfMirrors = cssText.replace(
    /^([ \t]*)(--[a-z0-9-]+):[ \t]*var\(\2\);[ \t]*$/gm,
    (_line, indent) => `${indent}/* self mirror */`,
  )
  /* The shadcn bridge maps the product palette onto the semantic names shadcn-style
     components expect (`bg-card`, `text-muted-foreground`, `border-input`). No first-party
     rule or class reads them today; they stay as an adapter for components pulled in from
     that ecosystem, so they are exempt from the consumer count by decision rather than by
     oversight. Adding a token here is that decision, made out loud. */
  const bridge = new Set(
    (schema.categories['component-tailwind-theme']?.tokens ?? []).map((token) => `--${token}`),
  )
  for (const match of rootBlock ? rootBlock[1].matchAll(/^ {2}(--[a-z0-9-]+):/gm) : []) {
    const token = match[1]
    if (bridge.has(token)) continue
    /* A reference is `var(--token)` in CSS, the token's own name in code (the functional
       atom form `size-(--token)`), or a utility class carrying its key — `--spacing-0`
       backs `m-0`, `--radius-lg` backs `rounded-lg`, and neither writes the token out.
       The class test is deliberately loose: under-reporting a dead token is acceptable,
       deleting one that a class still reads is not. */
    const key = token.replace(/^--/, '')
    /* Tailwind turns `--<namespace>-<key>` into `<utility>-<key>` (`--spacing-0` → `m-0`,
       `--radius-lg` → `rounded-lg`), so the class carries the key after the namespace,
       not the whole token name. */
    const namespaces = [
      'color-',
      'font-',
      'radius-',
      'spacing-',
      'text-',
      'leading-',
      'shadow-',
      'ease-',
      'blur-',
      'container-',
      'breakpoint-',
    ]
    const namespace = namespaces.find((prefix) => key.startsWith(prefix))
    const classKey = namespace ? key.slice(namespace.length) : key
    const used =
      count(cssWithoutSelfMirrors, `var(${token})`) > 0 ||
      count(codeText, token) > 0 ||
      new RegExp(`\\b[a-z]+-${classKey.replace(/[-[\]/{}()*+?.\\^$|]/g, '\\$&')}\\b`).test(codeText)
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
