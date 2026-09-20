#!/usr/bin/env node
'use strict'

const fs = require('node:fs')
const path = require('node:path')

const root = path.resolve(__dirname, '..')
const sourceRoot = path.join(root, 'src')
const violations = []

function walk(directory) {
  return fs.readdirSync(directory, { withFileTypes: true }).flatMap((entry) => {
    const target = path.join(directory, entry.name)
    return entry.isDirectory() ? walk(target) : [target]
  })
}

for (const file of walk(sourceRoot).filter((item) => /\.(ts|tsx)$/.test(item))) {
  const source = fs.readFileSync(file, 'utf8')
  const relative = path.relative(root, file).replaceAll('\\', '/')
  const rules = [
    [/<[a-z][^>]*\btitle\s*=/, 'native title tooltip'],
    [/window\.confirm\s*\(/, 'native confirm dialog'],
    [/(?:#[0-9a-f]{3,8}|rgba?\(|hsla?\()/i, 'hard-coded color'],
    [/transition-all/, 'transition-all'],
  ]
  for (const [pattern, label] of rules) {
    if (pattern.test(source)) violations.push(`${relative}: ${label}`)
  }
}

/* Some markup has exactly one owner, and a call site that re-hand-rolls it is how the two
   copies drift apart — the gallery and the product silently rendering "the same" component
   differently. Each entry names the file allowed to write the shape; everyone else must go
   through the owner. */
const singleOwnerRules = [
  {
    owner: 'src/shared/ui/empty-state.tsx',
    pattern: /className="[^"]*\bempty-state\b/,
    label: 'the empty/loading/error state belongs to shared/ui/empty-state',
  },
  {
    owner: 'src/shared/styles/index.css',
    pattern: /max-w-\(--layout-lead-max-inline-size\)/,
    label: 'the lead measure is the `type-lead` role, not a per-page width',
  },
  {
    owner: 'src/shared/ui/file-input.tsx',
    pattern: /type="file"/,
    label: 'a file picker goes through shared/ui/file-input, which clears the selection',
  },
  {
    owner: 'src/shared/ui/panel.tsx',
    pattern: /border-t border-border pt-md/,
    label: 'a panel sub-section band goes through the Panel owner SubSection',
  },
  {
    owner: 'src/features/report/report-sections.tsx',
    pattern: /border-t border-border py-lg/,
    label: 'a report section band goes through ReportSection',
  },
  {
    owner: 'src/shared/styles/index.css',
    pattern: /bg-surface-muted p-(sm|md|lg|xl)/,
    label: 'an inset card is the `inset-card` utility, not a fill plus a radius plus a padding',
  },
  {
    owner: 'src/shared/ui/session-row.tsx',
    pattern: /mx-sm text-xs font-semibold tracking-label/,
    label: 'a session group caption goes through SessionGroupLabel',
  },
]
for (const { owner, pattern, label } of singleOwnerRules) {
  for (const file of walk(sourceRoot).filter((item) => /\.(ts|tsx)$/.test(item))) {
    const relative = path.relative(root, file).replaceAll('\\', '/')
    if (relative === owner) continue
    if (pattern.test(fs.readFileSync(file, 'utf8')))
      violations.push(`${relative}: ${label} (${owner})`)
  }
}

/* A floating layer's offset from its anchor is design geometry, not a call-site preference.
   Four positioners carried three different literals with nothing to say which difference was
   meant; they now read from one named owner. */
for (const file of walk(sourceRoot).filter((item) => /\.(ts|tsx)$/.test(item))) {
  const relative = path.relative(root, file).replaceAll('\\', '/')
  if (relative === 'src/shared/ui/positioning.ts') continue
  for (const match of fs.readFileSync(file, 'utf8').matchAll(/sideOffset=\{([^}]*)\}/g)) {
    if (!/^OVERLAY_OFFSET\.[a-z]+$/.test(match[1].trim()))
      violations.push(
        `${relative}: sideOffset must read from OVERLAY_OFFSET, got "${match[1].trim()}"`,
      )
  }
}

/* A component's internal element classes (`prelude-menu__label`, `prelude-button__content`)
   are its own layout contract. A call site that writes one is reaching past the component's
   props into its markup, and the two then drift with nothing to notice it — which is how the
   interview menus ended up hand-composing what `shared/ui/menu` already drew. Only the
   families a `shared/ui` component owns are in scope: `workspace-page` and `app-layout` are
   page-level layout a route writes itself. */
const internalClassPattern = /\b((?:prelude-[a-z-]+|workspace-header)__[a-z-]+)/g
for (const file of walk(sourceRoot).filter((item) => /\.(ts|tsx)$/.test(item))) {
  const relative = path.relative(root, file).replaceAll('\\', '/')
  if (relative.startsWith('src/shared/ui/') || relative.startsWith('src/shared/styles/')) continue
  const leaked = [
    ...new Set([...fs.readFileSync(file, 'utf8').matchAll(internalClassPattern)].map((m) => m[1])),
  ]
  for (const name of leaked)
    violations.push(`${relative}: writes ${name}, a shared/ui internal class`)
}

for (const file of [
  path.join(root, 'index.html'),
  ...walk(sourceRoot).filter((item) => /\.(css|ts|tsx)$/.test(item)),
]) {
  const source = fs.readFileSync(file, 'utf8')
  if (!/fonts\.(?:googleapis|gstatic)\.com/i.test(source)) continue
  const relative = path.relative(root, file).replaceAll('\\', '/')
  violations.push(`${relative}: remote font dependency`)
}

const overlays = fs.readFileSync(path.join(sourceRoot, 'shared', 'ui', 'overlay.tsx'), 'utf8')
if (!overlays.includes('Tooltip.Provider') && !overlays.includes('Tooltip.Root')) {
  violations.push('shared/ui/overlay.tsx: Base UI tooltip primitive is required')
}

// ---------------------------------------------------------------- CSS hygiene
// The stylesheet is the single place Tailwind resolves class names from, so a rule
// nobody can reach is not merely untidy — it is the shape the atomic contract forbids.
const stylesheets = walk(sourceRoot).filter((item) => item.endsWith('.css'))
for (const file of stylesheets) {
  const relative = path.relative(root, file).replaceAll('\\', '/')
  if (relative !== 'src/shared/styles/index.css' && relative !== 'src/app/styles.css') {
    violations.push(
      `${relative}: feature CSS file is not allowed, register an @utility in index.css`,
    )
    continue
  }
  const source = fs.readFileSync(file, 'utf8')
  source.split('\n').forEach((line, index) => {
    if (
      /^import\s+['"]\.\/.+\.css['"]/.test(line) ||
      /@import\s+['"]\.{1,2}\/.*features.*\.css/.test(line)
    ) {
      violations.push(`${relative}:${index + 1}: stylesheet imported from a feature owner`)
    }
  })
  if (relative !== 'src/shared/styles/index.css') continue
  // Walk top-level rules only; comments are blanked so they cannot look like a body.
  const code = source.replace(/\/\*[\s\S]*?\*\//g, (comment) => comment.replace(/[^\n]/g, ' '))
  let depth = 0
  let line = 1
  let header = ''
  let body = ''
  let ruleLine = 1
  const flush = () => {
    if (depth === 1 && header) {
      const bare = header.replace(/::?[\w-]+(\([^)]*\))?/g, '').trim()
      if (body.trim() === '') violations.push(`${relative}:${ruleLine}: empty rule ${header}`)
      if (/^[a-z][a-z0-9]*$/i.test(bare) && !bare.includes('.')) {
        violations.push(
          `${relative}:${ruleLine}: unlayered element selector "${bare}" — move it into @layer base`,
        )
      }
    }
    header = ''
    body = ''
  }
  for (const character of code) {
    if (character === '\n') {
      line += 1
      if (depth === 0) header = ''
      continue
    }
    if (character === '{') {
      depth += 1
      if (depth === 1) {
        ruleLine = line
        body = ''
      }
      continue
    }
    if (character === '}') {
      depth -= 1
      if (depth === 0) flush()
      continue
    }
    if (depth === 0) header += character
    else if (depth === 1) body += character
  }
}

// A class rule with no consumer is dead weight; shared primitives are composed at
// runtime (`prelude-button--${variant}`), so their stems count as consumers too.
const indexCss = stylesheets
  .filter((file) => file.endsWith(path.join('shared', 'styles', 'index.css')))
  .map((file) => fs.readFileSync(file, 'utf8'))
  .join('\n')
const consumers = walk(sourceRoot)
  .filter((item) => /\.(tsx|ts)$/.test(item))
  .map((item) => fs.readFileSync(item, 'utf8'))
  .join('\n')
const registered = new Set(
  [...indexCss.matchAll(/^@utility\s+([a-z0-9*-]+)/gm)].map((match) =>
    match[1].replace(/-\*$/, ''),
  ),
)
const declaredClasses = new Set()
for (const match of indexCss.matchAll(/^\.([a-z][-\w]*)\s*[,{]/gm)) declaredClasses.add(match[1])
for (const name of declaredClasses) {
  if (registered.has(name)) continue
  const stems = [name, ...name.split(/(?=--)|(?=__)/).filter(Boolean)]
  const base = name.replace(/(--|__)[\s\S]*$/, '')
  const candidates = new Set([...stems, base])
  if ([...candidates].some((candidate) => candidate.length > 2 && consumers.includes(candidate)))
    continue
  violations.push(`src/shared/styles/index.css: .${name} has no consumer`)
}

/* A functional `@utility name-*` only reaches the bundle when some source file spells a
   concrete `name-<suffix>` out: Tailwind reads class names from text, so a name built at
   runtime (`name-${count}`) registers nothing and the rule silently never exists. The
   stem check above cannot see this, because the dynamic prefix does appear in source. */
for (const match of indexCss.matchAll(/^@utility\s+([a-z][a-z0-9-]*)-\*\s*\{/gm)) {
  const prefix = match[1]
  if (!new RegExp(`${prefix}-(?:[a-z][a-z0-9]*|\\d+)\\b`).test(consumers)) {
    violations.push(
      `src/shared/styles/index.css: @utility ${prefix}-* has no literal call site — Tailwind cannot emit a class name built at runtime`,
    )
  }
}

if (violations.length) {
  console.error(`UI guardrails: FAIL (${violations.length})`)
  for (const violation of violations) console.error(`  ${violation}`)
  process.exit(1)
}
console.log('UI guardrails: PASS')
