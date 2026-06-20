# Visual Regression Coverage

> Phase 1 of the UI quality system. Establishes reproducible screenshot baselines for the core UI surface. See also `ui-phase2-baseline.md`.

## What this is

- `frontend/playwright.visual.config.ts` — dedicated Playwright config.
- `frontend/tests/visual/ui-visual.spec.ts` — 15-scenario capture spec.
- `frontend/tests/visual/__screenshots__/` — captured artifacts (gitignored; see below).

The spec produces **artifacts only** in Phase 1. We do **not** assert pixel diffs yet. The goal is to (a) prove the harness is reproducible, (b) produce a reviewable baseline, and (c) give reviewers a clear place to inspect UI changes.

## Why non-blocking in Phase 1

Pixel-diff gating is fragile on first introduction: it requires careful baseline review, and CI environments with different fonts / DPIs / GPU acceleration routinely produce false positives. Phase 1 captures artifacts only so the team can review baselines offline. Phase 5 / Phase 6 may promote this to a blocking diff once baselines are committed and stable.

## Running

```powershell
npm --prefix frontend run capture:visual
# or
npm --prefix frontend run verify:visual
```

Both call `playwright test -c playwright.visual.config.ts`. Output goes to:

- `frontend/tests/visual/__screenshots__/*.png` — captured images.
- `frontend/output/screenshots/visual/.artifacts/` — Playwright test artifacts (traces, stdout).
- `frontend/output/screenshots/visual/` — listed in `docs/quality/local-review-checklist.md` red-line section as an ignore target.

## Stability guarantees

The spec enforces:

- Chromium headless (NOT msedge) — runs on any CI machine.
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

Once a baseline is reviewed and stable, commit it. The eventual pipeline (Phase 5) is expected to:

1. Run capture against the committed baseline artifacts.
2. Use `expect(page).toHaveScreenshot()` (or a custom PNG diff) for pixel-level regression.

## Failure interpretation

| Symptom | Likely cause |
| --- | --- |
| Capture failed: timed out waiting for `localhost:5173` | Dev server did not start. Run `npm --prefix frontend run dev` manually and inspect output. |
| Capture OK but image shows a 404 page | `/api/**` route stub is missing for a new endpoint. Add a stub in `installMockApi`. |
| Capture OK but image is blank | Selector `readyLocator` does not appear. The route stub returned data that the page did not render; check console. |
| Capture flakes on CI but not locally | Usually font / DPI mismatch. Verify `deviceScaleFactor: 1` and that CI uses bundled fonts (no system fallbacks). |

## Out of scope

- Snapshot diffing against committed PNGs (planned in Phase 5).
- Tests that require the backend Spring Boot dev server.
- Tests that exercise real LLM streaming (those would need network mocks at a much lower level).
