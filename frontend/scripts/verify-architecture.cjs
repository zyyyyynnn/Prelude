#!/usr/bin/env node
'use strict'

const fs = require('node:fs')
const path = require('node:path')
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
const ts = require('typescript')

function importSpecifiers(file, source) {
  const specifiers = []
  const tree = ts.createSourceFile(file, source, ts.ScriptTarget.Latest, true)
  function visit(node) {
    let specifier
    if (ts.isImportDeclaration(node) || ts.isExportDeclaration(node)) {
      specifier = node.moduleSpecifier
    } else if (
      ts.isCallExpression(node) &&
      (node.expression.kind === ts.SyntaxKind.ImportKeyword ||
        (ts.isIdentifier(node.expression) && node.expression.text === 'require'))
    ) {
      specifier = node.arguments[0]
    }
    if (specifier && ts.isStringLiteralLike(specifier)) specifiers.push(specifier.text)
    ts.forEachChild(node, visit)
  }
  visit(tree)
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
    if (!ts.isObjectBindingPattern(pattern) && !ts.isArrayBindingPattern(pattern)) return []
    return pattern.elements.flatMap((element) => {
      if (!ts.isBindingElement(element)) return []
      if (ts.isObjectBindingPattern(element.name) || ts.isArrayBindingPattern(element.name))
        return patternNames(element.name)
      if (element.propertyName && ts.isIdentifier(element.propertyName))
        return [element.propertyName.text]
      return ts.isIdentifier(element.name) ? [element.name.text] : []
    })
  }

  const dynamicNames = (call) => {
    let node = call
    let anchor = node.parent
    while (anchor) {
      if (ts.isAwaitExpression(anchor) || ts.isParenthesizedExpression(anchor)) {
        node = anchor
        anchor = anchor.parent
        continue
      }
      /* Inside an array literal the call is one of `Promise.all`'s tasks, so the name it
         contributes is whatever binds the matching position of the pattern it is assigned to. */
      if (anchor.elements && ts.isArrayLiteralExpression(anchor)) {
        const position = anchor.elements.findIndex((element) => element === node)
        let bound = anchor.parent
        while (bound && ts.isAwaitExpression(bound)) bound = bound.parent
        const pattern =
          bound && ts.isCallExpression(bound)
            ? (() => {
                let declaration = bound.parent
                while (declaration && ts.isAwaitExpression(declaration))
                  declaration = declaration.parent
                if (declaration && ts.isVariableDeclaration(declaration)) return declaration.name
                if (
                  declaration &&
                  ts.isBinaryExpression(declaration) &&
                  ts.isArrayBindingPattern(declaration.left)
                )
                  return declaration.left
                return undefined
              })()
            : undefined
        const element =
          pattern && ts.isArrayBindingPattern(pattern) ? pattern.elements[position] : undefined
        return element && ts.isBindingElement(element) ? patternNames(element.name) : []
      }
      if (ts.isPropertyAccessExpression(anchor)) return [anchor.name.text]
      if (ts.isVariableDeclaration(anchor)) return patternNames(anchor.name)
      if (ts.isBinaryExpression(anchor) && anchor.operatorToken.kind === ts.SyntaxKind.EqualsToken)
        return patternNames(anchor.left)
      return []
    }
    return []
  }

  const moduleReads = (file) => {
    const source = fs.readFileSync(file, 'utf8')
    const tree = ts.createSourceFile(file, source, ts.ScriptTarget.Latest, true)
    const reads = []
    const visit = (node) => {
      let specifier
      let names = []
      if (ts.isImportDeclaration(node)) {
        specifier = node.moduleSpecifier
        const bindings = node.importClause?.namedBindings
        if (bindings && ts.isNamedImports(bindings))
          names = bindings.elements.map((element) => element.name.text)
        else if (bindings) names = ['*']
      } else if (ts.isExportDeclaration(node) && node.moduleSpecifier) {
        specifier = node.moduleSpecifier
        const clause = node.exportClause
        if (clause && ts.isNamedExports(clause))
          names = clause.elements.map((element) => element.name.text)
      } else if (
        ts.isCallExpression(node) &&
        node.arguments[0] &&
        ts.isStringLiteralLike(node.arguments[0]) &&
        (node.expression.kind === ts.SyntaxKind.ImportKeyword ||
          (ts.isIdentifier(node.expression) && node.expression.text === 'require'))
      ) {
        specifier = node.arguments[0]
        names = dynamicNames(node)
      }
      if (specifier) {
        const { line } = ts.getLineAndCharacterOfPosition(tree, node.getStart(tree))
        reads.push({ specifier: specifier.getText(tree).slice(1, -1), line: line + 1, names })
      }
      ts.forEachChild(node, visit)
    }
    visit(tree)
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
    const tree = ts.createSourceFile(surface.entryPath, source, ts.ScriptTarget.Latest, true)
    const exported = []
    const byFile = new Map()
    for (const statement of tree.statements) {
      const isReexport =
        ts.isExportDeclaration(statement) &&
        Boolean(statement.moduleSpecifier) &&
        Boolean(statement.exportClause) &&
        ts.isNamedExports(statement.exportClause)
      if (!isReexport) {
        const line = source.slice(0, statement.getStart(tree)).split('\n').length
        violations.push(
          `${surface.label}/index.ts:${line}: a public entry may only re-export named symbols from a named file`,
        )
        continue
      }
      const specifier = statement.moduleSpecifier.getText(tree).slice(1, -1)
      if (!byFile.has(specifier)) byFile.set(specifier, [])
      for (const element of statement.exportClause.elements) {
        exported.push(element.name.text)
        byFile.get(specifier).push(element.name.text)
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
   resume is called 简历, a settings column owning `--layout-settings-sidebar-inline-size`,
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
     bare substring. */
  const featureReference = new RegExp(
    `(?:/|\\b)(?:${featureNames.join('|')})(?:/|\\b)|--(?:${featureNames.join('|')})-`,
  )

  /* Registered exceptions, each naming one declaration in the shared stylesheet whose
     migration is still owed. The list is enumerable on purpose: the alternatives —
     skipping the stylesheet, or skipping the word — retire the rule instead of the leak.
     An entry whose declaration is gone is itself a violation, so the list cannot rot
     into a permanent exemption. */
  const sharedVocabularyExceptions = [
    {
      name: '--layout-settings-sidebar-inline-size',
      reason:
        "the settings sidebar is one fixed column, not a step on the layout scale; it is the settings feature's presentation contract and moves with it",
    },
    {
      name: '--layout-position-catalog-min-inline-size',
      reason:
        "the position catalog's minimum column is the position feature's presentation contract, not a generic layout step; to move with it",
    },
    {
      name: '--layout-position-form-min-inline-size',
      reason:
        "the position form's minimum width is the position feature's presentation contract, not a generic layout step; to move with it",
    },
    {
      name: '--layout-position-item-min-inline-size',
      reason:
        "the position item grid's minimum column is the position feature's presentation contract, not a generic layout step; to move with it",
    },
    {
      name: '--layout-workspace-report-block-padding',
      reason:
        "the print report band's block padding measures the report's printed page, not the interface scale; it is the report feature's presentation contract, to move with it",
    },
    {
      name: '--layout-report-column-min-inline-size',
      reason:
        "the report's reading-column minimum measures the report's printed page, not the interface scale; it is the report feature's presentation contract, to move with it",
    },
    {
      name: '--layout-report-label-inline-size',
      reason:
        "the report detail row's label column measures the report's printed page, not the interface scale; it is the report feature's presentation contract, to move with it",
    },
    {
      name: '--layout-report-counter-min-inline-size',
      reason:
        "the report counter block's minimum width measures the report's printed page, not the interface scale; it is the report feature's presentation contract, to move with it",
    },
    {
      name: '--content-report-reading-max-inline-size',
      reason:
        "the report reading column's measure sizes the report's printed page, not the interface scale; it is the report feature's presentation contract, to move with it",
    },
    {
      name: 'position-item-grid',
      reason:
        "the position item grid's track recipe is the position feature's presentation contract, not a generic layout utility; to move with it",
    },
    {
      name: 'report-columns',
      reason:
        "the report column track recipe sizes the report's printed page, not the interface scale; it is the report feature's presentation contract, to move with it",
    },
  ]
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
    if (!featureReference.test(name)) continue
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
    const tree = ts.createSourceFile(
      file,
      fs.readFileSync(file, 'utf8'),
      ts.ScriptTarget.Latest,
      true,
    )
    const check = (node) => {
      /* String literals, template spans and JSX text only: an identifier named
         `settings` is a local variable, not a claim about the product. */
      const texts = []
      if (ts.isStringLiteralLike(node)) texts.push(node.text)
      if (ts.isTemplateHead(node) || ts.isTemplateMiddle(node) || ts.isTemplateTail(node))
        texts.push(node.text)
      if (ts.isJsxText(node)) texts.push(node.text)
      for (const text of texts) {
        if (featureReference.test(text)) {
          violations.push(
            `${relative}: names the feature "${text.match(featureReference)[0]}" — a shared component must not carry a feature's vocabulary`,
          )
        }
      }
      ts.forEachChild(node, check)
    }
    ts.forEachChild(tree, check)
  }
}

if (violations.length) {
  console.error(`Architecture verification: FAIL (${violations.length})`)
  for (const violation of violations) console.error(`  ${violation}`)
  process.exit(1)
}
console.log('Architecture verification: PASS')
