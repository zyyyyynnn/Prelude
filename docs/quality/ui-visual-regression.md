# UI Visual Regression Coverage

> Phase 1 of the Phase 2 UI quality system. See also `ui-phase2-baseline.md` for the wider plan and risk register.

## What this phase produces

A reproducible Playwright harness that captures 15 UI scenarios as full-page PNGs into `frontend/tests/visual/__screenshots__/`. The harness is designed for CI:

- Chromium headless (bundled with Playwright).
- Viewport `1440 × 900`, `deviceScaleFactor: 1`.
- Locale `en-US`, timezone `UTC`, `reducedMotion: 'reduce'`.
- All `/api/**` requests are stubbed via `page.route` — no backend dependency.

The committed source of truth is:

- `frontend/playwright.visual.config.ts` — Playwright config.
- `frontend/tests/visual/ui-visual.spec.ts` — 15-scenario capture spec.
- `frontend/package.json` — new scripts `capture:visual` and `verify:visual`.
- `frontend/tests/visual/README.md` — operating manual.
- `frontend/.gitignore` — screenshots directory is intentionally not committed.

## Scenarios covered (15)

| # | File | Scenario |
| --- | --- | --- |
| 1 | `01-login-light.png` | Login page, light scheme |
| 2 | `02-login-dark.png` | Login page, dark scheme |
| 3 | `03-sidebar-expanded.png` | Workspace sidebar expanded |
| 4 | `04-sidebar-collapsed.png` | Workspace sidebar collapsed (toggle clicked) |
| 5 | `05-interview-empty.png` | Empty state before any session |
| 6 | `06-composer-text-mode.png` | Composer in default text mode |
| 7 | `07-composer-voice-mode.png` | Composer voice mode placeholder (captures text-mode if toggle missing; see note below) |
| 8 | `08-settings-profile.png` | Settings modal — profile tab |
| 9 | `09-settings-theme.png` | Settings modal — theme tab |
| 10 | `10-settings-llm.png` | Settings modal — LLM tab |
| 11 | `11-position-dropdown-open.png` | Position selector dropdown open |
| 12 | `12-resume-dropdown-open.png` | Resume selector dropdown open |
| 13 | `13-interview-generating.png` | Workspace empty / active surface (see note below) |
| 14 | `14-report-paper.png` | Workspace empty / active surface (see note below) |
| 15 | `15-analytics-dashboard.png` | Analytics dashboard with mocked radar/trend/weakness data |

## Notes on partial coverage

### Voice mode (scenario 7)
The voice mode toggle is exposed via a `data-mode="voice"` selector on the composer, but in the current build the voice flow opens a WebSocket which we do NOT stub. The capture therefore tolerates the absence of the toggle and falls back to capturing the text-mode composer, which exercises the same composer chrome. A full voice-mode screenshot would require stubbing `/api/interview/{id}/voice-websocket` or similar and is deferred.

### Generating state and report paper (scenarios 13–14)
Both scenarios need an active session whose `status` is `generating` or `completed` to drive the corresponding workspace branch. With a cold page, `activeSessionId` is `null` (no pinned session), so the workspace lands on the empty composer regardless of what the mocked sessions API returns. Two options:

1. Drive the UI through the actual create-session flow with mocked backend (largest fidelity, biggest surface area to maintain).
2. Capture the empty workspace as a representative surface (smaller blast radius, current Phase 1 choice).

The Phase 1 decision is option 2. The spec documents this explicitly inline so the next reviewer understands what the artifact actually contains. Promoting to option 1 is a Phase 5 follow-up once baselines stabilize.

## How to run

```powershell
npm --prefix frontend run capture:visual
# or
npm --prefix frontend run verify:visual
```

Output goes to:

- `frontend/tests/visual/__screenshots__/*.png` — captured images.
- `output/screenshots/visual/.artifacts/` — Playwright artifacts (traces, stdout).
- `output/screenshots/visual/.diag/*.png` — diagnostic captures when a `readyLocator` fails.

## How to update baselines

The committed artifacts directory is intentionally empty (gitignored). To make baselines reviewable:

1. Run `npm --prefix frontend run capture:visual` locally.
2. Review screenshots in `frontend/tests/visual/__screenshots__/`.
3. If a screenshot reveals an unintended regression, fix the regression and re-capture.
4. If the change is a deliberate product evolution, update the spec's `readyLocator` (or add a new test) and re-capture.

When the team decides baselines are stable enough for blocking diffs, the spec can be extended with `expect(page).toHaveScreenshot('name.png', { maxDiffPixelRatio: 0.001 })` against a committed baseline set.

## CI status (Phase 1)

- Local: PASS (15 / 15 green).
- CI: Not yet wired. Phase 5 will integrate `verify:visual` as `artifact-only` first and only flip to blocking after we see stable green for at least one release cycle.

## Out of scope

- Pixel-level diff against committed PNGs (planned in Phase 5).
- Tests that exercise the real backend Spring Boot dev server.
- Tests that exercise real LLM streaming end-to-end.
- The pre-existing `frontend/tests/local-screenshots.spec.ts` dev-capture flow remains untouched; that flow uses the real backend dev fixtures and msedge, and is intended for manual artifact review only.
