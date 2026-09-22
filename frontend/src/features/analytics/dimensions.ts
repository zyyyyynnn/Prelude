import type { AnalyticsRadarResponse, AnalyticsTrendPoint } from './types'

/** The three scored dimensions, in the order every surface presents them. */
export const DIMENSIONS = [
  { key: 'technical', label: '技术能力' },
  { key: 'expression', label: '表达清晰度' },
  { key: 'logic', label: '逻辑思维' },
] as const

export type DimensionKey = (typeof DIMENSIONS)[number]['key']

/** Every dimension is scored on the same scale, so the radar states it once. */
export const DIMENSION_SCALE = 10

/** The radar's average for one dimension. */
export function radarValue(radar: AnalyticsRadarResponse, key: DimensionKey): number {
  return radar[key]
}

/** One interview's score for one dimension. */
export function trendValue(point: AnalyticsTrendPoint, key: DimensionKey): number {
  return point[key]
}
