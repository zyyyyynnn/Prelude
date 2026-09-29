#!/usr/bin/env node
'use strict'

/**
 * Repository governance gate for commit subjects and PR titles.
 *
 * The rule it enforces is the one written in CONTRIBUTING.md (提交与标题):
 *   <type>(<scope>): <semantic title in lowercase imperative english>
 * with no phase or process markers, and PR/Issue bodies in Chinese. Issue and PR title formats
 * are additionally fixed by .github/ISSUE_TEMPLATE/*.
 *
 * Rules:
 * 1. Type must be one of: feat, fix, arch, refactor, test, chore, build, ci, docs, security.
 * 2. Scope must be lowercase alphanumeric with hyphens/underscores.
 * 3. Title must be English (no non-ASCII), start with a lowercase alphanumeric character.
 * 4. Phase markers or process tags (wip, temp, patch1, etc.) are strictly forbidden.
 */

const { execSync } = require('node:child_process')

const ALLOWED_TYPES = new Set([
  'feat',
  'fix',
  'arch',
  'refactor',
  'test',
  'chore',
  'build',
  'ci',
  'docs',
  'security',
])

const FORBIDDEN_PHASE_PATTERN = /\b(wip|temp|patch[0-9]|phase[0-9]|tmp)\b/i
const CONVENTIONAL_STRUCTURE = /^([a-z]+)\(([a-z0-9_-]+)\):\s+(.+)$/

function validateTitle(title) {
  if (!title || typeof title !== 'string') {
    return 'title must be a non-empty string'
  }

  const trimmed = title.trim()
  if (/[^\x20-\x7E]/.test(trimmed)) {
    return 'title must contain only ASCII characters (no Chinese or special glyphs)'
  }

  if (FORBIDDEN_PHASE_PATTERN.test(trimmed)) {
    return 'title must not contain phase, temp, or process markers (wip, temp, patch, phase)'
  }

  const match = trimmed.match(CONVENTIONAL_STRUCTURE)
  if (!match) {
    return 'title must match <type>(<scope>): <semantic title in lowercase imperative english>'
  }

  const [, type, , description] = match
  if (!ALLOWED_TYPES.has(type)) {
    return `invalid type "${type}". Allowed: ${Array.from(ALLOWED_TYPES).join(', ')}`
  }

  if (/^[A-Z]/.test(description)) {
    return 'description must start with a lowercase letter or digit'
  }

  if (!/^[a-z0-9][a-zA-Z0-9 _.,'+/-]+$/.test(description)) {
    return 'description contains invalid characters'
  }

  return null
}

function verifyCommits(baseRef = 'origin/main') {
  let commits = []
  try {
    const raw = execSync(`git log ${baseRef}..HEAD --format=%s`, {
      encoding: 'utf8',
      stdio: ['pipe', 'pipe', 'ignore'],
    }).trim()
    if (raw) {
      commits = raw.split(/\r?\n/).filter(Boolean)
    }
  } catch {
    /* A missing base ref is a checkout problem, not a clean run. Falling back to the last
       commit here would let a branch of any length pass on the strength of one subject, so the
       gate says what happened and leaves the step red until the range can be resolved. */
    return [
      `cannot resolve ${baseRef}: fetch the base branch (checkout with fetch-depth: 0) ` +
        'or pass the range explicitly',
    ]
  }

  const errors = []
  for (const commit of commits) {
    // Ignore GitHub auto-merge squash commits that append PR numbers
    const cleanCommit = commit.replace(/\s+\(#[0-9]+\)$/, '')
    const error = validateTitle(cleanCommit)
    if (error) {
      errors.push(`Commit "${commit}": ${error}`)
    }
  }

  if (process.env.PR_TITLE) {
    const prError = validateTitle(process.env.PR_TITLE)
    if (prError) {
      errors.push(`PR Title "${process.env.PR_TITLE}": ${prError}`)
    }
  }

  return errors
}

if (require.main === module) {
  const errors = verifyCommits()
  if (errors.length > 0) {
    console.error('Governance verification: FAIL')
    for (const error of errors) {
      console.error(`  - ${error}`)
    }
    process.exit(1)
  }
  console.log('Governance verification: PASS')
}

module.exports = {
  validateTitle,
  verifyCommits,
}
