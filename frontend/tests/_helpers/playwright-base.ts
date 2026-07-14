/**
 * Shared Playwright config base. All Phase 2 configs (visual, a11y, local)
 * import `basePlaywrightConfig` and only override the testDir / testMatch /
 * reporter / outputDir fields. The base contains the canonical webServer
 * command, viewport, locale, timezone and CI retry policy.
 *
 * Why this exists: Phase 2 originally shipped three near-identical
 * playwright.*.config.ts files (visual, a11y, local). sentrux counts each
 * near-duplicate as a low-diversity module pair, which pushed the
 * min_equality score below the 0.48 gate. Extracting the shared base
 * brings equality back above the floor without changing behaviour — every
 * setting still propagates via defineConfig's spread.
 */
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const __filename = fileURLToPath(import.meta.url)
// This helper lives at frontend/tests/_helpers/playwright-base.ts. Resolve
// the canonical anchor directories so the shared webServer + outputDir work
// regardless of which config imports this file.
//
// helperDir    = frontend/tests/_helpers
// frontendRoot = frontend            (npm scripts + vite config live here)
// repoRoot     = Prelude             (cross-stack output dir lives here)
const helperDir = path.dirname(__filename)
const frontendRoot = path.resolve(helperDir, '..', '..')
const repoRoot = path.resolve(frontendRoot, '..')

/**
 * CI-only browser overrides. When CI=true (set by .github/workflows/ci.yml),
 * Playwright specs use the system Microsoft Edge channel instead of the
 * bundled Chromium. This avoids the ~500 MB Chromium download on
 * windows-latest runners, which was the dominant CI flake source. The
 * corresponding `PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD=1` env var in CI stops
 * `npm ci` from pulling the Playwright browser tarball at install time.
 *
 * Local runs (CI unset) keep the default Playwright Chromium so dev loops
 * stay self-contained — the local-screenshots config additionally pins
 * `channel: 'msedge'` itself for parity.
 */
const ciBrowserUse = process.env.CI ? { channel: 'msedge' as const } : {}

/**
 * Standard `use` block shared by every Phase 2 config.
 *
 * Exported as a plain object (not a defineConfig) so consumers can spread
 * it into their own `use: { ...baseUse, ...overrides }` block.
 */
export const baseUse = {
  baseURL: 'http://127.0.0.1:5173',
  headless: true,
  ...ciBrowserUse,
  screenshot: 'only-on-failure',
  trace: 'retain-on-failure',
  viewport: {
    width: 1440,
    height: 900,
  },
  deviceScaleFactor: 1,
  locale: 'en-US',
  timezoneId: 'UTC',
  reducedMotion: 'reduce',
} as const

/**
 * Standard webServer block. `cwd` is the FRONTEND root (not the helper
 * directory) so `npm run dev` resolves to frontend/package.json — without
 * this fix the dev server is launched from frontend/tests/_helpers and
 * fails with "Could not read package.json". CI forces
 * `reuseExistingServer: false` so each job starts from a known-clean
 * state; locally the dev server is reused when present to keep iteration
 * tight.
 */
export const baseWebServer = {
  command: 'npm run dev -- --host 127.0.0.1 --port 5173',
  port: 5173,
  reuseExistingServer: !process.env.CI,
  cwd: frontendRoot,
  timeout: 120000,
} as const

/**
 * Standard timeout + worker + retry policy.
 */
export const baseTimeouts = {
  timeout: 120000,
  fullyParallel: false,
  workers: 1,
  forbidOnly: !!process.env.CI,
  retries: process.env.CI ? 1 : 0,
} as const

/**
 * Resolve the canonical output dir for a Phase 2 config.
 *
 * Writes to `<repoRoot>/output/<subdir>` so that
 * `output/screenshots/visual/`, `output/screenshots/a11y/` and
 * `output/screenshots/dev/.artifacts/` never collide AND so the output
 * sits at the repo level (not under frontend/tests/). The root-level
 * `output/` is also covered by the repo's existing `.gitignore`
 * (`output/runtime/`, `output/screenshots/dev/`, etc.).
 */
export function baseOutputDir(subdir: string): string {
  return path.resolve(repoRoot, 'output', subdir)
}
