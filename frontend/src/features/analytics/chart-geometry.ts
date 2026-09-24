/**
 * Chart-internal geometry, sized for the chart's own labels and frame — not for the
 * interface spacing scale. Echarts reads these as pixels; converting them to
 * `--spacing-*` would dress an unrelated number in a token's clothes.
 */
export const TREND_GRID = {
  left: 44,
  right: 18,
  top: 30,
  bottom: 48,
} as const

export const RADAR_GEOMETRY = {
  radius: '64%',
  splitNumber: 5,
  areaOpacity: 0.16,
  lineWidth: 2,
} as const
