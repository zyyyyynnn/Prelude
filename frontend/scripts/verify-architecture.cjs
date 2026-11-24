#!/usr/bin/env node
'use strict'

const fs = require('node:fs')
const path = require('node:path')
const { parseSync, visitorKeys } = require('oxc-parser')
const { utilityPositions } = require('./utility-names.cjs')

const root = process.argv[2] ? path.resolve(process.argv[2]) : path.resolve(__dirname, '..')
const sourceRoot = path.join(root, 'src')
const allowedRoots = new Set(['app', 'features', 'shared'])
const blockedPackages = [
  'vue',
  'vue-router',
  'pinia',
  'reka-ui',
  'radix-vue',
  'vue-tsc',
  'html2canvas',
  'jspdf',
]
const sourceExtensions = new Set(['.ts', '.tsx', '.js', '.jsx', '.vue', '.css'])

function parseTree(file, source) {
  const result = parseSync(file, source, { sourceType: 'unambiguous', preserveParens: false })
  if (result.errors.length) {
    throw new Error(`${file}: ${result.errors.map((error) => error.message).join('; ')}`)
  }
  return result.program
}

function walkAst(node, visit, ancestors = []) {
  if (!node || typeof node !== 'object') return
  if (Array.isArray(node)) {
    for (const child of node) walkAst(child, visit, ancestors)
    return
  }
  if (typeof node.type !== 'string') return
  visit(node, ancestors)
  const nextAncestors = [...ancestors, node]
  for (const key of visitorKeys[node.type] ?? []) walkAst(node[key], visit, nextAncestors)
}

function importSpecifiers(file, source) {
  const specifiers = []
  const tree = parseTree(file, source)
  walkAst(tree, (node) => {
    if (
      (node.type === 'ImportDeclaration' ||
        node.type === 'ExportNamedDeclaration' ||
        node.type === 'ExportAllDeclaration' ||
        node.type === 'ImportExpression') &&
      node.source?.type === 'Literal'
    ) {
      specifiers.push(node.source.value)
      return
    }
    if (
      node.type === 'CallExpression' &&
      node.callee?.type === 'Identifier' &&
      node.callee.name === 'require' &&
      node.arguments[0]?.type === 'Literal'
    ) {
      specifiers.push(node.arguments[0].value)
    }
  })
  return specifiers
}
const cssImportPattern = /@import\s+(?:url\(\s*)?['"]([^'"]+)['"]\s*\)?/g
const violations = []

function walk(directory) {
  return fs.readdirSync(directory, { withFileTypes: true }).flatMap((entry) => {
    const target = path.join(directory, entry.name)
    return entry.isDirectory() ? walk(target) : [target]
  })
}

for (const entry of fs.readdirSync(sourceRoot, { withFileTypes: true })) {
  if (entry.isDirectory() && !allowedRoots.has(entry.name)) {
    violations.push(`src/${entry.name}: source must live under app, features, or shared`)
  }
}

for (const file of walk(sourceRoot).filter((item) => sourceExtensions.has(path.extname(item)))) {
  const relative = path.relative(sourceRoot, file).replaceAll('\\', '/')
  if (file.endsWith('.vue')) violations.push(`${relative}: Vue source is forbidden`)
  const source = fs.readFileSync(file, 'utf8')
  const specifiers = []
  if (path.extname(file) === '.css') {
    let cssMatch
    while ((cssMatch = cssImportPattern.exec(source)) !== null) specifiers.push(cssMatch[1])
  } else {
    specifiers.push(...importSpecifiers(file, source))
  }
  for (const specifier of specifiers) {
    if (specifier === 'cn' && relative !== 'shared/lib/cn.ts') {
      violations.push(`${relative}: cn must be imported from @/shared/lib/cn`)
    }
    const resolved = specifier.startsWith('@/')
      ? specifier.slice(2)
      : specifier.startsWith('.')
        ? path.posix.normalize(path.posix.join(path.posix.dirname(relative), specifier))
        : null
    if (!resolved) continue
    if (relative.startsWith('shared/') && /^(app|features)(\/|$)/.test(resolved)) {
      violations.push(`${relative}: shared cannot import ${specifier}`)
    }
    if (relative.startsWith('features/') && /^app(\/|$)/.test(resolved)) {
      violations.push(`${relative}: features cannot import ${specifier}`)
    }
    if (relative.startsWith('features/') && resolved.startsWith('features/')) {
      const sourceFeature = relative.split('/')[1]
      const targetParts = resolved.split('/')
      if (targetParts[1] !== sourceFeature && targetParts.length > 2) {
        violations.push(`${relative}: cross-feature imports must use @/features/${targetParts[1]}`)
      }
    }
  }
}

