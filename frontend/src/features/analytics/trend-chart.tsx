import * as echarts from 'echarts/core'
import { LineChart } from 'echarts/charts'
import { GridComponent, LegendComponent, TooltipComponent } from 'echarts/components'
import { CanvasRenderer } from 'echarts/renderers'
import { cssDeclarations, cssToken, cssVar, cssVarNumber, formatDate } from './chart-tokens'
import { TREND_GRID } from './chart-geometry'
import { DIMENSIONS, trendValue } from './dimensions'
import { useChart } from './use-chart'
import type { DimensionKey } from './dimensions'
import type { AnalyticsTrendPoint } from './types'

echarts.use([LineChart, GridComponent, LegendComponent, TooltipComponent, CanvasRenderer])

/**
 * The score trend: one line per dimension, each reading its own chart token so the trend
 * and the radar stay in step when a token changes.
 */
export function Trend({ data }: { data: AnalyticsTrendPoint[] }) {
  const ref = useChart(() => {
    const dimensionColors: Record<DimensionKey, string> = {
      technical: cssVar('--chart-technical', 'var(--color-brand)'),
      expression: cssVar('--chart-expression', 'var(--color-coral)'),
      logic: cssVar('--chart-logic', 'var(--color-ring-deep)'),
    }
    const secondary = cssVar('--color-text-secondary', 'var(--color-text-secondary)')
    const tertiary = cssVar('--color-text-tertiary', 'var(--color-text-tertiary)')
    const border = cssVar('--color-border-warm', 'var(--color-border)')
    const ring = cssVar('--color-ring', 'var(--color-border)')
    const surface = cssVar('--color-surface', 'var(--color-bg)')
    const input = cssVar('--color-input', 'var(--color-border)')
    const text = cssVar('--color-text-primary', 'var(--color-text-primary)')
    const serif = cssToken('--font-serif', 'serif')
    const sans = cssToken('--font-sans', 'sans-serif')
    return {
      animation: false,
      tooltip: {
        trigger: 'axis',
        backgroundColor: surface,
        borderColor: input,
        borderWidth: 1,
        padding: [cssVarNumber('--spacing-xs', 4), cssVarNumber('--spacing-sm', 8)],
        textStyle: {
          color: text,
          fontFamily: sans,
          fontSize: cssVarNumber('--font-size-sm', 14),
        },
        extraCssText: cssDeclarations({
          'border-radius': 'var(--radius-md)',
          'box-shadow': 'var(--shadow-whisper)',
        }),
        formatter: (params: unknown) => formatTrendTooltip(params, data),
      },
      legend: {
        bottom: cssVarNumber('--spacing-xs', 4),
        textStyle: {
          color: secondary,
          fontFamily: serif,
          fontSize: cssVarNumber('--font-size-xs', 13),
          fontWeight: 500,
        },
      },
      grid: {
        ...TREND_GRID,
      },
      xAxis: {
        type: 'category',
        boundaryGap: false,
        data: data.map((item) => formatDate(item.createdAt, 'MM/DD')),
        axisLine: { lineStyle: { color: ring } },
        axisLabel: {
          color: tertiary,
          fontFamily: sans,
          fontSize: cssVarNumber('--font-size-xs', 13),
        },
      },
      yAxis: {
        type: 'value',
        min: 0,
        max: 10,
        axisLine: { lineStyle: { color: ring } },
        axisLabel: {
          color: tertiary,
          fontFamily: sans,
          fontSize: cssVarNumber('--font-size-xs', 13),
        },
        splitLine: { lineStyle: { color: border } },
      },
      series: DIMENSIONS.map((dimension) => {
        const color = dimensionColors[dimension.key]
        return {
          name: dimension.label,
          type: 'line',
          smooth: true,
          data: data.map((item) => trendValue(item, dimension.key)),
          lineStyle: { color, width: 2 },
          itemStyle: { color },
        }
      }),
    }
  }, data)
  return (
    <div
      className="h-(--layout-chart-block-size) min-h-(--layout-chart-block-size)"
      ref={ref}
      role="img"
      aria-label={`最近 ${data.length} 场面试的分数趋势`}
    />
  )
}

function formatTrendTooltip(params: unknown, data: AnalyticsTrendPoint[]) {
  const entries: unknown[] = Array.isArray(params) ? (params as unknown[]) : [params]
  const first = entries[0]
  if (!first || typeof first !== 'object' || !('dataIndex' in first)) return ''
  const dataIndex = Number((first as { dataIndex?: unknown }).dataIndex)
  const point = data[dataIndex]
  if (!point) return ''
  const lines = [`<div>${formatDate(point.createdAt, 'YYYY/MM/DD')}</div>`]
  for (const entry of entries) {
    if (!entry || typeof entry !== 'object') continue
    const item = entry as { marker?: string; seriesName?: string; value?: unknown }
    let value = ''
    if (typeof item.value === 'string') value = item.value
    else if (typeof item.value === 'number' || typeof item.value === 'boolean') {
      value = item.value.toString()
    } else if (item.value && typeof item.value === 'object') {
      value = JSON.stringify(item.value)
    }
    lines.push(`<div>${item.marker ?? ''} ${item.seriesName ?? ''}: ${value}</div>`)
  }
  return lines.join('')
}
