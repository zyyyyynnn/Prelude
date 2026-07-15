#!/usr/bin/env node
/**
 * UI guardrail verifier (Node builtin only).
 *
 * Enforces the sizing & color rules in docs/quality/local-review-checklist.md.
 * Does NOT introduce new dependencies.
 *
 * Exits 0 on PASS, 1 on any VIOLATION.
 *
 * The token file `frontend/src/shared/ui/styles/index.css` is allowed to keep base
 * color values and px numerics as the source of truth for global tokens.
 */
'use strict'

const { execFileSync } = require('node:child_process')
const fs = require('node:fs')
const path = require('node:path')

const repoRoot = path.resolve(__dirname, '..', '..')
const frontendSrc = path.join(repoRoot, 'frontend', 'src')
const businessComponentRoots = [
  path.join(frontendSrc, 'features'),
  path.join(frontendSrc, 'devtools'),
]
const stylesIndex = path.join(frontendSrc, 'shared', 'ui', 'styles', 'index.css')
const tooltipContent = path.join(frontendSrc, 'shared', 'ui', 'tooltip', 'TooltipContent.vue')
const componentFocusShadowToken = '--shadow-icon-action-focus'

const semanticVarPrefixByFile = new Map([
  ['frontend/src/shared/ui/segmented-control/SegmentedControl.vue', ['--segmented-']],
  ['frontend/src/features/interview/components/SessionSidebar.vue', ['--sidebar-']],
  ['frontend/src/features/interview/components/InterviewComposer.vue', ['--composer-']],
  ['frontend/src/features/interview/components/InterviewWorkspace.vue', ['--workspace-']],
  ['frontend/src/features/insight/pages/AnalyticsPage.vue', ['--analytics-']],
  ['frontend/src/features/interview/components/MessageThread.vue', ['--message-', '--judge-']],
  ['frontend/src/features/settings/components/UserProfilePanel.vue', ['--profile-']],
])

const semanticVarTerms = [
  'size',
  'inline-size',
  'block-size',
  'width',
  'height',
  'offset',
  'radius',
  'inset',
  'layer',
  'shadow',
  'grid',
  'padding',
]

function cssVariableName(text) {
  const match = text.match(/^\s*(--[\w-]+)\s*:/)
  return match?.[1] || null
}

function relativeFile(file) {
  return path.relative(repoRoot, path.resolve(file)).replace(/\\/g, '/')
}

function isAllowedSemanticVariable(hit) {
  const name = cssVariableName(hit.text)
  if (!name) return false
  const rel = relativeFile(hit.file)
  const prefixes = semanticVarPrefixByFile.get(rel)
  if (!prefixes || !prefixes.some((prefix) => name.startsWith(prefix))) return false
  return semanticVarTerms.some((term) => name.includes(term))
}

function isStylesTokenDeclaration(hit) {
  return isAllowed(hit, new Set([stylesIndex])) && Boolean(cssVariableName(hit.text))
}

function isAllowedBoxShadow(hit) {
  const normalized = hit.text.trim()
  if (isStylesTokenDeclaration(hit)) return true

  const declaration = normalized.match(/^(?:-webkit-)?box-shadow:\s*(.+);$/)
  if (!declaration) return false

  const value = declaration[1].replace(/\s*!important$/, '').trim()
  if (value === 'none') return true

  return value.split(',').every((part) => /^var\(--shadow-[\w-]+\)$/.test(part.trim()))
}

