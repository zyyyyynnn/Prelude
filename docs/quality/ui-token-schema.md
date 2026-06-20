# UI Token Schema

> Phase 4 of the Phase 2 UI quality system. See also `ui-phase2-baseline.md`, `ui-visual-regression.md`, `ui-a11y.md`, `ui-component-lab.md`.

## What this phase produces

A read-only token schema and a verification harness:

- `frontend/tokens/ui-tokens.json` — declares every `--xxx` token the project owns, grouped by category with optional per-category rules.
- `frontend/scripts/verify-ui-tokens.cjs` — Node-built-in script that:
  1. Verifies every schema token is declared in `frontend/src/styles/index.css`.
  2. Verifies every `--shadow-*` declaration lives inside a token-definition block (`:root`, `:root.dark`, `.dark`, or `@theme`).
  3. Verifies every `--z-index-*` value is unique across the layer tokens.
  4. Verifies the design-locked values (Sidebar 260 / 51 / 800 / dialog 960 / 500 / height 34 / 30) match `design_lock_values` in the schema.
  5. Verifies every `box-shadow:` selector uses `var(--shadow-*)` (allowing `none` / `none !important` opt-outs).
  6. Reports uncategorized CSS tokens as `note` (informational, not a fail).
- `frontend/package.json` — new script `verify:tokens`.

## Why a schema (not a generator)

Per task constraints we explicitly do **not** introduce Style Dictionary or any token compiler. The current `index.css` is hand-written by the team and reflects `DESIGN.md` values; replacing it with a generator would be a much larger refactor than Phase 4 needs. The schema instead acts as a **read-only index** that lets the CI pipeline detect drift, missing declarations, or category reclassification without diffing the CSS by hand.

## Schema structure

```jsonc
{
  "$schema": "ui-tokens/v1",
  "categories": {
    "color": {
      "prefix": "color",
      "description": "品牌与语义颜色（含暗色模式映射）。",
      "tokens": ["color-bg", "color-surface", ...]
    },
    "shadow": {
      "prefix": "shadow",
      "rules": { "raw-only-in-token-definitions": true },
      "tokens": [...]
    },
    "z-index": {
      "prefix": "z-index",
      "rules": { "values-must-be-unique": true },
      "tokens": [...]
    },
    "ui-height": {
      "prefix": "ui-height",
      "rules": { "design-locked": ["ui-height-base", "ui-height-compact"] },
      "tokens": [...]
    }
    // ...
  },
  "design_lock_values": {
    "ui-height-base": "34px",
    "ui-height-compact": "30px",
    "layout-sidebar-inline-size": "260px",
    "layout-sidebar-collapsed-inline-size": "51px",
    "layout-workspace-content-max-inline-size": "800px",
    "layout-settings-dialog-max-inline-size": "960px",
    "layout-settings-dialog-min-block-size": "500px"
  }
}
```

## Categories

| Category | Prefix | Notes |
| --- | --- | --- |
| `color` | `--color-*` | Brand + semantic colors (light + dark). |
| `spacing` | `--spacing-*` | 4 / 8 / 16 grid plus irregular values (`1-5`, `2-5`, etc.). |
| `radius` | `--radius-*` | Border radius scale. |
| `shadow` | `--shadow-*` | Raw values allowed ONLY in token-definition blocks. |
| `motion` | `--motion-*` | Duration / easing / transition composites. |
| `font` | `--font-*` | Family + size scale. |
| `ui-height` | `--ui-height-*` | Locked at base 34 / compact 30 (DESIGN.md). |
| `layout` | `--layout-*` | Cross-page layout semantics. Locked subset for Sidebar / dialog / workspace. |
| `content` | `--content-*` | Readable content widths (message bubble, judge feedback). |
| `z-index` | `--z-index-*` | Layer tokens with enforced unique values. |
| `header-composer` | `--header-*` / `--composer-*` | Layout helpers (header / composer height). |
| `chart-brand` | `--chart-*` / `--brand-*` / `--rose-*` | Chart + brand pattern colors (kept independent from `--color-*`). |
| `shadcn-tailwind-theme` | (none) | Tailwind v4 + shadcn-vue defaults inside `@theme { ... }`. |
| `legacy-utility` | (none) | Older tokens retained for backward compat; track for future consolidation. |
| `scoped-component-private` | (none) | Component-scoped private variables declared in `<style scoped>` blocks. Not indexed centrally. |

## Rules

| Rule key | Behavior |
| --- | --- |
| `raw-only-in-token-definitions` | Shadow raw values must live inside `:root` / `:root.dark` / `.dark` / `@theme`. Selector-level shadow values must use `var(--shadow-*)`. |
| `values-must-be-unique` | Every `--z-index-*` token must have a distinct numeric value. |
| `design-locked` | Listed tokens must match the values in the schema's top-level `design_lock_values` block (Sidebar 260 / 51 / 800 / dialog 960 / 500 / height 34 / 30). |

## How to run

```powershell
npm --prefix frontend run verify:tokens
```

Exit codes:
- `0` — schema clean.
- `1` — at least one violation (missing token, raw shadow on selector, duplicate z-index value, design-lock mismatch).

## Adding a new token

1. Add the declaration to `frontend/src/styles/index.css` (inside the appropriate block: `:root` for light, `.dark` / `:root.dark` for dark, `@theme` for Tailwind v4 / shadcn-vue defaults).
2. Add the token name (without `--`) to the relevant `tokens` array in `frontend/tokens/ui-tokens.json`.
3. If the token is `design-locked`, also add the expected value to `design_lock_values`.
4. Run `npm run verify:tokens` to confirm.

## Migration path (future)

If the team later decides to introduce Style Dictionary (out of scope for Phase 4), this schema is the natural starting point: each category maps cleanly to a Style Dictionary transform group, and the `design_lock_values` block is already in a token-compatible JSON shape. The migration would be additive (generate additional CSS from JSON) without breaking the existing hand-written `index.css`.

## Out of scope

- Generating CSS from the schema. Not done; read-only validation only.
- Replacing the hand-written `index.css` with a generator.
- Migrating tokens to a third-party design platform.
- Auto-applying `--shadow-raw` fixes (the script reports them; humans fix them).
- Color contrast tuning (tracked separately in `ui-a11y.md`).
