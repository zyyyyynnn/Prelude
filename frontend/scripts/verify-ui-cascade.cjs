#!/usr/bin/env node
'use strict'

/**
 * Cascade-collision gate.
 *
 * Tailwind puts custom @utility blocks and core atoms into one utilities layer whose
 * relative order is not derivable from source order, and any unlayered class in
 * src/shared/styles/index.css outranks the whole layer. Combining two classes that
 * claim the same property on one element therefore resolves by luck.
 *
 * This gate reads the built stylesheet (the only place the real order exists) and
 * reports every call site that does it, so overlaps get removed by making the
 * utilities orthogonal instead of by betting on sort order.
 *
 * It also reports a core atom that an unlayered class on the same element always
 * beats: the winner is predictable, but the atom is dead and the page never gets
 * what the call site asked for. Both checks see through `cn(base, className)`, so a
 * class a component injects behind the caller's back counts too.
 */

const fs = require('node:fs')
const path = require('node:path')
const { declaredUtilities, utilityFamily } = require('./utility-names.cjs')

const root = path.resolve(__dirname, '..')
const distDir = path.join(root, 'dist', 'assets')
const indexCss = fs.readFileSync(path.join(root, 'src', 'shared', 'styles', 'index.css'), 'utf8')

if (!fs.existsSync(distDir)) {
  console.error('Cascade verification: FAIL — run `npm run build` first (needs dist/assets/*.css)')
  process.exit(1)
}

