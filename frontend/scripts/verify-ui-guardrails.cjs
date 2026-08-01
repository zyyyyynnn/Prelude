#!/usr/bin/env node
/**
 * UI guardrail verifier (Node builtin only).
 *
 * Enforces the sizing, color, focus, and shared floating-surface contracts.
 * Exits 0 on PASS and 1 on any violation.
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
const sharedDropdown = path.join(frontendSrc, 'shared', 'ui', 'shared-dropdown.ts')
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
  const prefixes = semanticVarPrefixByFile.get(relativeFile(hit.file))
  if (!prefixes || !prefixes.some((prefix) => name.startsWith(prefix))) return false
  return semanticVarTerms.some((term) => name.includes(term))
}

function isAllowed(hit, allowPaths) {
  if (!allowPaths || allowPaths.size === 0) return false
  for (const allowed of allowPaths) {
    if (hit.file === allowed || hit.file.startsWith(allowed + path.sep)) return true
    if (path.isAbsolute(hit.file) && path.isAbsolute(allowed)) {
      const normalizedHit = path.normalize(hit.file)
      const normalizedAllowed = path.normalize(allowed)
      if (
        normalizedHit === normalizedAllowed ||
        normalizedHit.startsWith(normalizedAllowed + path.sep)
      ) {
        return true
      }
    }
  }
  return false
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
    description: 'Tailwind raw z-index utility（使用 tokenized arbitrary z-index 或受控浮层 token）',
    pattern: '\\bz-\\d+\\b',
    paths: [frontendSrc],
    allowPaths: new Set(),
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
  try {
    return execFileSync('rg', ['--no-heading', '--line-number', '--color', 'never', ...args], {
      cwd: repoRoot,
      encoding: 'utf8',
      stdio: ['ignore', 'pipe', 'pipe'],
    })
  } catch (error) {
    if (error.status === 1) return ''
    if (error.status === 2 || error.status === 127 || error.code === 'ENOENT') {
      return walkFallback(args)
    }
    throw error
  }
}

function walkFallback(args) {
  const patternIndex = args.indexOf('-e')
  const pattern = patternIndex >= 0 ? args[patternIndex + 1] : args[0]
  const searchRoots = args.slice(patternIndex >= 0 ? patternIndex + 2 : 1)
  const regex = new RegExp(pattern)
  const results = []

  function walk(directory) {
    let entries
    try {
      entries = fs.readdirSync(directory, { withFileTypes: true })
    } catch {
      return
    }
    for (const entry of entries) {
      const file = path.join(directory, entry.name)
      if (entry.isDirectory()) {
        walk(file)
      } else if (/\.(vue|ts|css|tsx|js)$/.test(entry.name)) {
        const lines = fs.readFileSync(file, 'utf8').split(/\r?\n/)
        for (let index = 0; index < lines.length; index++) {
          regex.lastIndex = 0
          if (regex.test(lines[index])) results.push(`${file}:${index + 1}:${lines[index]}`)
        }
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
    .filter(Boolean)
    .map((line) => {
      const match = line.match(/^(.*?):(\d+):(.*)$/)
      return match
        ? { file: match[1], line: Number(match[2]), text: match[3] }
        : { file: line, line: 0, text: '' }
    })
}

function collectVueFiles(directory, files = []) {
  let entries
  try {
    entries = fs.readdirSync(directory, { withFileTypes: true })
  } catch {
    return files
  }
  for (const entry of entries) {
    const file = path.join(directory, entry.name)
    if (entry.isDirectory()) collectVueFiles(file, files)
    else if (entry.isFile() && entry.name.endsWith('.vue')) files.push(file)
  }
  return files
}

function sourceLine(source, index) {
  return source.slice(0, index).split(/\r?\n/).length
}

function stripNonExecutableComments(source) {
  return source
    .replace(/<!--[\s\S]*?-->/g, (comment) => comment.replace(/[^\n]/g, ' '))
    .replace(/\/\*[\s\S]*?\*\//g, (comment) => comment.replace(/[^\n]/g, ' '))
    .replace(/^\s*\/\/.*$/gm, '')
}

function classTokens(value) {
  return new Set(value.trim().split(/\s+/).filter(Boolean))
}

function requiredTokenViolations(tokens, required) {
  return required.filter((token) => !tokens.has(token))
}

function forbiddenTokenViolations(tokens, forbidden) {
  return forbidden.filter((token) => tokens.has(token))
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
        const offset = styleMatch.index + styleMatch[0].indexOf(styleSource) + focusMatch.index
        violations.push({
          id: 'component-focus-shadow-token',
          description: `业务组件 :focus-visible 的 box-shadow 必须精确使用 ${requiredValue}`,
          hit: {
            file,
            line: sourceLine(source, offset),
            text: `${focusMatch[1].trim()} { box-shadow: ${shadowMatch[1].trim()}; }`,
          },
        })
      }
    }
  }
  return violations
}

function extractSingleQuotedValue(source, pattern, label) {
  const match = source.match(pattern)
  if (!match) throw new Error(`Unable to parse ${label}`)
  return match[1]
}

function nestedTooltipAnchorViolations(file, source, containerTag) {
  const violations = []
  const containerPattern = new RegExp(
    `<${containerTag}\\b[^>]*>([\\s\\S]*?)<\\/${containerTag}>`,
    'g',
  )
  let containerMatch
  while ((containerMatch = containerPattern.exec(source)) !== null) {
    const body = containerMatch[1]
    const bodyOffset = containerMatch.index + containerMatch[0].indexOf(body)
    const tooltipPattern = /<TooltipText\b[^>]*>/g
    let tooltipMatch
    while ((tooltipMatch = tooltipPattern.exec(body)) !== null) {
      if (/\banchor\s*=\s*["']parent["']/.test(tooltipMatch[0])) continue
      violations.push({
        id: 'business-tooltip-anchor-contract',
        description: `${containerTag} 内的 TooltipText 必须锚定完整父交互控件`,
        hit: {
          file,
          line: sourceLine(source, bodyOffset + tooltipMatch.index),
          text: tooltipMatch[0],
        },
      })
    }
  }
  return violations
}

function findTooltipContractViolations() {
  const violations = []
  const rawTooltipSource = fs.readFileSync(tooltipContent, 'utf8')
  const tooltipSource = stripNonExecutableComments(rawTooltipSource)
  const tooltipClassValue = extractSingleQuotedValue(
    tooltipSource,
    /cn\(\s*'([^']+)'/,
    'TooltipContent class contract',
  )
  const tooltipTokens = classTokens(tooltipClassValue)
  const requiredTooltipTokens = [
    'bg-surface',
    'text-popover-foreground',
    'border-input',
    'w-max',
    'max-w-[var(--content-tooltip-max-inline-size)]',
    'break-words',
    'text-xs',
    'shadow-[var(--shadow-whisper)]',
  ]
  const forbiddenTooltipTokens = [
    'bg-foreground',
    'text-background',
    'max-w-xs',
    'text-sm',
    'shadow-ring-deep',
  ]
  const missingTooltipTokens = requiredTokenViolations(tooltipTokens, requiredTooltipTokens)
  const presentForbiddenTooltipTokens = forbiddenTokenViolations(
    tooltipTokens,
    forbiddenTooltipTokens,
  )
  const hasDirectionalMotion = [...tooltipTokens].some((token) => token.includes('slide-in-from-'))
  const hasDefaultOffset = /\bsideOffset:\s*6\b/.test(tooltipSource)
  const forwardsOffset = /:side-offset=["']props\.sideOffset["']/.test(tooltipSource)

  if (
    missingTooltipTokens.length > 0 ||
    presentForbiddenTooltipTokens.length > 0 ||
    hasDirectionalMotion ||
    !hasDefaultOffset ||
    !forwardsOffset
  ) {
    violations.push({
      id: 'tooltip-surface-contract',
      description: 'Tooltip primitive 必须使用统一 surface、边界、阴影、尺寸、间距和纯透明度动效',
      hit: {
        file: tooltipContent,
        line: sourceLine(rawTooltipSource, rawTooltipSource.indexOf("cn(\n          '")),
        text: JSON.stringify({
          missingTooltipTokens,
          presentForbiddenTooltipTokens,
          hasDirectionalMotion,
          hasDefaultOffset,
          forwardsOffset,
        }),
      },
    })
  }

  const rawDropdownSource = fs.readFileSync(sharedDropdown, 'utf8')
  const dropdownSource = stripNonExecutableComments(rawDropdownSource)
  const dropdownClassValue = extractSingleQuotedValue(
    dropdownSource,
    /dropdownContentClasses\s*=\s*'([^']+)'/,
    'shared dropdown class contract',
  )
  const dropdownTokens = classTokens(dropdownClassValue)
  const missingDropdownTokens = requiredTokenViolations(dropdownTokens, [
    'rounded-md',
    'border',
    'border-input',
    'bg-surface',
    'shadow-[var(--shadow-whisper)]',
  ])
  const forbiddenDropdownTokens = forbiddenTokenViolations(dropdownTokens, [
    'border-border',
    'border-transparent',
    'shadow-md',
    'shadow-ring-deep',
  ])
  if (missingDropdownTokens.length > 0 || forbiddenDropdownTokens.length > 0) {
    violations.push({
      id: 'dropdown-surface-border-contract',
      description: 'Dropdown、Select 与 Combobox 必须复用表单边界与单层低浮层阴影',
      hit: {
        file: sharedDropdown,
        line: sourceLine(rawDropdownSource, rawDropdownSource.indexOf('dropdownContentClasses')),
        text: JSON.stringify({ missingDropdownTokens, forbiddenDropdownTokens }),
      },
    })
  }

  for (const file of businessComponentRoots.flatMap((root) => collectVueFiles(root))) {
    const rawSource = fs.readFileSync(file, 'utf8')
    const source = stripNonExecutableComments(rawSource)
    const localOffsetPattern = /<TooltipContent\b[^>]*\b:?side-offset\s*=/g
    let localOffsetMatch
    while ((localOffsetMatch = localOffsetPattern.exec(source)) !== null) {
      violations.push({
        id: 'business-tooltip-offset-contract',
        description: '业务组件不得覆盖共享 Tooltip primitive 的 trigger 间距',
        hit: {
          file,
          line: sourceLine(rawSource, localOffsetMatch.index),
          text: localOffsetMatch[0],
        },
      })
    }
    violations.push(...nestedTooltipAnchorViolations(file, source, 'DropdownMenuItem'))
    violations.push(...nestedTooltipAnchorViolations(file, source, 'DropdownMenuTrigger'))
  }

  return violations
}

const failures = []
const allowed = []

for (const check of checks) {
  const hits = normalizeHits(runRipgrep(['-e', check.pattern, ...check.paths]))
  for (const hit of hits) {
    if (isAllowed(hit, check.allowPaths)) {
      allowed.push({ id: check.id, hit, reason: 'token-source' })
    } else if (check.allowHit?.(hit)) {
      allowed.push({ id: check.id, hit, reason: 'explicit-rule' })
    } else if (check.isVariableLine && isAllowedSemanticVariable(hit)) {
      allowed.push({ id: check.id, hit, reason: 'semantic-variable' })
    } else {
      failures.push({ id: check.id, description: check.description, hit })
    }
  }
}

failures.push(...findComponentFocusShadowViolations())
failures.push(...findTooltipContractViolations())

if (allowed.length > 0) {
  console.log('--- ALLOWED HITS ---')
  for (const { id, hit, reason } of allowed) {
    console.log(`  [${id}] ${reason}: ${hit.file}:${hit.line}`)
  }
}

if (failures.length === 0) {
  console.log('--- UI guardrail: PASS ---')
  process.exit(0)
}

console.log('--- UI guardrail: VIOLATION ---')
const grouped = new Map()
for (const failure of failures) {
  if (!grouped.has(failure.id)) {
    grouped.set(failure.id, { description: failure.description, hits: [] })
  }
  grouped.get(failure.id).hits.push(failure.hit)
}
for (const [id, group] of grouped) {
  console.log(`\n[${id}] ${group.description}`)
  for (const hit of group.hits) {
    console.log(`  ${hit.file}:${hit.line}  ${hit.text.trim()}`)
  }
}
process.exit(1)
