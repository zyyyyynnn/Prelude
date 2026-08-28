#!/usr/bin/env node
'use strict'

const fs = require('node:fs')
const path = require('node:path')

const root = path.resolve(__dirname, '..')
const sourceRoot = path.join(root, 'src')
const violations = []

function walk(directory) {
  return fs.readdirSync(directory, { withFileTypes: true }).flatMap((entry) => {
    const target = path.join(directory, entry.name)
    return entry.isDirectory() ? walk(target) : [target]
  })
}

for (const file of walk(sourceRoot).filter((item) => /\.(ts|tsx)$/.test(item))) {
  const source = fs.readFileSync(file, 'utf8')
  const relative = path.relative(root, file).replaceAll('\\', '/')
  const rules = [
    [/<[a-z][^>]*\btitle\s*=/, 'native title tooltip'],
    [/window\.confirm\s*\(/, 'native confirm dialog'],
    [/(?:#[0-9a-f]{3,8}|rgba?\(|hsla?\()/i, 'hard-coded color'],
    [/transition-all/, 'transition-all'],
  ]
  for (const [pattern, label] of rules) {
    if (pattern.test(source)) violations.push(`${relative}: ${label}`)
  }
}

const overlays = fs.readFileSync(path.join(sourceRoot, 'shared', 'ui', 'overlay.tsx'), 'utf8')
const styles = fs.readFileSync(path.join(sourceRoot, 'shared', 'styles', 'index.css'), 'utf8')
if (!overlays.includes('Tooltip.Provider') && !overlays.includes('Tooltip.Root')) {
  violations.push('shared/ui/overlay.tsx: Base UI tooltip primitive is required')
}
const tooltipRule = styles.match(/\.prelude-tooltip\s*\{([^}]+)\}/)?.[1] ?? ''
for (const required of [
  'background: var(--color-text-primary)',
  'color: var(--color-surface)',
  'border: 1px solid var(--color-border-warm)',
  'box-shadow: var(--shadow-whisper)',
]) {
  if (!tooltipRule.includes(required))
    violations.push(`shared/styles/index.css: tooltip must include ${required}`)
}

if (violations.length) {
  console.error(`UI guardrails: FAIL (${violations.length})`)
  for (const violation of violations) console.error(`  ${violation}`)
  process.exit(1)
}
console.log('UI guardrails: PASS')