const checks = [
  {
    id: 'forbidden-utility-classes',
    description:
      '禁止写法：transition-all / window.confirm / 原生 title= / shadow-md / shadow-lg / border-border / h-[30px] / h-[32px] / h-[34px]',
    pattern:
      'transition-all|window\\.confirm|title=|shadow-md|shadow-lg|border-border|h-\\[30px\\]|h-\\[32px\\]|h-\\[34px\\]',
    paths: [frontendSrc],
    allowPaths: new Set([stylesIndex]),
  },
  {
    id: 'color-token-bypass',
    description:
      '颜色 token 旁路：rgba / dark:bg- / bg-white / text-white / bg-black / text-black / 硬编码十六进制',
    pattern: 'rgba\\(|dark:bg-|bg-white|text-white|bg-black|text-black|#[0-9a-fA-F]{3,8}',
    paths: [frontendSrc],
    allowPaths: new Set([stylesIndex]),
  },
  {
    id: 'tailwind-arbitrary-px',
    description: 'Tailwind arbitrary px 类（业务组件）',
    pattern: '\\[[^\\]]*\\d+px[^\\]]*\\]',
    paths: [frontendSrc],
    allowPaths: new Set([stylesIndex]),
  },
  {
    id: 'css-raw-sizing-px',
    description:
      '业务组件属性侧 raw px 尺寸（z-index / width / height / inline-size / block-size / font-size）',
    pattern:
      '(min-|max-)?(width|height|inline-size|block-size):\\s*\\d+px|font-size:\\s*\\d+px|z-index:\\s*\\d+\\b',
    paths: [frontendSrc],
    allowPaths: new Set([stylesIndex]),
    // CSS custom property declarations (e.g. `--foo: 12px;`) inside a component scoped
    // block are allowed: they encode the geometric intent at the top of the component.
    isVariableLine: true,
  },
  {
    id: 'css-raw-sizing-px-block-size',
    description: '业务组件属性侧 width/height/inline-size/block-size 数值（非 0 像素）',
    pattern: '\\b(width|height|inline-size|block-size):\\s*\\d+px\\b',
    paths: [frontendSrc],
    allowPaths: new Set([stylesIndex]),
    isVariableLine: true,
  },
  {
    id: 'raw-box-shadow',
    description: '业务组件 raw box-shadow（必须只使用 shadow token）',
    pattern: 'box-shadow:',
    paths: [frontendSrc],
    allowPaths: new Set(),
    allowHit: isAllowedBoxShadow,
  },
  {
    id: 'raw-outline-px',
    description: '业务组件 raw outline px / outline-offset px',
    pattern: 'outline(-offset)?:\\s*-?\\d+px',
    paths: [frontendSrc],
    allowPaths: new Set([stylesIndex]),
  },
  {
    id: 'raw-border-radius-px',
    description: '业务组件 raw border-radius px',
    pattern: 'border-radius:\\s*\\d+px',
    paths: [frontendSrc],
    allowPaths: new Set([stylesIndex]),
  },
  {
    id: 'raw-translate-px',
    description: '业务组件 raw translate px',
    pattern: 'transform:\\s*translate[XY]?\\(-?\\d+px\\)',
    paths: [frontendSrc],
    allowPaths: new Set([stylesIndex]),
  },
  {
    id: 'tailwind-raw-z-index',
    description:
      'Tailwind raw z-index utility（使用 tokenized arbitrary z-index 或受控浮层 token）',
    pattern: '\\bz-\\d+\\b',
    paths: [frontendSrc],
    allowPaths: new Set([]),
  },
  {
    id: 'magic-height-ratio',
    description: '属性侧直接使用 calc(var(--ui-height-*) * 数字)',
    pattern: 'calc\\(var\\(--ui-height-[^)]+\\)\\s*\\*\\s*[0-9.]+',
    paths: [frontendSrc],
    allowPaths: new Set([stylesIndex]),
    isVariableLine: true,
  },
  {
    id: 'simple-spacing-calc',
    description: '简单半阶/负向 spacing calc（应使用 spacing-0-5 / spacing-neg-xs token）',
    pattern: 'calc\\(var\\(--spacing-(xs|sm|md|lg|xl|2xl)\\)\\s*[/\\*]\\s*-?[12]\\b',
    paths: [frontendSrc],
    allowPaths: new Set([stylesIndex]),
    isVariableLine: true,
  },
]

