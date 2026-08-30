#!/usr/bin/env node
'use strict'

const fs = require('node:fs')
const path = require('node:path')

const root = path.resolve(__dirname, '..')
const schema = JSON.parse(fs.readFileSync(path.join(root, 'tokens', 'ui-tokens.json'), 'utf8'))
const stylesRoot = path.join(root, 'src', 'shared', 'styles')
const css = fs
  .readdirSync(stylesRoot)
  .filter((name) => name.endsWith('.css'))
  .map((name) => fs.readFileSync(path.join(stylesRoot, name), 'utf8'))
  .join('\n')
const declared = new Map(
  [...css.matchAll(/(--[\w-]+)\s*:\s*([^;]+);/g)].map((match) => [match[1], match[2].trim()]),
)
const violations = []

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
const zValues = [...declared.entries()]
  .filter(([name]) => name.startsWith('--z-index-'))
  .map(([, value]) => value)
if (new Set(zValues).size !== zValues.length) violations.push('z-index token values must be unique')

for (const [index, line] of css.split(/\r?\n/).entries()) {
  const match = line.match(/^\s*box-shadow\s*:\s*(.+?);\s*$/)
  if (!match) continue
  const value = match[1].trim()
  if (/^none$/i.test(value) || /var\(--shadow-/.test(value)) continue
  violations.push(`raw box-shadow value at line ${index + 1}: ${value}`)
}

if (violations.length) {
  console.error(`UI token verification: FAIL (${violations.length})`)
  for (const violation of violations) console.error(`  ${violation}`)
  process.exit(1)
}
console.log(`UI token verification: PASS (${declared.size} declarations)`)
