#!/usr/bin/env node
'use strict'

const fs = require('node:fs')
const path = require('node:path')

const root = path.resolve(__dirname, '..')
const sourceRoot = path.join(root, 'src')
const allowedRoots = new Set(['app', 'devtools', 'features', 'shared'])
const blockedPackages = [
  'vue',
  'vue-router',
  'pinia',
  'reka-ui',
  'radix-vue',
  'vue-tsc',
  'html2canvas',
  'jspdf',
]
const sourceExtensions = new Set(['.ts', '.tsx', '.js', '.jsx', '.vue'])
const importPattern = /(?:import|export)\s+(?:type\s+)?(?:[^'";]*?\s+from\s+)?['"]([^'"]+)['"]/g
const violations = []

function walk(directory) {
  return fs.readdirSync(directory, { withFileTypes: true }).flatMap((entry) => {
    const target = path.join(directory, entry.name)
    return entry.isDirectory() ? walk(target) : [target]
  })
}

for (const entry of fs.readdirSync(sourceRoot, { withFileTypes: true })) {
  if (entry.isDirectory() && !allowedRoots.has(entry.name)) {
    violations.push(`src/${entry.name}: source must live under app, devtools, features, or shared`)
  }
}

for (const file of walk(sourceRoot).filter((item) => sourceExtensions.has(path.extname(item)))) {
  const relative = path.relative(sourceRoot, file).replaceAll('\\', '/')
  if (file.endsWith('.vue')) violations.push(`${relative}: Vue source is forbidden`)
  const source = fs.readFileSync(file, 'utf8')
  let match
  while ((match = importPattern.exec(source)) !== null) {
    const specifier = match[1]
    const resolved = specifier.startsWith('@/')
      ? specifier.slice(2)
      : specifier.startsWith('.')
        ? path.posix.normalize(path.posix.join(path.posix.dirname(relative), specifier))
        : null
    if (!resolved) continue
    if (relative.startsWith('shared/') && /^(app|features)(\/|$)/.test(resolved)) {
      violations.push(`${relative}: shared cannot import ${specifier}`)
    }
    if (relative.startsWith('features/') && /^app(\/|$)/.test(resolved)) {
      violations.push(`${relative}: features cannot import ${specifier}`)
    }
    if (relative.startsWith('features/') && resolved.startsWith('features/')) {
      const sourceFeature = relative.split('/')[1]
      const targetParts = resolved.split('/')
      if (targetParts[1] !== sourceFeature && targetParts.length > 2) {
        violations.push(`${relative}: cross-feature imports must use @/features/${targetParts[1]}`)
      }
    }
    if (relative.startsWith('devtools/') && /^app(\/|$)/.test(resolved)) {
      violations.push(`${relative}: devtools cannot import ${specifier}`)
    }
  }
}

const packageJson = JSON.parse(fs.readFileSync(path.join(root, 'package.json'), 'utf8'))
const declared = { ...packageJson.dependencies, ...packageJson.devDependencies }
for (const name of blockedPackages) {
  if (declared[name]) violations.push(`package.json: blocked dependency ${name}`)
}

if (violations.length) {
  console.error(`Architecture verification: FAIL (${violations.length})`)
  for (const violation of violations) console.error(`  ${violation}`)
  process.exit(1)
}
console.log('Architecture verification: PASS')
