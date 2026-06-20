# UI Component Lab

> Phase 3 of the Phase 2 UI quality system. See also `ui-phase2-baseline.md`, `ui-visual-regression.md`, `ui-a11y.md`.

## What this phase produces

A dev-only playground route at `/components-lab` that lists every shared UI primitive with its key states side-by-side. Designers and engineers use it to:

- Review how a component behaves across variants (e.g. `Button` × `variant` × `size` × `loading` × `disabled`).
- Catch accidental regressions after a token or theme change.
- Provide a stable, single source of truth for the visual regression (Phase 1) and a11y (Phase 2) harnesses to point at.

## Files

| File | Role |
| --- | --- |
| `frontend/src/views/ComponentLabView.vue` | The lab page itself. |
| `frontend/src/components/lab/ComponentLabSection.vue` | Section wrapper used by the lab (heading + description + body slot). |
| `frontend/src/router/index.ts` | Adds the `/components-lab` route only when `import.meta.env.DEV === true`. |
| `frontend/tests/visual/ui-visual.spec.ts` | New `16 components lab (light)` + `17 components lab (dark)` scenarios. |

## Dev-only contract

```ts
...(import.meta.env.DEV
  ? [
      {
        path: '/components-lab',
        name: 'components-lab',
        component: () => import('../views/ComponentLabView.vue'),
        meta: { public: true, devOnly: true },
      },
    ]
  : []),
```

- `import.meta.env.DEV` is `false` at production build time, so Vite/Rolldown tree-shakes the route registration AND the lazy import (`ComponentLabView-*.js` chunk does NOT appear in the prod bundle).
- `meta.public: true` keeps the route accessible in dev without auth — appropriate for an internal QA surface.
- No entry is added to the user-facing sidebar nav, the help menu, or the auth redirect — the route is intentionally hidden in dev too unless someone types the URL.

## Component coverage

| Family | Coverage in `ComponentLabView` |
| --- | --- |
| Button | variant × size × loading × disabled (6 variants × 3 sizes + loading + disabled) |
| Input / Textarea | default / disabled / error-style (border-error hint) |
| Select / DropdownMenu / Combobox | closed / open · long label |
| Dialog | settings-like modal example |
| Tooltip | hover/focus trigger; long-text variant |
| Badge | default / destructive / secondary / outline |
| Card / EmptyState | container + empty state |
| SegmentedControl | 1 / 2 / 3 / long-label items |
| Workspace excerpt | links to `/interview` (sidebar expanded/collapsed achieved in-place) |
| Composer excerpt | empty workspace + jump link to live composer |
| Message bubble | user / assistant / judge feedback (uses Phase 1 semantic tokens) |

## Stability invariants

- Component Lab itself passes `verify:ui` (no business-component raw px / no forbidden utilities). Two specific patterns to be aware of:
  - `ComponentLabSection` uses **slot props** (`<template #heading>` / `<template #description>`) rather than `title=` / `description=` props, because `verify:ui` `forbidden-utility-classes` rule matches the literal substring `title=` and would flag a prop literal as a "native HTML title". Slots avoid that false positive.
  - The lab file's doc comments deliberately avoid the literal substring `title=` / `shadow-md` / `border-border` / `h-[30px]` etc., for the same reason.
- Component Lab passes `verify:build` (TypeScript strict build green).
- The visual regression harness now captures `16-components-lab-light.png` and `17-components-lab-dark.png`.

## How to use

```powershell
npm --prefix frontend run dev
# open http://127.0.0.1:5173/components-lab
```

Capture regression baselines:

```powershell
npm --prefix frontend run capture:visual
```

## Out of scope

- Storybook-style runtime playground (per task constraints — avoid extra deps).
- Editing controls (sliders / knobs) for live token tuning. Current scope is "show, don't edit".
- Production exposure. The route is intentionally absent in production builds.

## Known limitations

- Phase 1 visual regression is artifact-only (no pixel diff). Component Lab makes drift easier to spot but does not by itself enforce stability.
- The page is not optimized for narrow viewports. Mobile QA must use a desktop viewport for now; adding a responsive split view is a follow-up if needed.
