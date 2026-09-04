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

function walkCss(directory) {
  return fs.readdirSync(directory, { withFileTypes: true }).flatMap((entry) => {
    const target = path.join(directory, entry.name)
    if (entry.isDirectory()) return walkCss(target)
    return entry.name.endsWith('.css') ? [target] : []
  })
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
const rootBlock = extractBlock(css, ':root')
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

if (violations.length) {
  console.error(`UI token verification: FAIL (${violations.length})`)
  for (const violation of violations) console.error(`  ${violation}`)
  process.exit(1)
}
console.log(`UI token verification: PASS (${declared.size} declarations)`)