function runRipgrep(args) {
  // Use rg if available; fall back to a tiny Node walker otherwise.
  // CI runners (windows-latest) do NOT ship with ripgrep, so we must
  // gracefully degrade: any spawnSync failure (ENOENT, status 2, 127)
  // falls through to the in-process walker. Previously only status 2
  // and 127 were caught, leaving ENOENT (status === null) to crash the
  // entire verify:ui step.
  try {
    const out = execFileSync('rg', ['--no-heading', '--line-number', '--color', 'never', ...args], {
      cwd: repoRoot,
      encoding: 'utf8',
      stdio: ['ignore', 'pipe', 'pipe'],
    })
    return out
  } catch (error) {
    if (error.status === 1) {
      // rg exit 1 means "no matches" — treat as empty
      return ''
    }
    if (error.status === 2 || error.status === 127 || error.code === 'ENOENT') {
      // rg missing, syntax error, or binary not on PATH — fall through
      // to the in-process walker so the step still produces results on
      // environments without ripgrep (CI runners, fresh devcontainers).
      return walkFallback(args)
    }
    throw error
  }
}

function walkFallback(args) {
  // Minimal fallback: only handles "PATTERN PATH..." style args; for simplicity
  // we just walk frontend/src recursively and apply the pattern in JS.
  const patternStr = args[args.length - 2]
  const searchRoots = args
    .slice(args.indexOf('-e') >= 0 ? args.indexOf('-e') + 2 : 0)
    .filter((a) => !a.startsWith('-'))
  // The fallback is best-effort; we expect rg to be present in normal setups.
  const regex = new RegExp(patternStr)
  const results = []
  function walk(dir) {
    let entries
    try {
      entries = fs.readdirSync(dir, { withFileTypes: true })
    } catch {
      return
    }
    for (const ent of entries) {
      const p = path.join(dir, ent.name)
      if (ent.isDirectory()) {
        walk(p)
        continue
      }
      if (!/\.(vue|ts|css|tsx|js)$/.test(ent.name)) continue
      const text = fs.readFileSync(p, 'utf8')
      const lines = text.split(/\r?\n/)
      for (let i = 0; i < lines.length; i++) {
        if (regex.test(lines[i])) results.push(`${p}:${i + 1}:${lines[i]}`)
      }
    }
  }
  for (const root of searchRoots) walk(root)
  return results.join('\n')
}

function normalizeHits(output) {
  if (!output) return []
  return output
    .split(/\r?\n/)
    .filter((line) => line.length > 0)
    .map((line) => {
      const match = line.match(/^(.*?):(\d+):(.*)$/)
      if (!match) return { file: line, line: 0, text: '' }
      return { file: match[1], line: Number(match[2]), text: match[3] }
    })
}

function isAllowed(hit, allowPaths) {
  if (!allowPaths || allowPaths.size === 0) return false
  for (const allowed of allowPaths) {
    if (hit.file === allowed || hit.file.startsWith(allowed + path.sep)) return true
    // also try absolute path match in case rg emitted an absolute Windows path
    if (path.isAbsolute(hit.file) && path.isAbsolute(allowed)) {
      const normHit = path.normalize(hit.file)
      const normAllowed = path.normalize(allowed)
      if (normHit === normAllowed || normHit.startsWith(normAllowed + path.sep)) return true
    }
  }
  return false
}

function debugAllowed(allowPaths) {
  if (allowPaths.size === 0) return
  if (process.env.UI_GUARD_DEBUG !== '1') return
  for (const a of allowPaths) {
    console.error(`  raw=${JSON.stringify(a)} isAbsolute=${path.isAbsolute(a)}`)
  }
}

function debugHit(hit, allowPaths) {
  if (process.env.UI_GUARD_DEBUG !== '1') return
  for (const allowed of allowPaths) {
    const ok =
      hit.file === allowed ||
      hit.file.startsWith(allowed + path.sep) ||
      (path.isAbsolute(hit.file) &&
        path.isAbsolute(allowed) &&
        path.normalize(hit.file) === path.normalize(allowed))
    if (ok) return
  }
  console.error(
    'debug fail:',
    JSON.stringify({ file: hit.file, line: hit.line, allowed: [...allowPaths] }),
  )
}

function collectVueFiles(dir, files = []) {
  let entries
  try {
    entries = fs.readdirSync(dir, { withFileTypes: true })
  } catch {
    return files
  }
  for (const entry of entries) {
    const file = path.join(dir, entry.name)
    if (entry.isDirectory()) {
      collectVueFiles(file, files)
    } else if (entry.isFile() && entry.name.endsWith('.vue')) {
      files.push(file)
    }
  }
  return files
}

