#!/usr/bin/env node
'use strict'

/**
 * Automates GitHub Development native issue-PR association.
 *
 * Adheres to AGENTS.md rule:
 * "PR 正文只描述最终交付、必要架构与稳定契约，禁止写入 Issue 编号或链接，禁止 Closes、Fixes、Resolves、Refs 等关联标识；
 *  Issue 关系只通过 GitHub Development 原生关系维护；验证事实只由 GitHub Checks / Actions 表达。"
 *
 * Usage:
 *   node scripts/link-issue.cjs <issueNumber> <prNumber>
 */

const { execFileSync } = require('node:child_process')

const [issueNum, prNum] = process.argv.slice(2)
if (!issueNum || !prNum) {
  console.error('Usage: node scripts/link-issue.cjs <issueNumber> <prNumber>')
  process.exit(1)
}

function runGh(args) {
  return execFileSync('gh', args, { encoding: 'utf8', stdio: ['pipe', 'pipe', 'pipe'] }).trim()
}

try {
  console.log(`Resolving node IDs for Issue #${issueNum} and PR #${prNum}...`)

  const issueRaw = runGh(['issue', 'view', String(issueNum), '--json', 'id,title'])
  const issue = JSON.parse(issueRaw)

  const prRaw = runGh(['pr', 'view', String(prNum), '--json', 'id,title'])
  const pr = JSON.parse(prRaw)

  console.log(`Linking Issue #${issue.id} ("${issue.title}") -> PR #${pr.id} ("${pr.title}")...`)

  const mutation = `
mutation {
  addCloseIssueReferences(input: {
    issueId: "${issue.id}",
    pullRequestIds: ["${pr.id}"]
  }) {
    clientMutationId
    issue {
      number
      title
    }
  }
}`

  const result = runGh(['api', 'graphql', '-f', `query=${mutation}`])
  console.log('GitHub Development native relation established successfully:')
  console.log(result)
} catch (error) {
  console.error('Failed to link issue and PR natively:', error.message || error)
  if (error.stderr) console.error(error.stderr)
  process.exit(1)
}
