import { useEffect, useRef } from 'react'
import { useQuery } from '@tanstack/react-query'
import * as echarts from 'echarts/core'
import { LineChart, RadarChart } from 'echarts/charts'
import { GridComponent, LegendComponent, TooltipComponent } from 'echarts/components'
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
  TooltipComponent,
  CanvasRenderer,
])

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
            <div className="insight-strip insight-strip--compact">
              {cards.map(([label, value]) => (
                <article className="insight-card" key={label}>
                  <p className="panel__eyebrow">{label}</p>
                  <h2 className="insight-card__value">{value.toFixed(1)}</h2>
                  <p className="insight-card__meta">最近面试均分</p>
                </article>
              ))}
            </div>
            <div className="page-grid page-grid--dashboard">
              <section className="panel">
                <div className="panel__head">
                  <div>
                    <h2 className="panel__title">能力雷达</h2>
                    <p className="panel__lead">三项核心面试能力的当前水平</p>
                  </div>
                </div>
                <Radar data={radar.data} />
              </section>
              <section className="panel">
                <div className="panel__head">
                  <div>
                    <h2 className="panel__title">分数趋势</h2>
                    <p className="panel__lead">按面试时间观察训练变化</p>
                  </div>
                </div>
                <Trend data={trend.data ?? []} />
              </section>
            </div>
            <section className="panel">
              <div className="panel__head">
                <div>
                  <h2 className="panel__title">薄弱点</h2>
                  <p className="panel__lead">从历史复盘中聚合的高频改进项</p>
                </div>
              </div>
              <div className="weakness-list">
                {weaknesses.data?.length ? (
                  weaknesses.data.map((item) => (
                    <article className="weakness-item" key={item.category}>
                      <div className="weakness-item__head">
                        <div>
                          <h3 className="weakness-item__title">{item.category}</h3>
                          <p className="weakness-item__summary">出现 {item.count} 次</p>
                        </div>
                      </div>
                      <ul className="weakness-item__descriptions">
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

function useChart(option: echarts.EChartsCoreOption) {
  const element = useRef<HTMLDivElement>(null)
  useEffect(() => {
    if (!element.current) return
    const chart = echarts.init(element.current)
    chart.setOption(option)
    const observer = new ResizeObserver(() => chart.resize())
    observer.observe(element.current)
    return () => {
      observer.disconnect()
      chart.dispose()
    }
  }, [option])
  return element
}

function Radar({ data }: { data: AnalyticsRadarResponse }) {
  const color = token('--color-brand')
  const ref = useChart({
    tooltip: {},
    radar: {
      indicator: [
        { name: '技术能力', max: 10 },
        { name: '表达清晰度', max: 10 },
        { name: '逻辑思维', max: 10 },
      ],
      splitArea: { areaStyle: { color: ['transparent'] } },
      axisName: { color: token('--color-text-secondary') },
      splitLine: { lineStyle: { color: token('--color-border') } },
      axisLine: { lineStyle: { color: token('--color-border') } },
    },
    series: [
      {
        type: 'radar',
        data: [{ value: [data.technical, data.expression, data.logic] }],
        areaStyle: { color, opacity: 0.16 },
        lineStyle: { color },
        itemStyle: { color },
      },
    ],
  })
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
  const ref = useChart({
    tooltip: { trigger: 'axis' },
    legend: { bottom: 0, textStyle: { color: token('--color-text-secondary') } },
    grid: { left: 40, right: 18, top: 24, bottom: 48 },
    xAxis: {
      type: 'category',
      data: data.map((item) =>
        new Intl.DateTimeFormat('zh-CN', { month: '2-digit', day: '2-digit' }).format(
          new Date(item.createdAt),
        ),
      ),
      axisLabel: { color: token('--color-text-tertiary') },
    },
    yAxis: {
      type: 'value',
      min: 0,
      max: 10,
      axisLabel: { color: token('--color-text-tertiary') },
      splitLine: { lineStyle: { color: token('--color-border') } },
    },
    series: [
      { name: '技术', type: 'line', smooth: true, data: data.map((item) => item.technical) },
      { name: '表达', type: 'line', smooth: true, data: data.map((item) => item.expression) },
      { name: '逻辑', type: 'line', smooth: true, data: data.map((item) => item.logic) },
    ],
  })
  return (
    <div
      className="analytics-chart"
      ref={ref}
      role="img"
      aria-label={`最近 ${data.length} 场面试的分数趋势`}
    />
  )
}

function token(name: string) {
  return getComputedStyle(document.documentElement).getPropertyValue(name).trim()
}
