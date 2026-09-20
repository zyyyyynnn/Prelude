#!/usr/bin/env node
'use strict'

const fs = require('node:fs')
const path = require('node:path')

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
/* `docs/frontend/architecture.md` makes a feature's `index.ts` its public surface: a barrel
   that re-exports chosen names and nothing else. Two failures follow from putting
   implementation there. The barrel then exports whatever happens to be defined in the file
   rather than what was chosen, so the public surface grows by accident. And a sibling that
   reaches a neighbour through `../index` imports the whole feature, including itself, back
   into the module that is part of it. */
{
  const tsSources = walk(sourceRoot).filter((file) => ['.ts', '.tsx'].includes(path.extname(file)))
  const featuresRoot = path.join(sourceRoot, 'features')
  const featureDirs = fs.existsSync(featuresRoot)
    ? fs.readdirSync(featuresRoot, { withFileTypes: true })
    : []
  for (const entry of featureDirs) {
    if (!entry.isDirectory()) continue
    const feature = entry.name
    /* A feature without an entry needs no rule of its own: the cross-feature check above
       already rejects any import that cannot name `@/features/<name>`. */
    const entryPath = path.join(featuresRoot, feature, 'index.ts')
    if (!fs.existsSync(entryPath)) continue
    const source = fs.readFileSync(entryPath, 'utf8')
    const tree = ts.createSourceFile(entryPath, source, ts.ScriptTarget.Latest, true)
    const exported = []
    for (const statement of tree.statements) {
      const isReexport =
        ts.isExportDeclaration(statement) &&
        Boolean(statement.moduleSpecifier) &&
        Boolean(statement.exportClause) &&
        ts.isNamedExports(statement.exportClause)
      if (!isReexport) {
        const line = source.slice(0, statement.getStart()).split('\n').length
        violations.push(
          `features/${feature}/index.ts:${line}: a public entry may only re-export named symbols from a named file`,
        )
        continue
      }
      const clause = statement.exportClause
      if (clause) for (const element of clause.elements) exported.push(element.name.text)
    }

    /* Names the rest of the app actually pulls through the barrel. Anything else is surface
       area nobody asked for. The router reaches pages through `await import()`, so a member
       access on a dynamic import counts as a consumer just like a static binding does. */
    const barrel = `@/features/${feature}`
    const consumed = new Set()
    for (const file of tsSources) {
      if (path.basename(file) === 'index.ts' && path.dirname(file) === path.dirname(entryPath))
        continue
      const relative = path.relative(sourceRoot, file).replaceAll('\\', '/')
      if (relative.startsWith(`features/${feature}/`)) continue
      const tree = ts.createSourceFile(
        file,
        fs.readFileSync(file, 'utf8'),
        ts.ScriptTarget.Latest,
        true,
      )
      const collect = (node) => {
        if (ts.isImportDeclaration(node) && node.moduleSpecifier?.text === barrel) {
          const bindings = node.importClause?.namedBindings
          if (bindings && ts.isNamedImports(bindings))
            for (const element of bindings.elements) consumed.add(element.name.text)
          else if (bindings) consumed.add('*')
        }
        if (
          ts.isCallExpression(node) &&
          node.expression.kind === ts.SyntaxKind.ImportKeyword &&
          node.arguments[0] &&
          ts.isStringLiteralLike(node.arguments[0]) &&
          node.arguments[0].text === barrel
        ) {
          /* `(await import('…')).Name` puts an AwaitExpression and often a parenthesis
             between the call and the member access. */
          let anchor = node.parent
          while (anchor && (ts.isAwaitExpression(anchor) || ts.isParenthesizedExpression(anchor)))
            anchor = anchor.parent
          if (anchor && ts.isPropertyAccessExpression(anchor)) consumed.add(anchor.name.text)
          if (
            anchor &&
            ts.isBinaryExpression(anchor) &&
            anchor.operatorToken.kind === ts.SyntaxKind.EqualsToken &&
            ts.isObjectBindingPattern(anchor.left)
          )
            for (const element of anchor.left.elements)
              consumed.add((element.propertyName ?? element.name).getText().replaceAll(/["']/g, ''))
        }
        ts.forEachChild(node, collect)
      }
      ts.forEachChild(tree, collect)
    }
    for (const name of exported) {
      if (consumed.has(name)) continue
      violations.push(
        `features/${feature}/index.ts: ${name} is exported but nothing outside the feature imports it`,
      )
    }
  }
}

if (violations.length) {
  console.error(`Architecture verification: FAIL (${violations.length})`)
  for (const violation of violations) console.error(`  ${violation}`)
  process.exit(1)
}
console.log('Architecture verification: PASS')
