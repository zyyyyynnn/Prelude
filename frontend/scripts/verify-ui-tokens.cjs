#!/usr/bin/env node
/**
 * UI token schema verification — Phase 4.
 *
 * Purpose: enforce that:
 *  1. Every token declared in `frontend/tokens/ui-tokens.json` exists as a
 *     `--xxx: value;` declaration somewhere in `frontend/src/styles/index.css`.
 *  2. Every `--shadow-*` token's RAW VALUE appears only inside token-definition
 *     lines (i.e. inside `:root` or `:root.dark, .dark`). Any raw shadow value
 *     on a non-token line is a violation (selector must use var(--shadow-*)).
 *  3. Every `--z-index-*` token has a unique numeric value.
 *  4. Design-locked tokens (Sidebar 260 / 51 / 800 / dialog 960 / 500 / height
 *     34 / 30) match the values listed in the schema's `design_lock_values`.
 *  5. No shadow-raw-in-selector: any `box-shadow:` declaration whose value
 *     does not start with `var(` is treated as a raw violation; selectors
 *     should always reference a `--shadow-*` token.
 *
 * Scope: read-only. Does NOT generate CSS. Does NOT modify any file. Does NOT
 * introduce dependencies — uses Node's built-in modules only.
 *
 * Exit codes:
 *   0 — all checks passed.
 *   1 — at least one VIOLATION was detected.
 */
'use strict'

const fs = require('node:fs')
const path = require('node:path')

const repoRoot = path.resolve(__dirname, '..', '..')
const schemaPath = path.join(repoRoot, 'frontend', 'tokens', 'ui-tokens.json')
const stylesPath = path.join(repoRoot, 'frontend', 'src', 'styles', 'index.css')

const ok = (...args) => console.log(...args)
const err = (...args) => console.error(...args)

function loadSchema() {
  const raw = fs.readFileSync(schemaPath, 'utf8')
  return JSON.parse(raw)
}

function loadStyles() {
  return fs.readFileSync(stylesPath, 'utf8')
}

/**
 * Parse token declarations from styles/index.css.
 *
 * Returns an array of { token, value, line, inDefinitionBlock } where:
 *   - token: `--xxx`
 *   - value: the RHS string up to the trailing semicolon
 *   - line: 1-based line number where the declaration starts
 *   - inDefinitionBlock: true if the declaration is inside a token-definition
 *     block (`:root`, `:root.dark`, `.dark`, or `@theme`). Shadow tokens that
 *     end up outside any of these blocks are reported as misplaced.
 *
 * Implementation note: we track block depth by counting `{`/`}` per line.
 * For top-level rule detection we look for a line that ends with `{` and
 * whose content starts with one of the recognized block selectors.
 */