const packageJson = JSON.parse(fs.readFileSync(path.join(root, 'package.json'), 'utf8'))
const declared = { ...packageJson.dependencies, ...packageJson.devDependencies }
for (const name of blockedPackages) {
  if (declared[name]) violations.push(`package.json: blocked dependency ${name}`)
}

// ---------------------------------------------------------------- glyph rendering
/* A glyph is a value, so importing one from the icon package is free. Rendering one is not:
   `shared/ui/Icon` owns the box token, the inherited colour and the decorative `aria-hidden`,
   and a call site that writes `<RiSearchLine />` re-decides all three in a file no
   design-system rule reaches — which is how an icon ends up at a size no token names. */
{
  const bareGlyph = /^Ri[A-Z]/
  for (const file of walk(sourceRoot).filter((entry) => path.extname(entry) === '.tsx')) {
    const source = fs.readFileSync(file, 'utf8')
    const tree = parseTree(file, source)
    const relative = path.relative(sourceRoot, file).replaceAll('\\', '/')
    const rendered = new Set()
    walkAst(tree, (node) => {
      if (
        (node.type === 'JSXOpeningElement' || node.type === 'JSXSelfClosingElement') &&
        node.name?.type === 'JSXIdentifier' &&
        bareGlyph.test(node.name.name)
      ) {
        rendered.add(node.name.name)
      }
    })
    if (rendered.size) {
      violations.push(
        `${relative}: bare glyph render of ${[...rendered].join(', ')} — a glyph renders through <Icon as={…} />`,
      )
    }
  }
}

// ---------------------------------------------------------------- adopted source provenance
/* `beautiful-ui.sources.json` is the attribution record for vendored components: upstream
   name, upstream file, and the local file that now carries the code. The upstream fields
   describe someone else's tree and cannot be checked from here; `localPath` can, and a
   renamed local file leaves an attribution pointing at nothing — the one record whose whole
   job is to be findable. */
{
  const sourcesFile = path.join(root, 'beautiful-ui.sources.json')
  if (fs.existsSync(sourcesFile)) {
    const parsed = JSON.parse(fs.readFileSync(sourcesFile, 'utf8'))
    for (const entry of parsed.sources ?? []) {
      if (typeof entry.localPath !== 'string') {
        violations.push(`beautiful-ui.sources.json: ${entry.component} has no localPath`)
        continue
      }
      if (!fs.existsSync(path.join(root, entry.localPath))) {
        violations.push(
          `beautiful-ui.sources.json: ${entry.component} attributes to ${entry.localPath}, which does not exist`,
        )
      }
    }
  }
}

