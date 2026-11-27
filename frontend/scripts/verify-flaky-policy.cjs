'use strict'

const fs = require('node:fs')
const path = require('node:path')

const root = path.resolve(__dirname, '..')

function verifyRetries(source, file) {
  const retries = source.match(/\bretries:\s*(\d+)\s*,?/)
  if (!retries) {
    throw new Error(`${file}: Playwright retries must be an explicit zero`)
  }
  if (Number(retries[1]) !== 0) {
    throw new Error(
      `${file}: Playwright retries must remain zero; register a dated flaky-test record instead`,
    )
  }
}

function main() {
  const registryPath = path.join(root, 'tests/flaky-tests.json')
  const registry = JSON.parse(fs.readFileSync(registryPath, 'utf8'))
  if (!Array.isArray(registry.tests)) throw new Error('flaky-tests.json must contain a tests array')

  const configFiles = fs
    .readdirSync(root)
    .filter((file) => /^playwright.*\.config\.ts$/.test(file))
    .sort()
  if (configFiles.length === 0) throw new Error('No Playwright configuration found')
  for (const file of configFiles) {
    verifyRetries(fs.readFileSync(path.join(root, file), 'utf8'), file)
  }

  const files = []
  const walk = (directory) => {
    for (const entry of fs.readdirSync(directory, { withFileTypes: true })) {
      const file = path.join(directory, entry.name)
      if (entry.isDirectory()) walk(file)
      else if (entry.name.endsWith('.spec.ts')) files.push(file)
    }
  }
  walk(path.join(root, 'tests'))

  const skips = []
  for (const file of files) {
    const source = fs.readFileSync(file, 'utf8')
    const pattern = /test\.(skip|fixme)\(\s*['"]([^'"]+)['"]/g
    for (const match of source.matchAll(pattern)) {
      skips.push({
        file: path.relative(root, file).replaceAll('\\', '/'),
        title: match[2],
        kind: match[1],
      })
    }
  }

  const today = Date.now()
  for (const skip of skips) {
    const record = registry.tests.find(
      (item) => item.file === skip.file && item.title === skip.title,
    )
    if (!record) throw new Error(`unregistered flaky test: ${skip.file} :: ${skip.title}`)
    if (!record.owner || !record.issue || !/^\d{4}-\d{2}-\d{2}$/.test(record.expiresOn || '')) {
      throw new Error(
        `flaky test record requires owner, issue and expiresOn: ${skip.file} :: ${skip.title}`,
      )
    }
    if (Date.parse(`${record.expiresOn}T00:00:00Z`) < today) {
      throw new Error(`expired flaky test record: ${skip.file} :: ${skip.title}`)
    }
  }

  for (const record of registry.tests) {
    if (!skips.some((skip) => skip.file === record.file && skip.title === record.title)) {
      throw new Error(`stale flaky test record: ${record.file} :: ${record.title}`)
    }
  }

  console.log(`Flaky policy: PASS (${skips.length} registered)`)
}

if (require.main === module) main()

module.exports = { verifyRetries }
