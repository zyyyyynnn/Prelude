#!/usr/bin/env node
'use strict'

const fs = require('node:fs')
const path = require('node:path')
const { UTILITY_NAME, declaredUtilities, utilityFamily } = require('./utility-names.cjs')

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
    owner: 'src/features/interview/components/session-row.tsx',
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

/* Class positions only: the full attribute value, a template literal, a braced string
   literal, or a `cn(...)` argument list. Shared by the recipe scan and the
   internal-class scan. The braced-string form (`className={"…"}`) is easy to overlook
   and is exactly where a hand-rolled recipe hides. */
const CLASS_ATTRIBUTE = /className="([^"]*)"|className=\{`([^`]*)`\}|className=\{"([^"]*)"\}/g
const MERGE_CALL = /\b(?:cn|clsx)\(/g

/* A `cn(...)` argument list is read by counting parens, not by `[^)]*`. An arbitrary
   value closes a paren of its own — `max-w-(--layout-workspace-content-max-inline-size)`
   — so the character-class form stopped at the *value's* `)` and left the rest of the
   call invisible to both scans below: `panel.tsx`'s
   `cn('max-w-(--…)', className)` was cut in half, and every `-(--token)` recipe moved
   into a `cn()` would have been scanned as a truncated fragment. Quotes are stepped
   over so a class carrying an unbalanced paren cannot truncate the read either. */
function readCallArguments(source, open) {
  let depth = 0
  let quote = null
  for (let index = open; index < source.length; index += 1) {
    const character = source[index]
    if (quote) {
      if (character === '\\') index += 1
      else if (character === quote) quote = null
      continue
    }
    if (character === "'" || character === '"' || character === '`') quote = character
    else if (character === '(') depth += 1
    else if (character === ')' && (depth -= 1) === 0) return source.slice(open + 1, index)
  }
  return null
}

/* The class-bearing chunks of one file. Only the four positions above are read, so
   `import … from '@/shared/ui/session-row'` still cannot look like a written class —
   that is what produced thirteen false positives when a whole-file scan was tried. */
function classPositions(source) {
  const chunks = []
  for (const match of source.matchAll(CLASS_ATTRIBUTE)) {
    for (const chunk of [match[1], match[2], match[3]]) if (chunk) chunks.push(chunk)
  }
  for (const match of source.matchAll(MERGE_CALL)) {
    const args = readCallArguments(source, match.index + match[0].length - 1)
    if (args !== null) chunks.push(args)
  }
  return chunks
}

/* Discovery, not an after-the-fact registry. A duplicated *recipe* — a run of at least
   three class tokens that names the design system — is how an owner and a call site end
   up rendering "the same" component differently with nothing to notice it: the floating
   card recipe lived in `shared/ui/panel` and was re-hand-rolled in `AnalyticsPage`. The
   registry above can only name the duplications somebody already found; this finds the
   next one and forces either a named exception or an owner to be promoted.

   Scanning reads class positions only (`className="…"`, className={`…`}, `cn(…)`). A
   whole-file scan would flag `import … from '@/shared/ui/session-row'` as having written
   the class `session-row`, which produced thirteen false positives when it was tried.

   A run counts as a recipe only when it names the design system: a bare layout combo
   (`flex flex-col gap-sm`) is a spelling, not a component, and repeating it is not a
   defect. Colour, radius, elevation, semantic text roles and the sheet's own registered
   names are what make two copies of the same thing. */
const MIN_RECIPE_RUN = 3
const sheetForRecipes = fs.readFileSync(
  path.join(sourceRoot, 'shared', 'styles', 'index.css'),
  'utf8',
)
const designSystemNames = new Set()
for (const name of declaredUtilities(sheetForRecipes)) designSystemNames.add(utilityFamily(name))
for (const match of sheetForRecipes.matchAll(/^\.([a-z][-\w]*)\s*[,{]/gm))
  designSystemNames.add(match[1])
for (const match of sheetForRecipes.matchAll(/--([a-z][a-z0-9-]*):/g))
  designSystemNames.add(`(${match[1]})`)

/* A Tailwind token, arbitrary values (which contain their own parens) and BEM
   element/modifier underscores included so `workspace-page__content` is one token. */
const RECIPE_TOKEN = /[a-z][a-z0-9_-]*(?:[!:/][a-z0-9_-]+)*(?:\([^)]*\))?/gi
const namesDesignSystem = (token) =>
  /^bg-(?!transparent)/.test(token) ||
  /^text-text-/.test(token) ||
  /^border-border$/.test(token) ||
  /^(?:rounded|shadow|elevated|ring|outline)-/.test(token) ||
  designSystemNames.has(token)

/* Registered exceptions, each naming the owner the duplication must be promoted to.
   This list is not a bypass: every entry states the concrete promotion target, and a
   new duplication is a defect until it is either promoted or listed here with a
   reason.

   `files` names the exact pair the exception covers. Matching on the pair as well as
   the run is what keeps an exception from shadowing a new duplication: the runs are
   fixed three-token windows, so a longer recipe always contains the leading window of
   a shorter one, and a run-only match would silently excuse every caller that copies
   the whole recipe on top of it. */
const duplicatedRecipeExceptions = [
  {
    run: 'rounded-lg border border-border',
    files: ['src/app/lab/ComponentLab.tsx', 'src/shared/ui/card.tsx'],
    reason:
      'the component-lab preview frame is a border-only demo frame, not the elevated card, so it cannot go through Card; it needs its own owner or a narrower recipe',
  },
]

const recipesByRun = new Map()
for (const file of walk(sourceRoot).filter((item) => /\.(ts|tsx)$/.test(item))) {
  const relative = path.relative(root, file).replaceAll('\\', '/')
  if (relative.startsWith('src/shared/styles/')) continue
  const source = fs.readFileSync(file, 'utf8')
  const runs = new Set()
  for (const chunk of classPositions(source)) {
    const tokens = chunk.match(RECIPE_TOKEN) || []
    for (let start = 0; start + MIN_RECIPE_RUN <= tokens.length; start++) {
      const run = tokens.slice(start, start + MIN_RECIPE_RUN)
      if (!run.some(namesDesignSystem)) continue
      runs.add(run.join(' '))
    }
  }
  for (const run of runs) {
    if (!recipesByRun.has(run)) recipesByRun.set(run, new Set())
    recipesByRun.get(run).add(relative)
  }
}

/* Overlapping runs from one pair of files (`gutter-stable flex min-h-0` and
   `scrollable gutter-stable flex`) are one duplication, so report each pair once with
   its longest shared run. */
const longestRunByPair = new Map()
for (const [run, files] of recipesByRun) {
  if (files.size < 2) continue
  for (const left of files) {
    for (const right of files) {
      if (left >= right) continue
      const key = `${left}\u0000${right}`
      const previous = longestRunByPair.get(key)
      if (!previous || run.split(' ').length > previous.split(' ').length)
        longestRunByPair.set(key, run)
    }
  }
}
let registeredExceptions = 0
for (const [key, run] of [...longestRunByPair].sort()) {
  const [left, right] = key.split('\u0000')
  const exception = duplicatedRecipeExceptions.find(
    (entry) => entry.run === run && entry.files.includes(left) && entry.files.includes(right),
  )
  if (exception) {
    // Registered: sanctioned, so it does not fail the gate. Counted so the pass line
    // still shows how many are outstanding.
    registeredExceptions += 1
    continue
  }
  violations.push(
    `${left} + ${right}: duplicated recipe "${run}" — promote it to one owner, or register it in duplicatedRecipeExceptions with a reason`,
  )
}

/* An exception whose named files no longer produce its run is a leftover, not an
   exemption — the same rule the chart-geometry allowlist follows. Audited against
   every run rather than the longest per pair, so a longer recipe sharing the window
   cannot make a live exception look stale. */
for (const entry of duplicatedRecipeExceptions) {
  const files = recipesByRun.get(entry.run)
  if (files?.has(entry.files[0]) && files?.has(entry.files[1])) continue
  violations.push(
    `duplicatedRecipeExceptions: "${entry.run}" between ${entry.files.join(' and ')} is gone — drop the entry`,
  )
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

/* A component's internal element classes (`ui-menu__label`, `ui-button__content`)
   are its own layout contract. A call site that writes one is reaching past the component's
   props into its markup, and the two then drift with nothing to notice it — which is how the
   interview menus ended up hand-composing what `shared/ui/menu` already drew. Only the
   families a `shared/ui` component owns are in scope: `workspace-page` and `app-layout` are
   page-level layout a route writes itself. */
const internalClassPattern = /\b((?:ui-[a-z-]+|workspace-header)__[a-z-]+)/g

/* The BEM shape only catches classes spelled with `__`. A `@utility` registered to carry one
   component's markup is exactly as internal and has no shape to recognise — `prompt-bar-control`
   is the case in point: a feature trigger rewrote the whole control (classes, truncating span,
   chevron) and the shape rule could not see it. Ownership is therefore declared in the
   stylesheet, and a declared family covers its suffixes (`prompt-bar-control` also owns
   `prompt-bar-control-text`). */
const declaredCss = fs.readFileSync(path.join(sourceRoot, 'shared', 'styles', 'index.css'), 'utf8')
const ownedFamilies = [
  ...declaredCss.matchAll(
    new RegExp(`\\/\\*\\s*@internal\\s+(\\S+)\\s*\\*\\/\\s*\\n@utility\\s+(${UTILITY_NAME})`, 'g'),
  ),
].map((match) => ({ owner: match[1], base: utilityFamily(match[2]) }))
const classTokens = (source) => {
  const found = new Set()
  for (const chunk of classPositions(source)) {
    for (const token of chunk.split(/[\s,'"`]+/)) if (token) found.add(token)
  }
  return found
}

for (const file of walk(sourceRoot).filter((item) => /\.(ts|tsx)$/.test(item))) {
  const relative = path.relative(root, file).replaceAll('\\', '/')
  if (relative.startsWith('src/shared/styles/')) continue
  const source = fs.readFileSync(file, 'utf8')
  if (!relative.startsWith('src/shared/ui/')) {
    const leaked = [...new Set([...source.matchAll(internalClassPattern)].map((match) => match[1]))]
    for (const name of leaked)
      violations.push(`${relative}: writes ${name}, a shared/ui internal class`)
  }
  if (relative === 'src/shared/styles/index.css') continue
  for (const { owner, base } of ownedFamilies) {
    if (relative === owner) continue
    const written = [...classTokens(source)].filter(
      (token) => token === base || token.startsWith(`${base}-`),
    )
    for (const name of written)
      violations.push(
        `${relative}: writes ${name}, declared @internal to ${owner} — go through its props instead`,
      )
  }
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
  const flush = (closingAt) => {
    if (closingAt === 1 && header) {
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
      const closingAt = depth
      depth -= 1
      if (closingAt === 1) flush(closingAt)
      continue
    }
    if (depth === 0) header += character
    else if (depth === 1) body += character
  }
}

const indexCss = stylesheets
  .filter((file) => file.endsWith(path.join('shared', 'styles', 'index.css')))
  .map((file) => fs.readFileSync(file, 'utf8'))
  .join('\n')
const consumers = walk(sourceRoot)
  .filter((item) => /\.(tsx|ts)$/.test(item))
  .map((item) => fs.readFileSync(item, 'utf8'))
  .join('\n')

/* A class rule with no consumer is dead weight.
   The previous form of this check tested `consumers.includes(stem)` over every source file
   concatenated, and its candidates included the BEM block (`field` from `field__hint`), so it
   was true for every element class in the sheet and reported nothing — a green that measured
   nothing. A class now has to appear as a whole token. The one exemption is a family actually
   composed at runtime, listed below rather than inferred from its name shape. */
const runtimeComposedFamilies = [
  /^ui-button--/, // `ui-button--${variant}` in shared/ui/button.tsx
]
const escapeForToken = (value) => value.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')
const usesClassToken = (name) =>
  new RegExp(`(?:[^\\w-]|^)${escapeForToken(name)}(?:[^\\w-]|$)`).test(consumers)

const registered = new Set(declaredUtilities(indexCss).map(utilityFamily))
const declaredClasses = new Set()
for (const match of indexCss.matchAll(/^\.([a-z][-\w]*)\s*[,{]/gm)) declaredClasses.add(match[1])
for (const name of declaredClasses) {
  if (registered.has(name)) continue
  if (runtimeComposedFamilies.some((pattern) => pattern.test(name))) continue
  if (usesClassToken(name)) continue
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
console.log(
  registeredExceptions
    ? `UI guardrails: PASS (${registeredExceptions} duplicated recipe(s) registered as exceptions, each naming its promotion target)`
    : 'UI guardrails: PASS',
)