// ---------------------------------------------------------------- feature public entry
/* A public entry — a feature's `index.ts`, or the design system's — is a barrel that
   re-exports chosen names and nothing else. Two failures follow from putting implementation
   there: the barrel exports whatever happens to be defined in a file rather than what was
   chosen, so the surface grows by accident; and a sibling that reaches a neighbour through
   the barrel imports the whole surface, including itself, back into its own module.
   `shared/ui` is in scope for the same reason as a feature, and additionally because it had
   no entry at all: 95 call sites named a file, so renaming or merging one primitive touched
   up to 17 unrelated places while `features/*` was being held to the opposite rule. */
{
  const tsSources = walk(sourceRoot).filter((file) => ['.ts', '.tsx'].includes(path.extname(file)))
  const rel = (file) => path.relative(sourceRoot, file).replaceAll('\\', '/')
  const featuresRoot = path.join(sourceRoot, 'features')
  const featureDirs = fs.existsSync(featuresRoot)
    ? fs.readdirSync(featuresRoot, { withFileTypes: true }).filter((e) => e.isDirectory())
    : []

  const publicEntries = featureDirs
    .map((entry) => ({
      label: `features/${entry.name}`,
      /* A feature without an entry needs no rule of its own: the cross-feature check above
         already rejects any import that cannot name `@/features/<name>`. */
      entryPath: path.join(featuresRoot, entry.name, 'index.ts'),
      inside: (relative) => relative.startsWith(`features/${entry.name}/`),
    }))
    .filter((surface) => fs.existsSync(surface.entryPath))
  const sharedUiRoot = path.join(sourceRoot, 'shared/ui')
  const sharedUiEntry = path.join(sharedUiRoot, 'index.ts')
  if (fs.existsSync(sharedUiRoot)) {
    /* A design system with no entry is the defect this rule exists to catch, so say so
       rather than skipping the surface and passing quietly. */
    if (!fs.existsSync(sharedUiEntry)) {
      violations.push('shared/ui has no index.ts — its primitives have no public surface')
    } else {
      publicEntries.push({
        label: 'shared/ui',
        entryPath: sharedUiEntry,
        inside: (relative) => relative.startsWith('shared/ui/'),
      })
    }
  }

  /* Every way one module can name another, and what it takes from the module it names.

     A plain text scan for `from '@/…'` was the first version of this rule; it read as green
     while the router loaded five of its six pages through `await import('@/features/x/Page')`,
     because a lazy route bypasses an entry without anyone typing `from`. A relative specifier
     names the same file through another syntax, so it resolves and is judged the same way. */
  const patternNames = (pattern) => {
    if (!pattern) return []
    if (pattern.type === 'Identifier') return [pattern.name]
    if (pattern.type === 'AssignmentPattern') return patternNames(pattern.left)
    if (pattern.type === 'RestElement') return patternNames(pattern.argument)
    if (pattern.type === 'ArrayPattern') {
      return pattern.elements.flatMap((element) => patternNames(element))
    }
    if (pattern.type === 'ObjectPattern') {
      return pattern.properties.flatMap((property) => {
        if (property.type === 'RestElement') return patternNames(property.argument)
        if (property.type !== 'Property') return []
        if (property.value?.type === 'ObjectPattern' || property.value?.type === 'ArrayPattern') {
          return patternNames(property.value)
        }
        return property.key?.type === 'Identifier' ? [property.key.name] : []
      })
    }
    return []
  }

  const dynamicNames = (call, ancestors) => {
    let current = call
    for (let index = ancestors.length - 1; index >= 0; index--) {
      const anchor = ancestors[index]
      if (anchor.type === 'AwaitExpression') {
        current = anchor
        continue
      }
      if (anchor.type === 'ArrayExpression') {
        const position = anchor.elements.indexOf(current)
        let callIndex = index - 1
        while (
          callIndex >= 0 &&
          !['CallExpression', 'VariableDeclaration', 'AssignmentExpression'].includes(
            ancestors[callIndex].type,
          )
        ) {
          callIndex--
        }
        const bound = ancestors[callIndex]
        let declarationIndex = callIndex - 1
        while (
          declarationIndex >= 0 &&
          ['AwaitExpression', 'ParenthesizedExpression'].includes(ancestors[declarationIndex].type)
        ) {
          declarationIndex--
        }
        const declaration = ancestors[declarationIndex]
        const pattern =
          declaration?.type === 'VariableDeclaration'
            ? declaration.id
            : declaration?.type === 'VariableDeclarator'
              ? declaration.id
              : declaration?.type === 'AssignmentExpression'
                ? declaration.left
                : bound?.type === 'VariableDeclarator'
                  ? bound.id
                  : undefined
        const element = pattern?.type === 'ArrayPattern' ? pattern.elements[position] : undefined
        return patternNames(element)
      }
      if (anchor.type === 'MemberExpression') {
        return anchor.property.type === 'Identifier' ? [anchor.property.name] : []
      }
      if (anchor.type === 'VariableDeclarator') return patternNames(anchor.id)
      if (anchor.type === 'VariableDeclaration') return patternNames(anchor.id)
      if (anchor.type === 'AssignmentExpression' && anchor.operator === '=') {
        return patternNames(anchor.left)
      }
      return []
    }
    return []
  }

  const moduleReads = (file) => {
    const source = fs.readFileSync(file, 'utf8')
    const tree = parseTree(file, source)
    const reads = []
    walkAst(tree, (node, ancestors) => {
      let specifier
      let names = []
      if (node.type === 'ImportDeclaration' && node.source?.type === 'Literal') {
        specifier = node.source.value
        names = node.specifiers.flatMap((specifierNode) => {
          if (specifierNode.type === 'ImportNamespaceSpecifier') return ['*']
          return specifierNode.type === 'ImportSpecifier' ? [specifierNode.local.name] : []
        })
      } else if (
        (node.type === 'ExportNamedDeclaration' || node.type === 'ExportAllDeclaration') &&
        node.source?.type === 'Literal'
      ) {
        specifier = node.source.value
        if (node.type === 'ExportNamedDeclaration') {
          names = node.specifiers.map((specifierNode) => {
            const exported = specifierNode.exported
            return exported.type === 'Identifier' ? exported.name : exported.value
          })
        }
      } else if (node.type === 'ImportExpression' && node.source?.type === 'Literal') {
        specifier = node.source.value
        names = dynamicNames(node, ancestors)
      } else if (
        node.type === 'CallExpression' &&
        node.callee?.type === 'Identifier' &&
        node.callee.name === 'require' &&
        node.arguments[0]?.type === 'Literal'
      ) {
        specifier = node.arguments[0].value
        names = dynamicNames(node, ancestors)
      }
      if (specifier) {
        const line = source.slice(0, node.start).split('\n').length
        reads.push({ specifier, line, names })
      }
    })
    return reads
  }

  /* Which module a specifier names, relative to `src`; null for a bare package. */
  const resolvesTo = (fromFile, specifier) => {
    if (specifier.startsWith('@/')) return specifier.slice(2)
    if (!specifier.startsWith('.')) return null
    return path.posix.normalize(
      path.posix.join(path.posix.dirname(fromFile).replaceAll('\\', '/'), specifier),
    )
  }

  /* What an entry declares, read once: the deep-import rule below needs to know whether a file
     is registered there, and the consumer rule needs the export list. */
  const readEntry = (surface) => {
    const source = fs.readFileSync(surface.entryPath, 'utf8')
    const tree = parseTree(surface.entryPath, source)
    const exported = []
    const byFile = new Map()
    for (const statement of tree.body) {
      const isReexport =
        statement.type === 'ExportNamedDeclaration' &&
        statement.source?.type === 'Literal' &&
        Array.isArray(statement.specifiers)
      if (!isReexport) {
        const line = source.slice(0, statement.start).split('\n').length
        violations.push(
          `${surface.label}/index.ts:${line}: a public entry may only re-export named symbols from a named file`,
        )
        continue
      }
      const specifier = statement.source.value
      if (!byFile.has(specifier)) byFile.set(specifier, [])
      for (const element of statement.specifiers) {
        const name =
          element.exported.type === 'Identifier' ? element.exported.name : element.exported.value
        exported.push(name)
        byFile.get(specifier).push(name)
      }
    }
    return { exported, byFile, deepConsumed: new Set() }
  }
  for (const surface of publicEntries) surface.info = readEntry(surface)

  /* Nothing outside the surface may name a file inside it — with one boundary drawn on purpose.
     `app/` (but not `app/lab`, a gallery that decides nothing about loading) is the composition
     root, so it may name a route module directly, but only one the entry registers, and only the
     names that registration exports. Routing every page through `index.ts` instead was measured,
     not assumed — it folded login, settings, resume and position into the entry chunk
     (+8.5 kB gzipped on every first paint, unauthenticated visitors included), because a static
     barrel import drags the whole surface into the entry graph. */
  const readsByFile = new Map(tsSources.map((file) => [file, moduleReads(file)]))
  for (const file of tsSources) {
    const relative = rel(file)
    for (const read of readsByFile.get(file)) {
      const target = resolvesTo(relative, read.specifier)
      if (!target) continue
      const surface = publicEntries.find((entry) => target.startsWith(`${entry.label}/`))
      if (!surface || surface.inside(relative)) continue
      const inner = target.slice(surface.label.length + 1)
      const registered = surface.info.byFile.get(`./${inner}`)
      if (registered && relative.startsWith('app/') && !relative.startsWith('app/lab/')) {
        for (const name of read.names)
          if (registered.includes(name)) surface.info.deepConsumed.add(name)
        continue
      }
      violations.push(
        `${relative}:${read.line}: imports ${read.specifier} — go through @/${surface.label}${
          registered
            ? ''
            : `, or register ${inner} in ${surface.label}/index.ts if the router must load it`
        }`,
      )
    }
  }

  for (const surface of publicEntries) {
    const barrel = surface.label
    const { exported, deepConsumed } = surface.info

    /* Names the rest of the app actually pulls through the entry. Anything else is surface area
       nobody asked for — and a composition-root route read only counts for the name it really
       binds, so an entry cannot buy silence for an unused export by having the router name its
       file. */
    const consumed = new Set()
    for (const file of tsSources) {
      const relative = rel(file)
      if (surface.inside(relative)) continue
      for (const read of readsByFile.get(file)) {
        if (resolvesTo(relative, read.specifier) !== barrel) continue
        for (const name of read.names) consumed.add(name)
      }
    }
    for (const name of deepConsumed) consumed.add(name)
    for (const name of exported) {
      if (consumed.has(name)) continue
      violations.push(
        `${surface.label}/index.ts: ${name} is exported but nothing outside imports it`,
      )
    }
  }
}

