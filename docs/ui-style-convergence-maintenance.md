# UI Style Convergence Maintenance

## Purpose

This document is the maintenance baseline after the Prelude UI convergence work. `DESIGN.md` is the highest source of truth for UI rules; this file only summarizes the implemented scope, verification path, and non-negotiable boundaries.

## Current scope

- Global token, theme, and motion systems are centralized in `frontend/src/styles/index.css`.
- UI primitives are the default path for controls, overlays, dialogs, toast, badges, cards, empty states, and segmented controls.
- Workspace, composer, message thread, report, and header components use the shared size, font, tooltip, and overlay rules.
- Settings contains three tabs: profile, theme, and LLM configuration.
- Profile supports editable user info and avatar upload. Avatar storage is local filesystem plus static resource mapping.
- Theme supports light, dark, and system preferences.
- Analytics radar is three-dimensional: technical ability, expression clarity, and logical thinking.
- BrandMetaballs uses a dedicated logo token palette to preserve the warm-brown visual hierarchy.
- RoseThree uses SVG plus `requestAnimationFrame`, currentColor, and motion parameters.

## Non-negotiables

- Do not hardcode business colors, heights, fonts, shadows, radius, dates, or z-index values.
- Do not use `transition-all`.
- Do not use native `title=`.
- Do not use `window.confirm`; confirmation flows must use an async Promise control flow.
- Do not bypass UI primitives for normal buttons, inputs, selects, dropdowns, comboboxes, tooltips, dialogs, or toast.
- Do not add chart dimensions without real backend scoring data and API support.
- Do not move theme settings back into the profile page.
- Do not use cloud storage or Base64 database storage for avatars.
- Do not hide, filter, or hardcode around data-source issues in the frontend.

## Verification

```powershell
npm --prefix frontend run build
npm --prefix frontend run verify:byok
npm --prefix frontend run verify:dark
git diff --check
```

If backend, schema, or seed data changes, also run:

```powershell
mvn -f backend/pom.xml test
```

Static scan:

```powershell
rg -n "var\\(--color-text\\)|transition-all|window\\.confirm|title=|shadow-md|border-border|rgba\\(|h-\\[(30|32|34)px\\]|text-\\[[0-9.]+px\\]" frontend DESIGN.md docs
```

## Known visual follow-ups

- BrandMetaballs still needs human visual review when its token palette changes.
- Dark theme needs smoke coverage after token, overlay, chart, canvas, or shader changes.
- Analytics must be checked in light and dark themes whenever ECharts token usage changes.

## Maintenance notes

- Read `DESIGN.md` before changing UI behavior or styles.
- New UI components must be added to `docs/ui-component-review-matrix.md`.
- Components backed by canvas, charts, or shaders must respond to theme changes and rerender their palette.
- Theme selection may preview immediately, but persisted preference must only be written after a successful save.
- Analytics cards, radar, and trend use the latest five completed scored sessions.
- Radar chart must avoid heavy filled background surfaces.
- Dev fixture seed data must be deterministic: exactly five finished sessions and two ongoing sessions for the demo user, with reports and score history only attached to finished sessions.
- Seed SQL must remain reviewable: multi-line statements, no legacy April migration logic, and no generated compressed SQL lines.
