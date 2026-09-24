import * as echarts from 'echarts/core'
import { RadarChart } from 'echarts/charts'
import { RadarComponent, TooltipComponent } from 'echarts/components'
import { CanvasRenderer } from 'echarts/renderers'
import { cssToken, cssVar, cssVarNumber } from './chart-tokens'
import { RADAR_GEOMETRY } from './chart-geometry'
import { DIMENSIONS, DIMENSION_SCALE, radarValue } from './dimensions'
import { useChart } from './use-chart'
import type { AnalyticsRadarResponse } from './types'

echarts.use([RadarChart, RadarComponent, TooltipComponent, CanvasRenderer])

/**
 * The capability radar. Its option is rebuilt from the design tokens on every render,
 * because a canvas cannot read a CSS variable the way an element can.
 */
export function Radar({ data }: { data: AnalyticsRadarResponse }) {
  const ref = useChart(() => {
    const brand = cssVar('--chart-technical', 'var(--color-brand)')
    const secondary = cssVar('--color-text-secondary', 'var(--color-text-secondary)')
    const border = cssVar('--color-border-warm', 'var(--color-border)')
    const ring = cssVar('--color-ring', 'var(--color-border)')
    const serif = cssToken('--font-serif', 'serif')
    return {
      animation: false,
      radar: {
        radius: RADAR_GEOMETRY.radius,
        splitNumber: RADAR_GEOMETRY.splitNumber,
        indicator: DIMENSIONS.map((dimension) => ({
          name: dimension.label,
          max: DIMENSION_SCALE,
        })),
        splitArea: { show: false },
        axisName: {
          color: secondary,
          fontFamily: serif,
          fontSize: cssVarNumber('--font-size-sm', 14),
          fontWeight: 500,
        },
        splitLine: { lineStyle: { color: border } },
        axisLine: { lineStyle: { color: ring } },
      },
      series: [
        {
          type: 'radar',
          data: [
            {
              value: [data.technical, data.expression, data.logic],
              areaStyle: { color: brand, opacity: RADAR_GEOMETRY.areaOpacity },
              lineStyle: { color: brand, width: RADAR_GEOMETRY.lineWidth },
              itemStyle: { color: brand },
            },
          ],
        },
      ],
    }
  }, data)
  return (
    <div
      className="h-(--layout-chart-block-size) min-h-(--layout-chart-block-size)"
      ref={ref}
      role="img"
      aria-label={DIMENSIONS.map(
        (dimension) => `${dimension.label} ${radarValue(data, dimension.key).toFixed(1)}`,
      ).join('，')}
    />
  )
}
