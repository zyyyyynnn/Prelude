import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { normalize } from 'node:path'
import { fileURLToPath } from 'node:url'
import { minifySync } from 'oxc-minify'
import { parseSync } from 'oxc-parser'
import { ResolverFactory } from 'oxc-resolver'
import { isolatedDeclarationSync, transformSync } from 'oxc-transform'

const root = fileURLToPath(new URL('../', import.meta.url))
const entry = `${root}src/app/main.tsx`
const declarationEntry = `${root}src/features/report/types.ts`
const entrySource = readFileSync(entry, 'utf8')
const declarationSource = readFileSync(declarationEntry, 'utf8')

for (const [filename, source] of [
  ['main.tsx', entrySource],
  ['types.ts', declarationSource],
]) {
  const result = parseSync(filename, source)
  assert.equal(result.errors.length, 0, `${filename} must parse without errors`)
}

const resolver = new ResolverFactory({
  tsconfig: 'auto',
  extensions: ['.ts', '.tsx', '.js', '.jsx', '.json'],
  conditionNames: ['node', 'import'],
})
const resolved = resolver.resolveFileSync(entry, '@/features/report/types')
assert.equal(
  normalize(resolved.path),
  normalize(declarationEntry),
  'tsconfig paths must resolve through oxc-resolver',
)

const transformed = transformSync('main.tsx', entrySource, {
  lang: 'tsx',
  target: 'es2023',
  jsx: { runtime: 'automatic' },
})
assert.equal(transformed.errors.length, 0, 'TSX must transform without errors')
assert.match(transformed.code, /jsx/, 'the automatic JSX runtime must be emitted')

const declaration = isolatedDeclarationSync('types.ts', declarationSource)
assert.equal(declaration.errors.length, 0, 'isolated declarations must emit without errors')
assert.match(
  declaration.code,
  /export type StructuredInterviewReport/,
  'isolated declarations must preserve exported types',
)

const minified = minifySync('main.js', transformed.code, {
  compress: { target: 'es2023' },
  mangle: false,
  codegen: { removeWhitespace: true },
})
assert.equal(minified.errors.length, 0, 'transformed output must minify without errors')
assert.ok(minified.code.length < transformed.code.length, 'minification must reduce output size')

console.log(
  `Oxc toolchain verification: PASS (${transformed.code.length} -> ${minified.code.length} bytes)`,
)