const registeredUtilities = new Set(declaredUtilities(indexCss).map(utilityFamily))
// An unlayered class outranks every utility, so it counts as an override too.
const unlayeredStart = indexCss.indexOf('\n* {')
const unlayeredClasses = new Set(
  [
    ...indexCss
      .slice(unlayeredStart < 0 ? 0 : unlayeredStart)
      .matchAll(/^\.([a-z][-\w]*)\s*[,{]/gm),
  ].map((m) => m[1]),
)

// ---------- parse emitted rules: class -> declared properties + global order
const classRules = new Map()
let order = 0
for (const file of fs.readdirSync(distDir).filter((name) => name.endsWith('.css'))) {
  const css = fs.readFileSync(path.join(distDir, file), 'utf8')
  const rulePattern = /([^{}]+)\{([^{}]*)\}/g
  let match
  while ((match = rulePattern.exec(css)) !== null) {
    const selector = match[1].trim()
    const declarations = match[2]
    if (!selector || !declarations.trim()) continue
    if (/^@/.test(selector)) continue
    // Only a single compound selector describes what this class itself declares;
    // `.foo li` or `.foo .bar` belongs to a descendant and must not be attributed to `foo`.
    if (/[,\s>+~]/.test(selector)) continue
    // Tailwind escapes the shorthand atoms it emits (`max-w-\(--token\)`), so the
    // selector name is unescaped back to the class a call site actually writes.
    const classes = selector.match(/\.([a-zA-Z](?:\\.|[-\w])*)/g)
    if (!classes || classes.length !== 1) continue
    const name = classes[0].slice(1).replaceAll('\\', '')
    for (const part of declarations.split(';')) {
      const property = part.split(':')[0].trim()
      if (!property || property.startsWith('--')) continue
      if (!classRules.has(name)) classRules.set(name, [])
      classRules.get(name).push({ property, order: order })
    }
    order += 1
  }
}

// ---------- shorthand expansion so `margin` really collides with `margin-top`
const SHORTHANDS = {
  margin: [
    'margin',
    'margin-top',
    'margin-bottom',
    'margin-left',
    'margin-right',
    'margin-inline',
    'margin-inline-start',
    'margin-inline-end',
    'margin-block',
    'margin-block-start',
    'margin-block-end',
  ],
  padding: [
    'padding',
    'padding-top',
    'padding-bottom',
    'padding-left',
    'padding-right',
    'padding-inline',
    'padding-inline-start',
    'padding-inline-end',
    'padding-block',
    'padding-block-start',
    'padding-block-end',
  ],
  border: [
    'border',
    'border-width',
    'border-style',
    'border-color',
    'border-top',
    'border-bottom',
    'border-left',
    'border-right',
    'border-inline',
    'border-inline-start',
    'border-inline-end',
    'border-block',
    'border-block-start',
    'border-block-end',
  ],
  borderColor: [
    'border-color',
    'border-top-color',
    'border-bottom-color',
    'border-left-color',
    'border-right-color',
    'border-inline-color',
    'border-inline-start-color',
    'border-inline-end-color',
    'border-block-color',
    'border-block-start-color',
    'border-block-end-color',
  ],
  radius: [
    'border-radius',
    'border-start-start-radius',
    'border-start-end-radius',
    'border-end-end-radius',
    'border-end-start-radius',
  ],
  background: [
    'background',
    'background-color',
    'background-image',
    'background-size',
    'background-position',
    'background-attachment',
  ],
  font: [
    'font',
    'font-family',
    'font-size',
    'font-weight',
    'font-style',
    'font-variant',
    'font-stretch',
    'line-height',
  ],
  inset: [
    'inset',
    'inset-inline',
    'inset-block',
    'top',
    'right',
    'bottom',
    'left',
    'inset-inline-start',
    'inset-inline-end',
    'inset-block-start',
    'inset-block-end',
  ],
  // Logical and physical axes are the same property, but `width` and `min-width`
  // are not: grouping them together would report `max-w-full` written next to
  // `min-inline-size` as a clash.
  inlineSize: ['width', 'inline-size'],
  blockSize: ['height', 'block-size'],
  minInlineSize: ['min-width', 'min-inline-size'],
  maxInlineSize: ['max-width', 'max-inline-size'],
  minBlockSize: ['min-height', 'min-block-size'],
  maxBlockSize: ['max-height', 'max-block-size'],
  gap: ['gap', 'row-gap', 'column-gap'],
  flex: ['flex', 'flex-grow', 'flex-shrink', 'flex-basis'],
  overflow: ['overflow', 'overflow-x', 'overflow-y'],
}
const EXPANSIONS = Object.create(null)
for (const group of Object.values(SHORTHANDS)) {
  for (const property of group) {
    if (!EXPANSIONS[property]) EXPANSIONS[property] = new Set()
    for (const other of group) EXPANSIONS[property].add(other)
  }
}
const affects = (property) => EXPANSIONS[property] ?? new Set([property])

// ---------- walk sources for class lists written on one element
function walk(directory, out = []) {
  for (const entry of fs.readdirSync(directory, { withFileTypes: true })) {
    const target = path.join(directory, entry.name)
    if (entry.isDirectory()) walk(target, out)
    else if (/\.tsx$/.test(entry.name)) out.push(target)
  }
  return out
}

const violations = []
const reported = new Set()
const isCoreAtom = (name) => !registeredUtilities.has(name) && !unlayeredClasses.has(name)
const isUtility = (name) => registeredUtilities.has(name) && !unlayeredClasses.has(name)

/**
 * Inspect one element's effective class set for the two ways it can go wrong:
 *  - ordering bet: a registered utility meets a core atom or an unlayered class on
 *    the same property, and the winner is not derivable from source order;
 *  - dead atom: an unlayered class always beats the utilities layer, so an atom
 *    written beside it asks for something the page never gets.
 */
function inspect(classes, source, line, note = '') {
  const claims = []
  for (const name of classes) {
    for (const rule of classRules.get(name) ?? []) claims.push({ name, ...rule })
  }
  for (let i = 0; i < claims.length; i += 1) {
    for (let j = i + 1; j < claims.length; j += 1) {
      const a = claims[i]
      const b = claims[j]
      if (a.name === b.name) continue
      const overlaps = [...affects(a.property)].some((property) =>
        affects(b.property).has(property),
      )
      if (!overlaps) continue
      // utility + its own variant (`base` then `base-…`) is the one sanctioned
      // override, and it resolves deterministically inside the utilities layer.
      if (
        (isUtility(a.name) && b.name.startsWith(`${a.name}-`)) ||
        (isUtility(b.name) && a.name.startsWith(`${b.name}-`))
      ) {
        continue
      }
      const owner =
        unlayeredClasses.has(a.name) && isCoreAtom(b.name)
          ? { claim: a, other: b }
          : unlayeredClasses.has(b.name) && isCoreAtom(a.name)
            ? { claim: b, other: a }
            : null
      // Two unlayered index.css classes resolve by source order inside one block.
      if (!owner && !isUtility(a.name) && !isUtility(b.name)) continue
      const pair = [a.property, b.property].sort().join('/')
      const key = `${source}:${line}:${note}:${[a.name, b.name].sort().join('+')}:${pair}`
      if (reported.has(key)) continue
      reported.add(key)
      const names = [a.name, b.name].sort().join(' + ')
      violations.push(
        owner
          ? `${source}:${line}${note} ${owner.other.name} is dead — .${owner.claim.name} already sets ${owner.claim.property}`
          : `${source}:${line}${note} [${pair}] ${names} — resolved by ${a.order > b.order ? a.name : b.name}`,
      )
    }
  }
}

for (const file of walk(path.join(root, 'src'))) {
  const source = path.relative(root, file).replaceAll('\\', '/')
  const lines = fs.readFileSync(file, 'utf8').split('\n')
  lines.forEach((line, index) => {
    for (const literal of line.matchAll(/['"]([-\w\s:/[\]()*.]+)['"]/g)) {
      const classes = literal[1]
        .trim()
        .split(/\s+/)
        .filter((token) => classRules.has(token))
      if (classes.length < 2) continue
      inspect(classes, source, index + 1)
    }
  })
}

// ---------- classes a component injects around the caller's className
// `cn('rose-three-loader', className)` is invisible to the call site, so a `size-*`
// written there can be beaten by a class the author never sees. Resolve the merge,
// then run the same inspection over the effective set.
function callerMergedBases(text) {
  const map = new Map()
  const functions = [...text.matchAll(/^export function (\w+)/gm)].map((m) => ({
    name: m[1],
    at: m.index,
  }))
  let cursor = 0
  while ((cursor = text.indexOf('cn(', cursor)) >= 0) {
    let depth = 0
    let end = cursor + 2
    for (; end < text.length; end += 1) {
      if (text[end] === '(') depth += 1
      else if (text[end] === ')') {
        depth -= 1
        if (!depth) break
      }
    }
    const span = text.slice(cursor, end + 1)
    cursor = end + 1
    if (!/\bclassName\b/.test(span)) continue
    const owner = functions.filter((item) => item.at < cursor).pop()
    if (!owner) continue
    if (!map.has(owner.name)) map.set(owner.name, new Set())
    for (const literal of span.matchAll(/['"]([-\w]+)['"]/g)) {
      if (classRules.has(literal[1])) map.get(owner.name).add(literal[1])
    }
  }
  return map
}

const sourceFiles = walk(path.join(root, 'src'))
const mergedByFile = new Map(
  sourceFiles.map((file) => [file, callerMergedBases(fs.readFileSync(file, 'utf8'))]),
)
const resolveModule = (specifier) => {
  const relative = specifier.startsWith('@/')
    ? specifier.replace('@/', 'src/')
    : path.relative(path.join(root, 'src'), path.resolve(root, 'src', specifier))
  return path.join(root, relative + '.tsx')
}

for (const file of sourceFiles) {
  const text = fs.readFileSync(file, 'utf8')
  const components = new Map()
  for (const statement of text.matchAll(/import\s*\{([^}]+)\}\s*from\s*['"]([^'"]+)['"]/g)) {
    const target = mergedByFile.get(resolveModule(statement[2]))
    if (!target || !target.size) continue
    for (const name of statement[1].split(',')) {
      const [imported, alias] = name.split(/\s+as\s+/).map((part) => part.trim())
      if (target.has(imported)) components.set(alias || imported, target.get(imported))
    }
  }
  if (!components.size) continue
  const source = path.relative(root, file).replaceAll('\\', '/')
  for (const [name, bases] of components) {
    for (const element of text.matchAll(new RegExp(`<${name}\\b([^>]*)>`, 'g'))) {
      // A nested element in an attribute slot (`trigger={<button …/>}`) belongs to a
      // different element; its classes are covered by the literal scan instead.
      if (element[1].includes('<')) continue
      const written = element[1].match(/className=['"]([^'"]+)['"]/)
      if (!written) continue
      const classes = [
        ...new Set([
          ...bases,
          ...written[1]
            .trim()
            .split(/\s+/)
            .filter((token) => classRules.has(token)),
        ]),
      ]
      if (classes.length < 2) continue
      const line = text.slice(0, element.index).split('\n').length
      inspect(classes, source, line, ` (via <${name}>)`)
    }
  }
}

if (violations.length) {
  console.error(`Cascade verification: FAIL (${violations.length})`)
  for (const violation of violations) console.error(`  ${violation}`)
  console.error(
    '\nMake the classes orthogonal (split the property out of the utility, or drop one side).',
  )
  console.error('Do not rely on utility/atom order — it is not derivable from source order.')
  process.exit(1)
}
console.log('Cascade verification: PASS')
