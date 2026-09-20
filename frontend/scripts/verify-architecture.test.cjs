'use strict'

const { test } = require('node:test')
const assert = require('node:assert/strict')
const fs = require('node:fs')
const os = require('node:os')
const path = require('node:path')
const { spawnSync } = require('node:child_process')

function verify(files) {
  const root = fs.mkdtempSync(path.join(os.tmpdir(), 'prelude-architecture-'))
  try {
    fs.writeFileSync(path.join(root, 'package.json'), '{}', 'utf8')
    for (const [name, source] of Object.entries(files)) {
      const file = path.join(root, 'src', name)
      fs.mkdirSync(path.dirname(file), { recursive: true })
      fs.writeFileSync(file, source, 'utf8')
    }
    return spawnSync(process.execPath, [path.join(__dirname, 'verify-architecture.cjs'), root], {
      encoding: 'utf8',
    })
  } finally {
    fs.rmSync(root, { recursive: true, force: true })
  }
}

test('rejects static, dynamic and re-export cross-feature internals', () => {
  for (const source of [
    "import type { Value } from '@/features/resume/types'",
    "const panel = import('@/features/resume/ResumeManagementPanel')",
    "export { value } from '../resume/internal'",
  ]) {
    const result = verify({ 'features/settings/example.ts': source })
    assert.equal(result.status, 1)
    assert.match(result.stderr, /cross-feature imports/)
  }
})

test('allows application composition, public feature imports and internal imports', () => {
  const result = verify({
    'app/shell.ts': "const panel = import('@/features/resume/ResumeManagementPanel')",
    'features/settings/example.ts':
      "import { value } from '@/features/resume'; import './internal'",
    'features/settings/internal.ts':
      "// import('@/features/resume/internal')\nconst text = \"import('@/features/resume/internal')\"",
    'shared/lib/cn.ts': "export { cn } from 'cn'",
  })
  assert.equal(result.status, 0, result.stderr)
})

test('rejects reverse dynamic dependencies and direct cn imports', () => {
  for (const [name, source, message] of [
    ['shared/tool.ts', "import('@/features/auth')", /shared cannot import/],
    ['features/auth/tool.ts', "import('@/app/main')", /features cannot import/],
    ['shared/ui/button.ts', "import { cn } from 'cn'", /cn must be imported/],
    ['shared/styles/test.css', '@import "../../features/auth/style.css";', /shared cannot import/],
  ]) {
    const result = verify({ [name]: source })
    assert.equal(result.status, 1)
    assert.match(result.stderr, message)
  }
})

/* The feature entry is the public surface, so two things have to hold: nothing is defined
   there, and nothing is exported there that no outsider reads. */
test('rejects implementation and wildcard re-export in a feature entry', () => {
  for (const source of [
    "export const fetchThing = () => 1\nexport { Thing } from './Thing'",
    "export * from './Thing'",
    "import { Thing } from './Thing'\nexport { Thing }",
  ]) {
    const result = verify({
      'features/report/index.ts': source,
      'features/report/Thing.ts': 'export const Thing = 1',
    })
    assert.equal(result.status, 1)
    assert.match(result.stderr, /public entry may only re-export named symbols/)
  }
})

test('rejects an entry symbol that nothing outside the feature imports', () => {
  const result = verify({
    'features/report/index.ts': "export { Thing } from './Thing'\nexport { unused } from './Thing'",
    'features/report/Thing.ts': 'export const Thing = 1\nexport const unused = 2',
    'app/page.tsx': "import { Thing } from '@/features/report'",
  })
  assert.equal(result.status, 1)
  assert.match(result.stderr, /unused is exported but nothing outside the feature imports it/)
  assert.doesNotMatch(result.stderr, /Thing is exported/)
})

test('counts a dynamic route import as a consumer of the barrel', () => {
  const result = verify({
    'features/analytics/index.ts': "export { AnalyticsPage } from './AnalyticsPage'",
    'features/analytics/AnalyticsPage.tsx': 'export const AnalyticsPage = 1',
    'app/main.tsx':
      "const route = { lazy: async () => ({ Component: (await import('@/features/analytics')).AnalyticsPage }) }",
  })
  assert.equal(result.status, 0, result.stderr)
})

test('accepts an entry that only re-exports names the app reads', () => {
  const result = verify({
    'features/report/index.ts': "export { Thing } from './Thing'",
    'features/report/Thing.ts': 'export const Thing = 1',
    'features/report/Panel.tsx': "import { Thing } from './Thing'",
    'app/page.tsx': "import { Thing } from '@/features/report'",
  })
  assert.equal(result.status, 0, result.stderr)
})
