'use strict'

const { test } = require('node:test')
const assert = require('node:assert/strict')
const { validateTitle } = require('./verify-governance.cjs')

test('accepts valid conventional commit titles', () => {
  const validTitles = [
    'feat(resume): establish agent-assisted patch review and workspace controls',
    'fix(frontend): resolve cascade color collision on field error text',
    'arch(backend): tighten cross-module boundaries with named interfaces',
    'refactor(frontend): standardize className composition with cn',
    'test(jobs): assert registered scheduler tasks rather than annotation existence',
    'chore(governance): establish GitHub contribution templates',
    'build(frontend): restore the unified Vite+ toolchain',
    'ci(governance): enforce commit title and quality gates',
    'docs(setup): clarify docker container full integration commands',
    'security(platform): establish application security baseline',
  ]

  for (const title of validTitles) {
    const error = validateTitle(title)
    assert.equal(error, null, `Expected "${title}" to be valid, got error: ${error}`)
  }
})

test('rejects titles containing Chinese or non-ASCII characters', () => {
  const invalidTitles = [
    'feat(resume): 落地简历工作区链路与 RemixIcon 图标体系',
    'fix(frontend): 工具轨迹按上游 ToolChips 结构重做并对齐参考图',
    'test(jobs): 断言登记任务而非注解存在',
  ]

  for (const title of invalidTitles) {
    const error = validateTitle(title)
    assert.match(error, /ASCII/, `Expected "${title}" to fail with ASCII error`)
  }
})

test('rejects titles containing phase, wip, or process markers', () => {
  const invalidTitles = [
    'feat(resume): wip implement patch review',
    'fix(ui): temp patch for button layout',
    'chore(backend): phase1 cleanup',
  ]

  for (const title of invalidTitles) {
    const error = validateTitle(title)
    assert.match(
      error,
      /phase, temp, or process markers/,
      `Expected "${title}" to fail with process error`,
    )
  }
})

test('rejects invalid types or malformed structures', () => {
  assert.match(validateTitle('update(frontend): something'), /invalid type/)
  assert.match(validateTitle('feat: missing scope'), /must match <type>\(<scope>\)/)
  assert.match(validateTitle('feat(resume): UpperCaseStart'), /must start with a lowercase letter/)
  assert.match(validateTitle(''), /non-empty string/)
})