// ---------------------------------------------------------------- shared stays generic
/* `shared/**` is the layer every feature builds on, so it must not name a feature. A
   design-system component that hard-codes a business word — the prompt bar deciding a
   resume is called 简历, a settings column owning `--layout-nav-inline-size`,
   a score tile declaring `--score-fill` — has taken a product decision into the
   generic layer, where the next feature inherits it without being asked. The import rule
   above only catches a *code* dependency; this catches a *vocabulary* one, which is how
   the four leaks survived a clean dependency graph.

   That vocabulary arrives through three surfaces, so all three are read. In TS: string
   literals, template spans, and JSX text — `<span>report</span>` is a JsxText node, not a
   StringLiteral, so a walk that stops at literals steps straight over the very leak it
   exists to catch. In the stylesheet: the names it declares, because a custom property
   or an `@utility` is a name every feature reads whether it asked for it or not. */
{
  const featureNames = fs.existsSync(path.join(sourceRoot, 'features'))
    ? fs
        .readdirSync(path.join(sourceRoot, 'features'), { withFileTypes: true })
        .filter((entry) => entry.isDirectory())
        .map((entry) => entry.name)
    : []
  /* A feature directory whose name is also an ordinary English word would fire on prose,
     so the test is the directory name as a path segment or a `--<name>-` token, never a
     bare substring. With no feature directory at all the alternation would be empty and
     match every slash, so the rule reports nothing rather than everything. */
  const featureReference = featureNames.length
    ? new RegExp(`(?:/|\\b)(?:${featureNames.join('|')})(?:/|\\b)|--(?:${featureNames.join('|')})-`)
    : null

  /* A shared declaration that names a feature directory is a leak. Geometry that is
     one feature's presentation contract is named for its geometry (`document-columns`,
     `item-grid`), never for the feature that first needed it. */
  const sharedVocabularyExceptions = []
  const exceptionByName = new Map(sharedVocabularyExceptions.map((entry) => [entry.name, entry]))

  /* The names a shared stylesheet declares, each with where it is first written. A
     declaration is a claim every feature inherits; a reference (`var(--x)`) only repeats
     the claim its declaration already made, so names are read once. Only declared names
     are read: comments are prose, a BEM modifier (`page--auth`) is not a custom property,
     and a property name (`max-inline-size`) is standard CSS that cannot name a feature. */
  const declaredNames = new Map()
  const sharedStylesheets = walk(sourceRoot).filter((file) => {
    const relative = path.relative(sourceRoot, file).replaceAll('\\', '/')
    return relative.startsWith('shared/') && path.extname(file) === '.css'
  })
  for (const file of sharedStylesheets) {
    const relative = path.relative(sourceRoot, file).replaceAll('\\', '/')
    const sheet = fs
      .readFileSync(file, 'utf8')
      .replace(/\/\*[\s\S]*?\*\//g, (comment) => comment.replace(/[^\n]/g, ' '))
    const record = (name, index) => {
      if (declaredNames.has(name)) return
      declaredNames.set(name, { file: relative, line: sheet.slice(0, index).split('\n').length })
    }
    for (const match of sheet.matchAll(/(?<![-\w])--[\w-]+/g)) record(match[0], match.index)
    for (const match of utilityPositions(sheet)) record(match[1], match.index)
  }
  for (const [name, where] of declaredNames) {
    if (!featureReference || !featureReference.test(name)) continue
    if (exceptionByName.has(name)) continue
    violations.push(
      `${where.file}:${where.line}: declares ${name}, which names the feature "${name.match(featureReference)[0]}" — a shared stylesheet must not carry a feature's vocabulary`,
    )
  }
  /* The exception list is only honest while it still describes the sheet, so an entry
     whose declaration is gone is a finding. A tree with no shared stylesheet has no
     stylesheet surface at all — the unit tests below build exactly that — and calling
     every entry stale there would be the reader finding nothing and naming it a
     finding. */
  if (sharedStylesheets.length) {
    for (const { name, reason } of sharedVocabularyExceptions) {
      if (declaredNames.has(name)) continue
      violations.push(
        `shared/styles: ${name} is registered as a vocabulary exception but is no longer declared — drop the entry (${reason})`,
      )
    }
  }

  const sharedFiles = walk(sourceRoot).filter((file) => {
    const relative = path.relative(sourceRoot, file).replaceAll('\\', '/')
    return relative.startsWith('shared/') && ['.ts', '.tsx'].includes(path.extname(file))
  })
  for (const file of sharedFiles) {
    const relative = path.relative(sourceRoot, file).replaceAll('\\', '/')
    const tree = parseTree(file, fs.readFileSync(file, 'utf8'))
    walkAst(tree, (node) => {
      /* String literals, template spans and JSX text only: an identifier named
         `settings` is a local variable, not a claim about the product. */
      const texts = []
      if (node.type === 'Literal' && typeof node.value === 'string') texts.push(node.value)
      if (node.type === 'TemplateElement') texts.push(node.value.raw)
      if (node.type === 'JSXText') texts.push(node.value)
      for (const text of texts) {
        if (featureReference && featureReference.test(text)) {
          violations.push(
            `${relative}: names the feature "${text.match(featureReference)[0]}" — a shared component must not carry a feature's vocabulary`,
          )
        }
      }
    })
  }
}

if (violations.length) {
  console.error(`Architecture verification: FAIL (${violations.length})`)
  for (const violation of violations) console.error(`  ${violation}`)
  process.exit(1)
}
console.log('Architecture verification: PASS')
