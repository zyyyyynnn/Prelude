#!/usr/bin/env node
'use strict'

const fs = require('node:fs')
const path = require('node:path')

const root = path.resolve(__dirname, '..', '..')
const migration = fs.readFileSync(
  path.join(
    root,
    'backend',
    'src',
    'main',
    'resources',
    'db',
    'migration',
    'V2__establish_reference_data.sql',
  ),
  'utf8',
)
const settings = fs.readFileSync(
  path.join(root, 'frontend', 'src', 'features', 'settings', 'SettingsModal.tsx'),
  'utf8',
)
const types = fs.readFileSync(
  path.join(root, 'frontend', 'src', 'features', 'settings', 'types.ts'),
  'utf8',
)
const required = ['deepseek', 'openai-responses', 'openai-chat-completions', 'anthropic-messages']
const violations = required
  .filter((key) => !migration.includes(`'${key}'`))
  .map((key) => `missing provider ${key}`)
if (/DISPLAY_NAME_MAP|displayNameMap/.test(settings))
  violations.push('provider display names must come from the API')
for (const field of ['providerKey', 'displayName', 'availableModels', 'enabled']) {
  if (!types.includes(`${field}:`)) violations.push(`provider DTO missing ${field}`)
}
if (violations.length) {
  console.error(`BYOK contract verification: FAIL (${violations.length})`)
  for (const violation of violations) console.error(`  ${violation}`)
  process.exit(1)
}
console.log('BYOK contract verification: PASS')
