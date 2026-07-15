const assert = require('node:assert/strict')
const fs = require('node:fs')
const os = require('node:os')
const path = require('node:path')
const test = require('node:test')

const { verifyProductionBundle } = require('../../scripts/verify-production-bundle.cjs')

function createBundle(files) {
  const root = fs.mkdtempSync(path.join(os.tmpdir(), 'prelude-production-bundle-'))
  for (const [relativePath, source] of Object.entries(files)) {
    const target = path.join(root, relativePath)
    fs.mkdirSync(path.dirname(target), { recursive: true })
    fs.writeFileSync(target, source, 'utf8')
  }
  return root
}

void test('accepts a production bundle without development tools', (t) => {
  const root = createBundle({ 'index.html': '<script src="/assets/index.js"></script>' })
  t.after(() => fs.rmSync(root, { recursive: true, force: true }))

  assert.deepEqual(verifyProductionBundle(root), [])
})

void test('rejects Component Lab markers in a production bundle', (t) => {
  const root = createBundle({
    'assets/index.js': 'const route = "/components-lab"; const title = "Component Lab"',
  })
  t.after(() => fs.rmSync(root, { recursive: true, force: true }))

  assert.equal(verifyProductionBundle(root).length, 2)
})

void test('rejects an eagerly preloaded PDF vendor chunk', (t) => {
  const root = createBundle({
    'index.html': '<link rel="modulepreload" href="/assets/vendor-pdf-123.js">',
    'assets/vendor-pdf-123.js': 'export const pdf = true',
  })
  t.after(() => fs.rmSync(root, { recursive: true, force: true }))

  assert.deepEqual(verifyProductionBundle(root), ['index.html eagerly loads vendor-pdf'])
})

void test('requires a deferred PDF vendor chunk for the live production build', (t) => {
  const root = createBundle({
    'index.html': '<script type="module" src="/assets/index.js"></script>',
    'assets/vendor-pdf-123.js': 'export const pdf = true',
  })
  t.after(() => fs.rmSync(root, { recursive: true, force: true }))

  assert.deepEqual(verifyProductionBundle(root, { requireDeferredPdf: true }), [])
})