function parseTokens(css) {
  const lines = css.split(/\r?\n/)
  const tokens = []
  const definitionSelectors = [':root', ':root.dark', '.dark', '@theme']
  let blockStack = []
  for (let i = 0; i < lines.length; i++) {
    const line = lines[i]
    const trimmed = line.trim()
    if (blockStack.length === 0) {
      // Look for a top-level rule. Match either a CSS selector with `{`
      // (e.g. `:root {`, `.dark {`) or an at-rule with body (e.g. `@theme {`).
      const ruleMatch = trimmed.match(/^(@?[\w.:-][^{]*)\{[^}]*$/)
      if (ruleMatch) {
        const selector = ruleMatch[1].trim()
        blockStack.push({ selector, depth: 1 })
        continue
      }
      // Single-line rule with inline block.
      if (trimmed.includes('{') && trimmed.endsWith('}')) {
        // don't push; it's inline.
      }
    } else {
      // Count braces in this line.
      let depth = blockStack[blockStack.length - 1].depth
      let inString = false
      let inComment = false
      for (let j = 0; j < line.length; j++) {
        const ch = line[j]
        if (inComment) {
          if (ch === '*' && line[j + 1] === '/') {
            inComment = false
            j++
          }
          continue
        }
        if (inString) {
          if (ch === '"' || ch === "'") inString = false
          continue
        }
        if (ch === '/' && line[j + 1] === '*') {
          inComment = true
          j++
          continue
        }
        if (ch === '"' || ch === "'") {
          inString = true
          continue
        }
        if (ch === '{') depth++
        else if (ch === '}') depth--
      }
      if (depth <= 0) {
        blockStack.pop()
      } else {
        blockStack[blockStack.length - 1].depth = depth
      }
    }
    // Detect token declarations. CSS allows `--name: value;` with optional
    // spaces and may span multiple lines (e.g. a long shadow value). We
    // detect the START line via the `--name:` token (no semicolon required
    // on the same line), then accumulate the value across subsequent lines
    // until we find `;`. We scan line-by-line instead of repeatedly
    // joining the accumulator (which is O(n^2) on a long multi-line value).
    const startMatch = line.match(/^\s*(--[a-zA-Z][a-zA-Z0-9_-]*)\s*:\s*(.*)$/)
    if (!startMatch) continue
    let valueText = startMatch[2]
    // Always advance j past the start line so the for-loop's `i++` makes
    // forward progress, even when the entire value fits on one line.
    let j = i + 1
    if (!valueText.includes(';')) {
      // Multi-line value: scan forward until we find `;`.
      while (j < lines.length && j - i <= 30) {
        const next = lines[j]
        valueText += ' ' + next
        j++
        if (next.includes(';')) break
      }
    }
    // Continue tracking block depth on the start line so we know whether the
    // declaration lives in a definition block.
    const inDefinitionBlock = blockStack.some(
      (b) =>
        definitionSelectors.includes(b.selector) ||
        b.selector.startsWith(':root') ||
        b.selector.startsWith('.dark') ||
        b.selector.startsWith('@theme'),
    )
    // We only emit the FIRST occurrence of each token. Later occurrences
    // (typically dark-mode overrides) are still tracked by name for the
    // duplicate-value checks, but only the first gets recorded as the
    // "primary" declaration for design-lock assertions.
    const tokenName = startMatch[1]
    tokens.push({
      token: tokenName,
      value: valueText.replace(/;.*$/, '').trim(),
      line: i + 1,
      inDefinitionBlock,
    })
    // Skip past the multi-line value when the loop resumes. The for-loop's
    // `i++` will then increment once more, which is correct: we want the
    // NEXT line after the value's terminator to be re-evaluated normally.
    i = j - 1
  }
  return tokens
}

function unique(arr) {
  return Array.from(new Set(arr))
}

function reportViolation(label, detail) {
  err(`[VIOLATION] ${label}`)
  if (detail) err(`  ${detail}`)
}

function reportNote(label, detail) {
  ok(`[note] ${label}${detail ? ` — ${detail}` : ''}`)
}

function main() {
  const schema = loadSchema()
  const css = loadStyles()
  const tokens = parseTokens(css)

  const violations = []
  const notes = []

  // Build a map token → declaration for quick lookup.
  const tokenMap = new Map()
  for (const t of tokens) {
    // First definition wins for value assertions; later ones (e.g. .dark) are
    // tracked separately by their containing block.
    if (!tokenMap.has(t.token)) tokenMap.set(t.token, t)
  }

  // 1. Every token in the schema must exist somewhere in styles/index.css.
  for (const [category, def] of Object.entries(schema.categories)) {
    if (!def.tokens) continue
    for (const tokenName of def.tokens) {
      const expected = `--${tokenName}`
      if (!tokenMap.has(expected)) {
        const msg = `schema token '${expected}' (category=${category}) not declared in styles/index.css`
        violations.push({ kind: 'missing', label: msg })
        reportViolation(msg)
      }
    }
  }

  // 2. Reverse check (optional, just a note): CSS tokens not in schema.
  const schemaTokens = new Set()
  for (const def of Object.values(schema.categories)) {
    if (def && def.tokens) for (const t of def.tokens) schemaTokens.add(`--${t}`)
  }
  const cssTokens = unique(tokens.map((t) => t.token))
  const uncategorized = cssTokens.filter((t) => !schemaTokens.has(t))
  // Some tokens are intentionally out of scope (legacy / brand). We list
  // them as notes rather than violations so the team can decide.
  if (uncategorized.length > 0) {
    notes.push(
      `found ${uncategorized.length} CSS tokens not in schema: ${uncategorized.slice(0, 10).join(', ')}${uncategorized.length > 10 ? '...' : ''}`,
    )
    reportNote('uncategorized', uncategorized.slice(0, 10).join(', '))
  }

  // 3. shadow raw-only-in-definitions:
  //    For every `--shadow-*` token, its raw value must appear ONLY in
  //    definition blocks (`:root`, `:root.dark`, `.dark`).
  for (const t of tokens) {
    if (!t.token.startsWith('--shadow-')) continue
    if (!t.inDefinitionBlock) {
      const msg = `shadow token '${t.token}' declared outside a token-definition block (line ${t.line})`
      violations.push({ kind: 'shadow-misplaced', label: msg })
      reportViolation(msg)
    }
  }

  // 4. Shadow selectors must use var(--shadow-*). Any `box-shadow:` line
  //    whose value does not start with `var(` (after stripping color-mix /
  //    nested-var) is a raw violation. The literal value `none` (sometimes
  //    written as `none !important` to override Tailwind utilities) is
  //    accepted as an explicit opt-out and NOT flagged.
  const cssLines = lines(css)
  const shadowRuleRegex = /^\s*box-shadow\s*:\s*(.+?);\s*$/
  for (let i = 0; i < cssLines.length; i++) {
    const line = cssLines[i]
    const m = line.match(shadowRuleRegex)
    if (!m) continue
    const value = m[1].trim()
    // Accept `none` and `none !important` as opt-outs.
    if (/^none(\s+!important)?$/i.test(value)) continue
    if (!/var\(--shadow-/.test(value)) {
      const msg = `raw box-shadow value at line ${i + 1}: ${value.slice(0, 80)}${value.length > 80 ? '...' : ''}`
      violations.push({ kind: 'shadow-raw', label: msg })
      reportViolation(msg)
    }
  }

  // 5. z-index values must be unique.
  const zIndexValues = new Map() // token → value
  for (const t of tokens) {
    if (!t.token.startsWith('--z-index-')) continue
    zIndexValues.set(t.token, t.value)
  }
  const seenZ = new Map()
  for (const [tok, val] of zIndexValues) {
    if (seenZ.has(val)) {
      const msg = `z-index value '${val}' duplicated by ${tok} and ${seenZ.get(val)}`
      violations.push({ kind: 'zindex-duplicate', label: msg })
      reportViolation(msg)
    } else {
      seenZ.set(val, tok)
    }
  }

  // 6. Design-locked values must match the expected strings in the schema.
  //    The schema is the source of truth for what "matches" means; values
  //    are read from styles/index.css and trimmed before comparison.
  if (schema.design_lock_values) {
    for (const [tok, expected] of Object.entries(schema.design_lock_values)) {
      const fullName = `--${tok}`
      const decl = tokenMap.get(fullName)
      if (!decl) {
        // already reported by rule 1 if the token is missing entirely.
        continue
      }
      if (decl.value !== expected) {
        const msg = `design-locked token '${fullName}' has value '${decl.value}' but schema requires '${expected}'`
        violations.push({ kind: 'design-lock-mismatch', label: msg })
        reportViolation(msg)
      }
    }
  }

  // Summary
  ok('')
  ok('=== verify:ui-tokens summary ===')
  ok(`  schema tokens indexed: ${schemaTokens.size}`)
  ok(`  css declarations:      ${tokens.length}`)
  ok(`  notes:                 ${notes.length}`)
  ok(`  violations:            ${violations.length}`)

  if (violations.length > 0) {
    err('')
    err('FAIL: token schema verification detected violations.')
    process.exit(1)
  }
  ok('PASS: token schema verification clean.')
}

const lines = (s) => s.split(/\r?\n/)

if (require.main === module) {
  try {
    main()
  } catch (e) {
    err(`verify:ui-tokens crashed: ${e && e.stack ? e.stack : e}`)
    process.exit(1)
  }
}

module.exports = { parseTokens }
