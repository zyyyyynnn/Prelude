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
