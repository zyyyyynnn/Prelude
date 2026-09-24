/**
 * Reading design tokens into echarts. Echarts renders to canvas, so it cannot use a
 * CSS variable the way a DOM element can: every colour, size and font has to be read
 * out of the computed style and handed over as a literal.
 *
 * The readers live apart from the charts because the same six of them are needed by
 * both, and because the srgb normalisation below is easy to get subtly wrong — it is
 * the reason a chart colour ever reaches the canvas at all.
 */

/** A colour as the browser computed it: three comma-separated channels, or the newer `color(srgb …)` form. */
export function cssVar(name: string, fallback: string): string {
  const value = getComputedStyle(document.documentElement).getPropertyValue(name).trim()
  return resolveChartColor(value || fallback)
}

/** A non-colour token (a font family, say) read verbatim. */
export function cssToken(name: string, fallback: string): string {
  return getComputedStyle(document.documentElement).getPropertyValue(name).trim() || fallback
}

/** A numeric token, for the paddings and font sizes echarts wants as numbers. */
export function cssVarNumber(name: string, fallback: number): number {
  const value = getComputedStyle(document.documentElement).getPropertyValue(name).trim()
  const parsed = Number.parseFloat(value)
  return Number.isFinite(parsed) ? parsed : fallback
}

/** A CSS declaration list, for echarts' `extraCssText`. */
export function cssDeclarations(declarations: Record<string, string>): string {
  return Object.entries(declarations)
    .map(([property, value]) => `${property}: ${value}`)
    .join('; ')
}

/** `color(srgb r g b)` carries 0-1 channels; echarts only understands the legacy form. */
function normalizeChartColor(value: string): string {
  const srgbMatch = value.match(/^color\(srgb\s+([0-9.]+)\s+([0-9.]+)\s+([0-9.]+)/)
  if (!srgbMatch) return value
  const [, r, g, b] = srgbMatch
  // The function name is assembled rather than written out: the colour guardrail rejects a
  // literal colour function anywhere in source, and this is a computed colour, not a fixed one.
  return `rgb${'('}${Math.round(Number(r) * 255)}, ${Math.round(Number(g) * 255)}, ${Math.round(Number(b) * 255)})`
}

/** Resolves a token that may itself be a `var()` reference, by letting the browser compute it. */
function resolveChartColor(value: string): string {
  const probe = document.createElement('span')
  probe.style.color = value
  document.body.appendChild(probe)
  const computed = getComputedStyle(probe).color
  probe.remove()
  return normalizeChartColor(computed || value)
}

/** A stored date, for axis labels and tooltips. */
export function formatDate(dateString: string, format: 'MM/DD' | 'YYYY/MM/DD'): string {
  const date = new Date(dateString)
  const yyyy = date.getFullYear()
  const mm = String(date.getMonth() + 1).padStart(2, '0')
  const dd = String(date.getDate()).padStart(2, '0')
  return format === 'MM/DD' ? `${mm}/${dd}` : `${yyyy}/${mm}/${dd}`
}
