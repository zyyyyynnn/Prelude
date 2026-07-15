const assert = require('node:assert/strict')
const fs = require('node:fs')
const os = require('node:os')
const path = require('node:path')
const test = require('node:test')

const { verifyArchitecture } = require('../../scripts/verify-architecture.cjs')

function createFixture(files) {
  const root = fs.mkdtempSync(path.join(os.tmpdir(), 'prelude-architecture-'))
  for (const [relativePath, source] of Object.entries(files)) {
    const target = path.join(root, relativePath)
    fs.mkdirSync(path.dirname(target), { recursive: true })
    fs.writeFileSync(target, source, 'utf8')
  }
  return root
}

void test('accepts app, features, shared and devtools with public feature imports', (t) => {
  const root = createFixture({
    'app/main.ts': "import { InterviewPage } from '@/features/interview'\n",
    'features/interview/index.ts':
      "export { default as InterviewPage } from './pages/InterviewPage.vue'\n",
    'features/interview/pages/InterviewPage.vue': "import { http } from '@/shared/api'\n",
    'shared/api/index.ts': 'export const http = {}\n',
    'devtools/component-lab/index.ts': "import { Button } from '@/shared/ui/button'\n",
  })
  t.after(() => fs.rmSync(root, { recursive: true, force: true }))

  assert.deepEqual(verifyArchitecture(root), [])
})

void test('rejects legacy top-level source directories', (t) => {
  const root = createFixture({
    'api/http.ts': 'export const http = {}\n',
    'app/main.ts': 'export {}\n',
  })
  t.after(() => fs.rmSync(root, { recursive: true, force: true }))

  const violations = verifyArchitecture(root)
  assert.ok(violations.some((item) => item.rule === 'legacy-top-level'))
})

void test('rejects unknown top-level source directories', (t) => {
  const root = createFixture({
    'widgets/LegacyWidget.vue': '<template><div /></template>\n',
  })
  t.after(() => fs.rmSync(root, { recursive: true, force: true }))

  const violations = verifyArchitecture(root)
  assert.ok(violations.some((item) => item.rule === 'unknown-top-level'))
})

void test('rejects shared imports from app or features', (t) => {
  const root = createFixture({
    'shared/api/http.ts': "import { useAuthStore } from '@/features/auth'\n",
    'features/auth/index.ts': 'export const useAuthStore = () => null\n',
  })
  t.after(() => fs.rmSync(root, { recursive: true, force: true }))

  const violations = verifyArchitecture(root)
  assert.ok(violations.some((item) => item.rule === 'shared-dependency-direction'))
})

void test('rejects feature imports from app or devtools', (t) => {
  const root = createFixture({
    'features/interview/pages/InterviewPage.vue': [
      "import router from '@/app/router'",
      "import Lab from '@/devtools/component-lab/ComponentLabView.vue'",
    ].join('\n'),
    'app/router.ts': 'export default {}\n',
    'devtools/component-lab/ComponentLabView.vue': '<template><div /></template>\n',
  })
  t.after(() => fs.rmSync(root, { recursive: true, force: true }))

  const violations = verifyArchitecture(root)
  assert.equal(violations.filter((item) => item.rule === 'feature-dependency-direction').length, 2)
})

void test('rejects shared dependencies on Pinia and Vue Router', (t) => {
  const root = createFixture({
    'shared/api/http.ts': [
      "import { defineStore } from 'pinia'",
      "import router from 'vue-router'",
    ].join('\n'),
  })
  t.after(() => fs.rmSync(root, { recursive: true, force: true }))

  const violations = verifyArchitecture(root)
  assert.equal(violations.filter((item) => item.rule === 'shared-runtime-dependency').length, 2)
})

void test('rejects cross-feature deep imports but allows public entries', (t) => {
  const root = createFixture({
    'features/interview/components/Composer.vue': [
      "import { fetchProviders } from '@/features/settings/api/llm'",
      "import type { ResumeItem } from '@/features/resume'",
    ].join('\n'),
    'features/settings/api/llm.ts': 'export const fetchProviders = () => []\n',
    'features/resume/index.ts': 'export {}\n',
  })
  t.after(() => fs.rmSync(root, { recursive: true, force: true }))

  const violations = verifyArchitecture(root)
  assert.equal(violations.filter((item) => item.rule === 'cross-feature-deep-import').length, 1)
})

void test('keeps report independent from interview state', (t) => {
  const root = createFixture({
    'features/report/components/Report.vue':
      "import { useInterviewSessionStore } from '@/features/interview'\n",
    'features/interview/index.ts': 'export const useInterviewSessionStore = () => null\n',
  })
  t.after(() => fs.rmSync(root, { recursive: true, force: true }))

  const violations = verifyArchitecture(root)
  assert.ok(violations.some((item) => item.rule === 'report-independence'))
})

void test('rejects compatibility files that only re-export a feature implementation', (t) => {
  const root = createFixture({
    'shared/useInterviewWorkspace.ts':
      "export * from '@/features/interview/composables/useInterviewWorkspace'\n",
  })
  t.after(() => fs.rmSync(root, { recursive: true, force: true }))

  const violations = verifyArchitecture(root)
  assert.ok(violations.some((item) => item.rule === 'compatibility-reexport'))
})
