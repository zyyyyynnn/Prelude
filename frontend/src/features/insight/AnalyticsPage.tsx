import { useEffect, useRef } from 'react'
import { useQuery } from '@tanstack/react-query'
import * as echarts from 'echarts/core'
import { LineChart, RadarChart } from 'echarts/charts'
import {
  GridComponent,
  LegendComponent,
  RadarComponent,
  TooltipComponent,
} from 'echarts/components'
import { CanvasRenderer } from 'echarts/renderers'
import { RefreshCw } from 'lucide-react'
import { Button } from '@/shared/ui'
import { fetchRadar, fetchTrend, fetchWeaknesses } from './api'
import type { AnalyticsRadarResponse, AnalyticsTrendPoint } from './types'

echarts.use([
  LineChart,
  RadarChart,
  GridComponent,
  LegendComponent,
  RadarComponent,
  TooltipComponent,
  CanvasRenderer,
])

const TREND_GRID = {
  left: 44,
  right: 18,
  top: 30,
  bottom: 48,
} as const

export function AnalyticsPage() {
  const radar = useQuery({ queryKey: ['analytics-radar'], queryFn: fetchRadar })
  const trend = useQuery({ queryKey: ['analytics-trend'], queryFn: fetchTrend })
  const weaknesses = useQuery({ queryKey: ['analytics-weaknesses'], queryFn: fetchWeaknesses })
  const pending = radar.isPending || trend.isPending || weaknesses.isPending
  const error = radar.error || trend.error || weaknesses.error
  const cards = radar.data
    ? ([
        ['技术能力', radar.data.technical],
        ['表达清晰度', radar.data.expression],
        ['逻辑思维', radar.data.logic],
      ] as const)
    : []
  function reload() {
    void Promise.all([radar.refetch(), trend.refetch(), weaknesses.refetch()])
  }
  return (
    <section className="workspace-page">
      <header className="workspace-header">
        <div className="workspace-header__main">
          <div className="workspace-header__title-area">
            <h1 className="workspace-header__title">数据看板</h1>
          </div>
        </div>
      </header>
      <div className="workspace-page__content scrollable">
        {pending ? (
          <div className="empty-state" aria-live="polite">
            正在整理训练数据…
          </div>
        ) : error ? (
          <div className="empty-state">
            <p>{error.message}</p>
            <Button variant="secondary" onClick={reload}>
              <RefreshCw size={15} />
              重新加载
            </Button>
          </div>
        ) : !radar.data?.sessionCount ? (
          <div className="empty-state">
            <p>完成至少一场面试后，这里会显示能力变化与训练重点。</p>
          </div>
        ) : (
          <>
            <div className="analytics-score-grid">
              {cards.map(([label, value]) => (
                <article className="analytics-score-card" key={label}>
                  <p className="analytics-score-card__label">{label}</p>
                  <strong className="analytics-score-card__value">{value.toFixed(1)}</strong>
                  <p className="analytics-score-card__meta">最近 {radar.data.sessionCount} 场均分</p>
                </article>
              ))}
            </div>
            <div className="analytics-dashboard-grid">
              <section className="analytics-panel">
                <div className="analytics-panel__head">
                  <div>
                    <p className="analytics-panel__eyebrow">结构</p>
                    <h2 className="analytics-panel__title">能力雷达</h2>
                    <p className="analytics-panel__lead">展示最近面试在三项核心维度上的平均水平。</p>
                  </div>
                  <span className="analytics-panel__meta">{radar.data.sessionCount} 场</span>
                </div>
                <Radar data={radar.data} />
              </section>
              <section className="analytics-panel">
                <div className="analytics-panel__head">
                  <div>
                    <p className="analytics-panel__eyebrow">走势</p>
                    <h2 className="analytics-panel__title">分数趋势</h2>
                    <p className="analytics-panel__lead">按时间查看技术、表达与逻辑评分变化。</p>
                  </div>
                </div>
                <Trend data={trend.data ?? []} />
              </section>
            </div>
            <section className="analytics-panel">
              <div className="analytics-panel__head">
                <div>
                  <p className="analytics-panel__eyebrow">聚合</p>
                  <h2 className="analytics-panel__title">薄弱点列表</h2>
                  <p className="analytics-panel__lead">按出现频率汇总薄弱点。</p>
                </div>
                <span className="analytics-panel__meta">{weaknesses.data?.length ?? 0} 类问题</span>
              </div>
              <div className="analytics-weakness-list">
                {weaknesses.data?.length ? (
                  weaknesses.data.map((item) => (
                    <article className="analytics-weakness-item" key={item.category}>
                      <div className="analytics-weakness-item__head">
                        <h3 className="analytics-weakness-item__title">{item.category}</h3>
                        <p className="analytics-weakness-item__summary">出现 {item.count} 次</p>
                      </div>
                      <ul className="analytics-weakness-item__descriptions">
                        {item.descriptions.map((description) => (
                          <li key={description}>{description}</li>
                        ))}
                      </ul>
                    </article>
                  ))
                ) : (
                  <div className="empty-state">暂无已归纳的薄弱点。</div>
                )}
              </div>
            </section>
          </>
        )}
      </div>
    </section>
  )
}

