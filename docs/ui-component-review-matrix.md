# UI Component Review Matrix

This matrix is a maintenance checklist for future UI reviews. It is not a backlog or a record of earlier discussions. `DESIGN.md` remains the highest standard.

| Area | Component | Review Focus | Required Standard | Status |
|---|---|---|---|---|
| Login | Page background | color, dark theme | Tokenized paper background, no extra gradient layer | Baseline |
| Login | Auth card | card, spacing, focus | Paper card, token spacing, visible focus states | Baseline |
| Login | BrandMetaballs | logo palette, shader rerender | Dedicated logo tokens, old warm-brown hierarchy, rerender on theme change | Needs visual check |
| Login | Form controls | height, font, autofill | Base 34px, serif UI text, dark input text uses primary text token | Baseline |
| App shell | Layout | viewport, overflow | Fixed app shell, contained scrolling areas | Baseline |
| Sidebar | Brand and collapse | size, aria, focus | Token sizes, icon-only collapsed state, accessible controls | Baseline |
| Sidebar | Session list | truncate, tooltip, state | Job name only, no database id, full text through Tooltip | Baseline |
| Sidebar | Navigation | active, hover, dark | Token colors, no hardcoded borders or shadows | Baseline |
| Workspace header | Title | truncate, tooltip | Long job names use Tooltip, no raw session id | Baseline |
| Workspace header | Status | badge, data source | Business status or stage only | Baseline |
| Workspace header | Actions | hierarchy, sizing | Secondary report action, segmented control uses shared primitive | Baseline |
| Message thread | Bubbles | alignment, font | User right, interviewer left, body sans, labels serif | Baseline |
| Message thread | Plain text | markdown, wrapping | Interview messages render as text with line breaks, not Markdown | Baseline |
| Message thread | Score hint | tooltip, overflow | Score pill readable, long hint uses Tooltip | Baseline |
| Message thread | Streaming state | motion, status | Token motion, no layout animation | Baseline |
| Composer | Container | border, shadow, position | Token paper container, bottom state fixed, centered empty state | Baseline |
| Composer | Textarea | size, font, disabled | Token height and font, disabled state covers ended interview | Baseline |
| Composer | Compact metadata | height, dropdown | Resume, position, model, JD use compact 30px | Baseline |
| Composer | Main actions | height, loading | Send, start, voice toggle, hold-to-talk use base 34px | Baseline |
| Composer | Model dropdown | provider scope, width | Switch current provider model only, content width matches trigger | Baseline |
| Composer | Voice mode | aria, token size | Icon buttons have aria-label, hold button uses token visual rules | Baseline |
| Report | Generating state | loading visual | RoseThree or shared loading visual, no raw hourglass | Baseline |
| Report | Markdown | headings, lists, tables, code | h1-h4, p, ul/ol/li, blockquote, table, code/pre styled by tokens | Baseline |
| Report | Reading body | font, width, scroll | Long report body can use sans, constrained readable width | Baseline |
| Settings | Shell | dialog, navigation | Two-column dialog, profile/theme/LLM tabs | Baseline |
| Settings profile | User info | form, avatar | Editable username/email, avatar local filesystem plus static URL | Baseline |
| Settings theme | Theme picker | scope, persistence | Independent tab, light/dark/system, backend for logged-in users, localStorage fallback | Baseline |
| Settings LLM | Provider wording | copy, select | Use “OpenAI 兼容协议”, provider stays in settings | Baseline |
| Settings LLM | Model field | select, combobox | Built-in uses Select, OpenAI compatible allows typed Combobox | Baseline |
| Settings LLM | BYOK actions | state, toast | Test/save via top notice, no duplicate panel status row | Baseline |
| Resume management | List | filename, tooltip | Long filenames truncate with Tooltip | Baseline |
| Resume management | Delete flow | dialog, async | Shared Promise-based dialog, no native confirm | Baseline |
| Resume management | Empty state | tone, spacing | Quiet EmptyState, token spacing | Baseline |
| Analytics | Score cards | data, typography | Technical, expression, logic only, tokenized card visual | Baseline |
| Analytics | Radar chart | dimensions, tokens | Three dimensions: technical ability, expression clarity, logical thinking | Baseline |
| Analytics | Trend chart | data, theme | Latest five real score points, rerender on theme change | Baseline |
| Analytics | Tooltip and axes | color, dark | ECharts colors resolve from tokens in light and dark themes | Baseline |
| Analytics | Weakness list | hierarchy, data | Category groups with same-level descriptions, no fake summary/detail split | Baseline |
| UI Primitive | Button | sizes, loading | Default/base 34px, compact 30px only where allowed, no large variant in business UI | Baseline |
| UI Primitive | Input/Textarea | height, dark | Base 34px for inputs, token backgrounds, no hardcoded dark colors | Baseline |
| UI Primitive | Select/Dropdown | overlay, item | Low paper overlay, shadow-whisper, transparent or weak border, token item height | Baseline |
| UI Primitive | Combobox | keyboard, overlay | Reka/shadcn behavior, shared overlay visual, no raw suggestion list | Baseline |
| UI Primitive | Tooltip | z-index, style | z-110, bg-surface, shadow-whisper, serif UI text | Baseline |
| UI Primitive | Dialog | overlay, close | Token mask and paper surface, no unstyled native modal behavior | Baseline |
| UI Primitive | Confirm | async control | Promise-based async flow, no synchronous native confirm | Baseline |
| UI Primitive | Badge | status text | Token colors and serif UI text | Baseline |
| UI Primitive | Card | elevation | No strong default shadow; repeated items only where useful | Baseline |
| UI Primitive | Separator | color | Weak token border color only | Baseline |
| UI Primitive | Toast | state, surface | Shared low overlay surface, state-specific token accents | Baseline |
| UI Primitive | SegmentedControl | height, radius | Base height, medium radius, clear inactive area | Baseline |
| UI Primitive | EmptyState | density, copy | Quiet copy, no default extra action button | Baseline |
| Motion | Transitions | duration, properties | Motion tokens, no transition-all, no layout animation | Baseline |
| Theme | App-wide switching | events, storage | Root class/dataset update, `prelude-theme-change` event, persisted preference | Baseline |
| Loading | RoseThree | SVG, currentColor | SVG plus RAF, currentColor, speedMultiplier, no heavy animation dependency | Baseline |
| Documentation | UI maintenance docs | drift, coverage | Update this matrix when new UI surfaces are introduced | Baseline |
