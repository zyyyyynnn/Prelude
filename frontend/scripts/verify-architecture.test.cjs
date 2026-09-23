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
  assert.match(result.stderr, /unused is exported but nothing outside imports it/)
  assert.doesNotMatch(result.stderr, /Thing is exported/)
})

test('rejects a design-system import that names a file instead of the surface', () => {
  const result = verify({
    'shared/ui/index.ts': "export { Button } from './button'",
    'shared/ui/button.tsx': 'export const Button = 1',
    'features/report/Panel.tsx': "import { Button } from '@/shared/ui/button'",
  })
  assert.equal(result.status, 1)
  assert.match(result.stderr, /imports @\/shared\/ui\/button — go through @\/shared\/ui/)
})

test('accepts a design-system import taken through the surface', () => {
  const result = verify({
    'shared/ui/index.ts': "export { Button } from './button'",
    'shared/ui/button.tsx': 'export const Button = 1',
    'features/report/Panel.tsx': "import { Button } from '@/shared/ui'",
  })
  assert.equal(result.status, 0, result.stderr)
})

/* A design system with no entry is the whole defect this rule guards, so the absence has to
   be a violation rather than a silent skip. */
test('rejects a design system that has no public surface at all', () => {
  const result = verify({
    'shared/ui/button.tsx': 'export const Button = 1',
    'features/report/Panel.tsx': "import { Button } from '@/shared/ui/button'",
  })
  assert.equal(result.status, 1)
  assert.match(result.stderr, /shared\/ui has no index.ts/)
})

/* Siblings reach each other directly; the ban is on outsiders. `Icon` stays out of the barrel
   because only `Button` consumes it — that is the same rule the surface itself is held to. */
test('allows a design-system file to import its own sibling', () => {
  const result = verify({
    'shared/ui/index.ts': "export { Button } from './button'",
    'shared/ui/button.tsx': "import { Icon } from './icon'\nexport const Button = Icon",
    'shared/ui/icon.tsx': 'export const Icon = 1',
    'features/report/Panel.tsx': "import { Button } from '@/shared/ui'",
  })
  assert.equal(result.status, 0, result.stderr)
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

/* The composition root may name a route module directly — that is what keeps a route out of
   the entry chunk — but only one the surface's own entry registers. */
test('allows the composition root to load a route module the entry registers', () => {
  const result = verify({
    'features/settings/index.ts': "export { SettingsProvider } from './SettingsModal'",
    'features/settings/SettingsModal.tsx': 'export const SettingsProvider = 1',
    'app/main.tsx':
      "const [{ SettingsProvider }] = await Promise.all([import('@/features/settings/SettingsModal')])",
  })
  assert.equal(result.status, 0, result.stderr)
})

test('rejects a composition-root import of a file the entry does not register', () => {
  const result = verify({
    'features/settings/index.ts': "export { useSettings } from './settings-context'",
    'features/settings/settings-context.tsx': 'export const useSettings = 1',
    'features/settings/SecretPanel.tsx': 'export const SecretPanel = 1',
    'app/main.tsx': "const load = () => import('@/features/settings/SecretPanel')",
  })
  assert.equal(result.status, 1)
  assert.match(result.stderr, /or register SecretPanel in features\/settings\/index\.ts/)
})

/* A feature reading a neighbour's file is the coupling the entry exists to prevent, registered
   in the entry or not: the exemption is about how a route loads, not about who may couple. */
test('rejects a feature import of a neighbour file even when registered', () => {
  const result = verify({
    'features/settings/index.ts': "export { SettingsProvider } from './SettingsModal'",
    'features/settings/SettingsModal.tsx': 'export const SettingsProvider = 1',
    'features/interview/Page.tsx': "import { x } from '@/features/settings/SettingsModal'",
  })
  assert.equal(result.status, 1)
  assert.match(
    result.stderr,
    /imports @\/features\/settings\/SettingsModal — go through @\/features\/settings/,
  )
})

/* The rule is about the surface, not the keyword: naming a file through `import()` bypasses the
   entry exactly as `from` does. The first version of this scan matched the literal text
   `from '@/…'` and therefore read the router's lazy pages as clean. */
test('rejects a dynamic import that names a file inside a surface', () => {
  const result = verify({
    'features/settings/index.ts': "export { SettingsProvider } from './SettingsModal'",
    'features/settings/SettingsModal.tsx': 'export const SettingsProvider = 1',
    'features/report/Panel.tsx': "const load = () => import('@/features/settings/SettingsModal')",
  })
  assert.equal(result.status, 1)
  assert.match(
    result.stderr,
    /imports @\/features\/settings\/SettingsModal — go through @\/features\/settings/,
  )
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

/* A template span's literal part is as much a claim about the product as a string literal.
   This source used to be read through `ts.isTemplateLiteralLiteralPart`, which is not a
   function in the TypeScript API — the optional call made the third claimed source silently
   absent rather than failing. */
test('rejects shared vocabulary carried by a substituted template literal', () => {
  const result = verify({
    'features/report/Thing.ts': 'export const Thing = 1',
    'shared/ui/index.ts': "export { label } from './label'",
    'shared/ui/label.tsx': 'export const label = (id) => `report/${id}`',
    'app/page.tsx': "import { label } from '@/shared/ui'",
  })
  assert.equal(result.status, 1)
  assert.match(result.stderr, /FAIL \(1\)/)
  assert.match(result.stderr, /shared\/ui\/label\.tsx: names the feature "report\/"/)
})
