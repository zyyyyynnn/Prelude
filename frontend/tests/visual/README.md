# Visual Regression Coverage

> 当前态见 `docs/quality/ui-quality-system.md` §4 Visual coverage 与 §8 Backlog R5。本文件是该手册的具体操作指南。

## What this is

- `frontend/playwright.visual.config.ts` — dedicated Playwright config.
- `frontend/tests/visual/ui-visual.spec.ts` — 17-scenario capture spec.
- `frontend/tests/visual/__screenshots__/` — captured artifacts (gitignored; see below).

The spec produces **artifacts only**. We do **not** assert pixel diffs. The goal is to (a) prove the harness is reproducible, (b) produce a reviewable baseline, and (c) give reviewers a clear place to inspect UI changes.

## Why artifact-only

Pixel-diff gating is fragile on first introduction: it requires careful baseline review, and CI environments with different fonts / DPIs / GPU acceleration routinely produce false positives. `capture:visual` is `continue-on-error: true` in CI and uploads the 17 PNGs as the `ui-visual-baseline` artifact for human review.

Whether to upgrade `capture:visual` to a blocking pixel-diff gate is tracked as R5 in `docs/quality/ui-quality-system.md` Backlog. Do not promote it in this README; promotion decisions live in that single source of truth.

## Browser strategy

- **本地（默认）**：Playwright 默认 browser（Chromium headless, viewport 1440×900, deviceScaleFactor=1, locale en-US, timezone UTC, reducedMotion: 'reduce'）。
- **CI**：`PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD=1` + 共享 `frontend/tests/_helpers/playwright-base.ts` 在 `process.env.CI` 为真时给 `baseUse` 注入 `channel: 'msedge'`，使用系统 Microsoft Edge channel。`npx playwright install` 在 CI 中被整体移除，避免 windows-latest 上反复出现的 Chromium + headless-shell CDN 下载 stall。

本地 dev 不受 CI 配置影响；`process.env.CI` 未设置时保留默认 Playwright Chromium。

## Running

```powershell
npm --prefix frontend run capture:visual
# or
npm --prefix frontend run verify:visual
```

Both call `playwright test -c playwright.visual.config.ts`. Output goes to:

- `frontend/tests/visual/__screenshots__/*.png` — captured images.
- `output/screenshots/visual/.artifacts/` — Playwright test artifacts (traces, stdout).
- `output/screenshots/visual/.diag/*.png` — diagnostic captures when a `readyLocator` fails.

## Stability guarantees

The spec enforces:

- Viewport `1440 × 900`, `deviceScaleFactor: 1` — no high-DPR scaling surprises.
- Locale `en-US`, timezone `UTC` — no locale- or timezone-dependent formatting.
- `reducedMotion: 'reduce'` — eliminates animation jitter.
- `document.fonts.ready` awaited before screenshot — eliminates font swap flicker.
- All `/api/**` requests are stubbed via `page.route` — no backend dependency.

## Updating baselines

The screenshots directory `frontend/tests/visual/__screenshots__/` is currently **not** committed. To add baselines to the repo:

1. Run `npm --prefix frontend run capture:visual`.
2. Review the new screenshots in `frontend/tests/visual/__screenshots__/`.
3. If a screenshot is wrong because of a real product change, update the test (e.g. update selector or scenario), re-capture, and commit both.
4. If a screenshot is wrong because of an unintended regression, fix the regression and re-capture.

Once a baseline is reviewed and stable, commit it. Promotion to a blocking pixel-diff gate is tracked in `docs/quality/ui-quality-system.md` §8 R5.

## Failure interpretation

| Symptom                                                | Likely cause                                                                                                        |
| ------------------------------------------------------ | ------------------------------------------------------------------------------------------------------------------- |
| Capture failed: timed out waiting for `localhost:5173` | Dev server did not start. Run `npm --prefix frontend run dev` manually and inspect output.                          |
| Capture OK but image shows a 404 page                  | `/api/**` route stub is missing for a new endpoint. Add a stub in `installMockApi`.                                 |
| Capture OK but image is blank                          | Selector `readyLocator` does not appear. The route stub returned data that the page did not render; check console.  |
| Capture flakes on CI but not locally                   | Usually font / DPI mismatch. Verify `deviceScaleFactor: 1` and that the system Microsoft Edge channel is available. |

## Out of scope

- Snapshot diffing against committed PNGs (tracked as R5).
- Tests that require the backend Spring Boot dev server.
- Tests that exercise real LLM streaming (those would need network mocks at a much lower level).
