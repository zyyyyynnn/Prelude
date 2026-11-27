#!/usr/bin/env node
'use strict'

/** Links an issue and PR through GitHub Development's native relation. */
// Usage: node scripts/link-issue.cjs <issueNumber> <prNumber>

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
