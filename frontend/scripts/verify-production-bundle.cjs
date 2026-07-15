#!/usr/bin/env node
'use strict'

const fs = require('node:fs')
const path = require('node:path')

const textExtensions = new Set(['.css', '.html', '.js', '.json'])
const forbiddenMarkers = ['/components-lab', 'Component Lab', 'devtools/component-lab']

function listBundleFiles(root) {
  if (!fs.existsSync(root)) return []
  const files = []
  const visit = (directory) => {
    for (const entry of fs.readdirSync(directory, { withFileTypes: true })) {
      const target = path.join(directory, entry.name)
      if (entry.isDirectory()) visit(target)
      else if (textExtensions.has(path.extname(entry.name))) files.push(target)
    }
  }
  visit(root)
  return files
}

function verifyProductionBundle(distRoot, options = {}) {
  if (!fs.existsSync(distRoot)) return [`production bundle does not exist: ${distRoot}`]

  const violations = []
  const bundleFiles = listBundleFiles(distRoot)
  const indexPath = path.join(distRoot, 'index.html')
  const indexSource = fs.existsSync(indexPath) ? fs.readFileSync(indexPath, 'utf8') : ''
  if (indexSource.includes('vendor-pdf')) {
    violations.push('index.html eagerly loads vendor-pdf')
  }
  if (
    options.requireDeferredPdf &&
    !bundleFiles.some((file) => path.basename(file).startsWith('vendor-pdf-'))
  ) {
    violations.push('deferred vendor-pdf chunk is missing')
  }

  for (const file of bundleFiles) {
    const source = fs.readFileSync(file, 'utf8')
    for (const marker of forbiddenMarkers) {
      if (source.includes(marker)) {
        violations.push(`${path.relative(distRoot, file)} contains ${JSON.stringify(marker)}`)
      }
    }
  }
  return violations
}

function main() {
  const distRoot = path.resolve(__dirname, '..', 'dist')
  const violations = verifyProductionBundle(distRoot, { requireDeferredPdf: true })
  if (violations.length === 0) {
    console.log('Production bundle verification: PASS')
    return
  }

  console.error(`Production bundle verification: FAIL (${violations.length} violations)`)
  for (const item of violations) console.error(`  ${item}`)
  process.exitCode = 1
}

module.exports = { verifyProductionBundle }

if (require.main === module) main()
