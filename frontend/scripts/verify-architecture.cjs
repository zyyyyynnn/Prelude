#!/usr/bin/env node
'use strict'

const fs = require('node:fs')
const path = require('node:path')

const legacyTopLevelDirectories = new Set([
  'api',
  'assets',
  'components',
  'composables',
  'lib',
  'router',
  'schemas',
  'stores',
  'styles',
  'utils',
  'views',
])
const allowedTopLevelDirectories = new Set(['app', 'features', 'shared', 'devtools'])

const sourceExtensions = new Set(['.ts', '.tsx', '.js', '.jsx', '.vue'])
const importPattern =
  /(?:\b(?:import|export)\s+(?:type\s+)?(?:[^'";]*?\s+from\s+)?|\bimport\s*\()\s*['"]([^'"]+)['"]/g

function toPosix(value) {
  return value.replace(/\\/g, '/')
}

function listSourceFiles(root) {
  if (!fs.existsSync(root)) return []
  const files = []
  const visit = (directory) => {
    for (const entry of fs.readdirSync(directory, { withFileTypes: true })) {
      const target = path.join(directory, entry.name)
      if (entry.isDirectory()) visit(target)
      else if (sourceExtensions.has(path.extname(entry.name))) files.push(target)
    }
  }
  visit(root)
  return files
}

function extractImports(source) {
  const imports = []
  let match
  while ((match = importPattern.exec(source)) !== null) imports.push(match[1])
  return imports
}

function resolveSourceImport(relativeFile, specifier) {
  if (specifier.startsWith('@/')) return specifier.slice(2)
  if (!specifier.startsWith('.')) return null
  return toPosix(path.normalize(path.join(path.dirname(relativeFile), specifier)))
}

function violation(rule, file, message) {
  return { rule, file: toPosix(file), message }
}

function verifyArchitecture(sourceRoot) {
  const violations = []
  if (!fs.existsSync(sourceRoot)) {
    return [violation('source-root', '.', `source root does not exist: ${sourceRoot}`)]
  }

  for (const entry of fs.readdirSync(sourceRoot, { withFileTypes: true })) {
    if (!entry.isDirectory()) continue

    if (legacyTopLevelDirectories.has(entry.name)) {
      violations.push(
        violation(
          'legacy-top-level',
          entry.name,
          `legacy top-level directory must be migrated or removed: ${entry.name}`,
        ),
      )
    } else if (!allowedTopLevelDirectories.has(entry.name)) {
      violations.push(
        violation(
          'unknown-top-level',
          entry.name,
          `source directory is outside app/features/shared/devtools: ${entry.name}`,
        ),
      )
    }
  }

  for (const absoluteFile of listSourceFiles(sourceRoot)) {
    const relativeFile = toPosix(path.relative(sourceRoot, absoluteFile))
    const source = fs.readFileSync(absoluteFile, 'utf8')
    const imports = extractImports(source)

    for (const specifier of imports) {
      const resolved = resolveSourceImport(relativeFile, specifier)
      if (!resolved) continue

      if (
        relativeFile.startsWith('shared/') &&
        /^(?:app|features|devtools)(?:\/|$)/.test(resolved)
      ) {
        violations.push(
          violation(
            'shared-dependency-direction',
            relativeFile,
            `shared cannot import ${specifier}`,
          ),
        )
      }

      if (relativeFile.startsWith('features/') && /^(?:app|devtools)(?:\/|$)/.test(resolved)) {
        violations.push(
          violation(
            'feature-dependency-direction',
            relativeFile,
            `features cannot import ${specifier}`,
          ),
        )
      }

      const owner = relativeFile.match(/^features\/([^/]+)\//)?.[1]
      const target = resolved.match(/^features\/([^/]+)(?:\/(.*))?$/)
      if (owner === 'report' && target?.[1] === 'interview') {
        violations.push(
          violation(
            'report-independence',
            relativeFile,
            'report must receive data from callers and cannot import interview',
          ),
        )
      }
      if (owner && target && target[1] !== owner && target[2]) {
        violations.push(
          violation(
            'cross-feature-deep-import',
            relativeFile,
            `feature ${owner} must import ${target[1]} through @/features/${target[1]}`,
          ),
        )
      }
    }

    if (relativeFile.startsWith('shared/')) {
      for (const specifier of imports) {
        if (/^(?:pinia|vue-router)(?:\/|$)/.test(specifier)) {
          violations.push(
            violation(
              'shared-runtime-dependency',
              relativeFile,
              `shared cannot depend on ${specifier}`,
            ),
          )
        }
      }
    }

    const meaningfulLines = source
      .split(/\r?\n/)
      .map((line) => line.trim())
      .filter((line) => line && !line.startsWith('//'))
    const isFeaturePublicEntry = /^features\/[^/]+\/index\.ts$/.test(relativeFile)
    const isCompatibilityReexport =
      !isFeaturePublicEntry &&
      meaningfulLines.length > 0 &&
      meaningfulLines.every((line) =>
        /^export\s+(?:\*|\{[^}]+\})\s+from\s+['"]@\/features\/[^'"]+['"];?$/.test(line),
      )
    if (isCompatibilityReexport) {
      violations.push(
        violation(
          'compatibility-reexport',
          relativeFile,
          'compatibility-only feature re-export files are forbidden',
        ),
      )
    }
  }

  return violations
}

function main() {
  const sourceRoot = path.resolve(__dirname, '..', 'src')
  const violations = verifyArchitecture(sourceRoot)
  if (violations.length === 0) {
    console.log('Architecture verification: PASS')
    return
  }

  console.error(`Architecture verification: FAIL (${violations.length} violations)`)
  for (const item of violations) {
    console.error(`  [${item.rule}] ${item.file}: ${item.message}`)
  }
  process.exitCode = 1
}

module.exports = { verifyArchitecture }

if (require.main === module) main()