function findComponentFocusShadowViolations() {
  const violations = []
  const requiredValue = `var(${componentFocusShadowToken})`
  for (const file of businessComponentRoots.flatMap((root) => collectVueFiles(root))) {
    const source = fs.readFileSync(file, 'utf8')
    const stylePattern = /<style\b[^>]*>([\s\S]*?)<\/style>/g
    let styleMatch
    while ((styleMatch = stylePattern.exec(source)) !== null) {
      const styleSource = styleMatch[1]
      const focusBlockPattern = /([^{}]*:focus-visible[^{}]*)\{([^{}]*)\}/g
      let focusMatch
      while ((focusMatch = focusBlockPattern.exec(styleSource)) !== null) {
        const shadowMatch = focusMatch[2].match(/box-shadow\s*:\s*([^;]+);/)
        if (!shadowMatch || shadowMatch[1].trim() === requiredValue) continue
        const sourceOffset =
          styleMatch.index + styleMatch[0].indexOf(styleSource) + focusMatch.index
        const line = source.slice(0, sourceOffset).split(/\r?\n/).length
        violations.push({
          id: 'component-focus-shadow-token',
          description: `业务组件 :focus-visible 的 box-shadow 必须精确使用 ${requiredValue}`,
          hit: {
            file,
            line,
            text: `${focusMatch[1].trim()} { box-shadow: ${shadowMatch[1].trim()}; }`,
          },
        })
      }
    }
  }
  return violations
}

function findTooltipContractViolations() {
  const source = fs.readFileSync(tooltipContent, 'utf8')
  const requiredClasses = ['bg-foreground', 'text-background', 'max-w-xs', 'break-words']
  const missing = requiredClasses.filter((className) => !source.includes(className))
  if (missing.length === 0 && !source.includes('bg-surface')) {
    return []
  }
  return [
    {
      id: 'tooltip-surface-contract',
      description: 'Tooltip 必须使用统一的中性高对比浮层，不得回退到页面 surface',
      hit: {
        file: tooltipContent,
        line: 1,
        text: missing.length > 0 ? `missing: ${missing.join(', ')}` : 'contains bg-surface',
      },
    },
  ]
}

const failures = []
const allowed = []

for (const check of checks) {
  debugAllowed(check.allowPaths)
  const output = runRipgrep(['-e', check.pattern, ...check.paths])
  const hits = normalizeHits(output)
  for (const hit of hits) {
    debugHit(hit, check.allowPaths)
    if (isAllowed(hit, check.allowPaths)) {
      allowed.push({ id: check.id, hit })
      continue
    }
    if (check.allowHit?.(hit)) {
      allowed.push({ id: check.id, hit, reason: 'explicit-allow-rule' })
      continue
    }
    if (check.isVariableLine && isAllowedSemanticVariable(hit)) {
      allowed.push({ id: check.id, hit, reason: 'semantic-variable-declaration' })
      continue
    }
    failures.push({ id: check.id, description: check.description, hit })
  }
}

failures.push(...findComponentFocusShadowViolations())
failures.push(...findTooltipContractViolations())

if (allowed.length > 0) {
  console.log('--- ALLOWED HITS ---')
  for (const { id, hit, reason } of allowed) {
    const label =
      reason === 'semantic-variable-declaration'
        ? 'ALLOWED SEMANTIC VARIABLE'
        : reason === 'explicit-allow-rule'
          ? 'ALLOWED EXPLICIT RULE'
          : 'ALLOWED TOKEN'
    console.log(`  [${id}] ${label}: ${hit.file}:${hit.line}`)
  }
}

if (failures.length === 0) {
  console.log('--- UI guardrail: PASS ---')
  process.exit(0)
}

console.log('--- UI guardrail: VIOLATION ---')
const grouped = new Map()
for (const f of failures) {
  if (!grouped.has(f.id)) grouped.set(f.id, { description: f.description, hits: [] })
  grouped.get(f.id).hits.push(f.hit)
}
for (const [id, group] of grouped) {
  console.log(`\n[${id}] ${group.description}`)
  for (const hit of group.hits) {
    console.log(`  ${hit.file}:${hit.line}  ${hit.text.trim()}`)
  }
}
process.exit(1)