function useChart(createOption: () => echarts.EChartsCoreOption, dependency: unknown) {
  const element = useRef<HTMLDivElement>(null)
  const createOptionRef = useRef(createOption)
  const chartRef = useRef<echarts.ECharts | null>(null)

  useEffect(() => {
    createOptionRef.current = createOption
  }, [createOption])

  useEffect(() => {
    if (!element.current) return
    const chart = echarts.init(element.current)
    chartRef.current = chart
    const render = () => {
      chart.setOption(createOptionRef.current(), true)
      chart.resize()
    }
    render()
    const observer = new ResizeObserver(() => chart.resize())
    observer.observe(element.current)
    window.addEventListener('prelude-theme-change', render)
    return () => {
      window.removeEventListener('prelude-theme-change', render)
      observer.disconnect()
      chart.dispose()
      chartRef.current = null
    }
  }, [])

  useEffect(() => {
    chartRef.current?.setOption(createOptionRef.current(), true)
  }, [dependency])
  return element
}

function Radar({ data }: { data: AnalyticsRadarResponse }) {
  const ref = useChart(
    () => {
      const brand = cssVar('--chart-technical', 'var(--color-brand)')
      const secondary = cssVar('--color-text-secondary', 'var(--color-text-secondary)')
      const border = cssVar('--color-border-warm', 'var(--color-border)')
      const ring = cssVar('--color-ring', 'var(--color-border)')
      const serif = cssToken('--font-serif', 'serif')
      return {
        animation: false,
        radar: {
          radius: '64%',
          splitNumber: 5,
          indicator: [
            { name: '技术能力', max: 10 },
            { name: '表达清晰度', max: 10 },
            { name: '逻辑思维', max: 10 },
          ],
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
                areaStyle: { color: brand, opacity: 0.16 },
                lineStyle: { color: brand, width: 2 },
                itemStyle: { color: brand },
              },
            ],
          },
        ],
      }
    },
    data,
  )
  return (
    <div
      className="analytics-chart"
      ref={ref}
      role="img"
      aria-label={`技术能力 ${data.technical}，表达清晰度 ${data.expression}，逻辑思维 ${data.logic}`}
    />
  )
}

function Trend({ data }: { data: AnalyticsTrendPoint[] }) {
  const ref = useChart(
    () => {
      const technical = cssVar('--chart-technical', 'var(--color-brand)')
      const expression = cssVar('--chart-expression', 'var(--color-coral)')
      const logic = cssVar('--chart-logic', 'var(--color-ring-deep)')
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
          className: 'ui-chart-tooltip',
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
        series: [
          {
            name: '技术能力',
            type: 'line',
            smooth: true,
            data: data.map((item) => item.technical),
            lineStyle: { color: technical, width: 2 },
            itemStyle: { color: technical },
          },
          {
            name: '表达清晰度',
            type: 'line',
            smooth: true,
            data: data.map((item) => item.expression),
            lineStyle: { color: expression, width: 2 },
            itemStyle: { color: expression },
          },
          {
            name: '逻辑思维',
            type: 'line',
            smooth: true,
            data: data.map((item) => item.logic),
            lineStyle: { color: logic, width: 2 },
            itemStyle: { color: logic },
          },
        ],
      }
    },
    data,
  )
  return (
    <div
      className="analytics-chart"
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

function formatDate(dateString: string, format: 'MM/DD' | 'YYYY/MM/DD') {
  const date = new Date(dateString)
  const yyyy = date.getFullYear()
  const mm = String(date.getMonth() + 1).padStart(2, '0')
  const dd = String(date.getDate()).padStart(2, '0')
  return format === 'MM/DD' ? `${mm}/${dd}` : `${yyyy}/${mm}/${dd}`
}

function normalizeChartColor(value: string) {
  const srgbMatch = value.match(/^color\(srgb\s+([0-9.]+)\s+([0-9.]+)\s+([0-9.]+)/)
  if (!srgbMatch) return value
  const [, r, g, b] = srgbMatch
  return `rgb${'('}${Math.round(Number(r) * 255)}, ${Math.round(Number(g) * 255)}, ${Math.round(Number(b) * 255)})`
}

function resolveChartColor(value: string) {
  const probe = document.createElement('span')
  probe.style.color = value
  document.body.appendChild(probe)
  const computed = getComputedStyle(probe).color
  probe.remove()
  return normalizeChartColor(computed || value)
}

function cssVar(name: string, fallback: string) {
  const value = getComputedStyle(document.documentElement).getPropertyValue(name).trim()
  return resolveChartColor(value || fallback)
}

function cssToken(name: string, fallback: string) {
  return getComputedStyle(document.documentElement).getPropertyValue(name).trim() || fallback
}

function cssVarNumber(name: string, fallback: number) {
  const value = getComputedStyle(document.documentElement).getPropertyValue(name).trim()
  const parsed = Number.parseFloat(value)
  return Number.isFinite(parsed) ? parsed : fallback
}

function cssDeclarations(declarations: Record<string, string>) {
  return Object.entries(declarations)
    .map(([property, value]) => `${property}: ${value}`)
    .join('; ')
}
