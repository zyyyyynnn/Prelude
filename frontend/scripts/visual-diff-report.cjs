/**
 * Collects Playwright visual diffs into one folder so a reviewer can open the
 * failed frame, its actual capture and the side-by-side diff without walking
 * `test-results/`. Baselines themselves are still authored only on a Windows
 * workstation via `npm run snapshot:update`.
 */
const fs = require('node:fs')
const path = require('node:path')

const root = path.resolve(__dirname, '..')
const resultsDir = path.join(root, 'test-results')
const outDir = path.join(root, 'output', 'visual-diffs')

function walk(dir, visit) {
  if (!fs.existsSync(dir)) return
  for (const entry of fs.readdirSync(dir, { withFileTypes: true })) {
    const full = path.join(dir, entry.name)
    if (entry.isDirectory()) walk(full, visit)
    else visit(full)
  }
}

function main() {
  fs.rmSync(outDir, { recursive: true, force: true })
  fs.mkdirSync(outDir, { recursive: true })
  const copies = []
  walk(resultsDir, (file) => {
    const name = path.basename(file)
    if (!/-actual\.png$|-diff\.png$|-expected\.png$/.test(name)) return
    const target = path.join(outDir, name)
    fs.copyFileSync(file, target)
    copies.push(name)
  })
  copies.sort()
  const report = [
    '# Visual diffs',
    '',
    'Baselines live in `tests/app.surfaces.spec.ts-snapshots/*-win32.png`.',
    'Refresh them only on a Windows workstation after reviewing these frames:',
    '',
    '```powershell',
    'npm run snapshot:update',
    '```',
    '',
    copies.length
      ? copies.map((name) => `- ${name}`).join('\n')
      : '_No visual diffs found in test-results._',
    '',
  ].join('\n')
  fs.writeFileSync(path.join(outDir, 'README.md'), report, 'utf8')
  process.stdout.write(`visual-diffs: ${copies.length} file(s) -> output/visual-diffs/\n`)
}

main()
