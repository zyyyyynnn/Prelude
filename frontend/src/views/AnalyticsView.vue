<script setup lang="ts">
import { LineChart, RadarChart } from 'echarts/charts'
import { GridComponent, RadarComponent, TooltipComponent } from 'echarts/components'
import { init, use, type ECharts } from 'echarts/core'
import { CanvasRenderer } from 'echarts/renderers'
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { fetchRadarAnalytics, fetchTrendAnalytics, fetchWeaknessAnalytics } from '../api/analytics'
import { getErrorMessage } from '../utils/errors'
import type { AnalyticsRadarResponse, AnalyticsTrendPoint, AnalyticsWeaknessItem } from '../api/contracts'
import { usePageNotice } from '../composables/usePageNotice'
import { Card } from '@/components/ui/card'
import { Badge } from '@/components/ui/badge'
import EmptyState from '@/components/ui/empty-state/EmptyState.vue'

const { showNotice } = usePageNotice()

use([LineChart, RadarChart, GridComponent, RadarComponent, TooltipComponent, CanvasRenderer])

const radar = ref<AnalyticsRadarResponse | null>(null)
const trend = ref<AnalyticsTrendPoint[]>([])
const weaknesses = ref<AnalyticsWeaknessItem[]>([])
const radarRef = ref<HTMLDivElement | null>(null)
const trendRef = ref<HTMLDivElement | null>(null)
let radarChart: ECharts | null = null
let trendChart: ECharts | null = null

const scoreCards = computed(() => {
  if (!radar.value || radar.value.sessionCount === 0) {
    return []
  }

  return [
    { label: '技术能力', value: radar.value.technical.toFixed(1), hint: '最近 10 场均分' },
    { label: '表达清晰度', value: radar.value.expression.toFixed(1), hint: '结构、表达和节奏' },
    { label: '逻辑思维', value: radar.value.logic.toFixed(1), hint: '问题拆解与推演' },
  ]
})

function cssVar(name: string, fallback: string) {
  const value = getComputedStyle(document.documentElement).getPropertyValue(name).trim()
  return value || fallback
}

function hexToRgba(color: string, alpha: number) {
  const normalized = color.replace('#', '')
  if (!/^[\da-fA-F]{6}$/.test(normalized)) {
    return color
  }
  const red = Number.parseInt(normalized.slice(0, 2), 16)
  const green = Number.parseInt(normalized.slice(2, 4), 16)
  const blue = Number.parseInt(normalized.slice(4, 6), 16)
  return `rgba(${red}, ${green}, ${blue}, ${alpha})`
}

function formatDate(dateString: string, format: 'MM/DD' | 'YYYY/MM/DD') {
  const d = new Date(dateString)
  const yyyy = d.getFullYear()
  const mm = String(d.getMonth() + 1).padStart(2, '0')
  const dd = String(d.getDate()).padStart(2, '0')
  if (format === 'MM/DD') return `${mm}/${dd}`
  return `${yyyy}/${mm}/${dd}`
}



async function loadAnalytics() {
  try {
    const [radarData, trendData, weaknessData] = await Promise.all([
      fetchRadarAnalytics(),
      fetchTrendAnalytics(),
      fetchWeaknessAnalytics(),
    ])
    radar.value = radarData
    trend.value = trendData
    weaknesses.value = weaknessData
    await nextTick()
    renderCharts()
  } catch (error) {
    showNotice(getErrorMessage(error), 'error')
  }
}

function renderCharts() {
  const brand = cssVar('--color-brand', '#9e7b6a')
  const coral = cssVar('--color-coral', '#b08878')
  const secondary = cssVar('--color-text-secondary', '#5e5d59')
  const lineDecor = cssVar('--color-border-warm', '#e8e6dc')
  const ring = cssVar('--color-ring', '#d1cfc5')

  if (radar.value && radarRef.value && radar.value.sessionCount > 0) {
    radarChart ??= init(radarRef.value)
    radarChart.setOption({
      animation: false,
      radar: {
        radius: '64%',
        splitNumber: 5,
        axisName: { color: secondary, fontSize: 14 },
        splitLine: { lineStyle: { color: lineDecor } },
        splitArea: { areaStyle: { color: ['rgba(250,249,245,0)', hexToRgba(lineDecor, 0.12)] } },
        axisLine: { lineStyle: { color: ring } },
        indicator: [
          { name: '技术能力', max: 10 },
          { name: '表达清晰度', max: 10 },
          { name: '逻辑思维', max: 10 },
        ],
      },
      series: [{
        type: 'radar',
        data: [{
          value: [radar.value.technical, radar.value.expression, radar.value.logic],
          areaStyle: { color: hexToRgba(brand, 0.16) },
          lineStyle: { color: brand, width: 2 },
          itemStyle: { color: brand },
        }],
      }],
    })
  }

  if (trendRef.value && trend.value.length > 0) {
    trendChart ??= init(trendRef.value)
    trendChart.setOption({
      animation: false,
      tooltip: {
        trigger: 'axis',
        formatter: (params: any) => {
          if (!params || !params.length) return ''
          const dataIndex = params[0].dataIndex
          const originalDate = trend.value[dataIndex].createdAt
          let html = `<div>${formatDate(originalDate, 'YYYY/MM/DD')}</div>`
          for (const param of params) {
            html += `<div>${param.marker} ${param.seriesName}: <span style="float:right;margin-left:20px;font-weight:900">${param.value}</span></div>`
          }
          return html
        }
      },
      grid: { left: 44, right: 18, top: 30, bottom: 30 },
      xAxis: {
        type: 'category',
        boundaryGap: false,
        data: trend.value.map((item) => formatDate(item.createdAt, 'MM/DD')),
        axisLine: { lineStyle: { color: ring } },
        axisLabel: { color: secondary },
      },
      yAxis: {
        type: 'value',
        min: 0,
        max: 10,
        splitLine: { lineStyle: { color: lineDecor } },
        axisLabel: { color: secondary },
      },
      series: [
        {
          name: '技术能力',
          type: 'line',
          smooth: true,
          data: trend.value.map((item) => item.technical),
          lineStyle: { color: brand, width: 2 },
          itemStyle: { color: brand },
        },
        {
          name: '表达清晰度',
          type: 'line',
          smooth: true,
          data: trend.value.map((item) => item.expression),
          lineStyle: { color: coral, width: 2 },
          itemStyle: { color: coral },
        },
        {
          name: '逻辑思维',
          type: 'line',
          smooth: true,
          data: trend.value.map((item) => item.logic),
          lineStyle: { color: secondary, width: 2 },
          itemStyle: { color: secondary },
        },
      ],
    })
  }
}

function handleResize() {
  radarChart?.resize()
  trendChart?.resize()
}

watch([radar, trend], async () => {
  await nextTick()
  renderCharts()
})

onMounted(() => {
  window.addEventListener('resize', handleResize)
  void loadAnalytics()
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', handleResize)
  radarChart?.dispose()
  trendChart?.dispose()
})
</script>

<template>
  <section class="workspace-page">
    <header class="workspace-header">
      <div class="workspace-header__main">
        <div class="workspace-header__title-area">
          <h2 class="workspace-header__title">数据看板</h2>
        </div>
      </div>
    </header>

    <div class="workspace-page__content scrollable flex flex-col gap-6">
      <template v-if="radar && radar.sessionCount > 0">
        <div class="insight-strip insight-strip--compact">
          <article v-for="item in scoreCards" :key="item.label" class="insight-card">
            <p class="panel__eyebrow">{{ item.label }}</p>
            <h3 class="insight-card__value">{{ item.value }}</h3>
            <p class="insight-card__meta">{{ item.hint }}</p>
          </article>
        </div>

        <div class="page-grid page-grid--dashboard">
          <Card class="flex flex-col border-none shadow-none bg-surface p-5 rounded-xl">
            <div class="flex justify-between items-start mb-4">
              <div>
                <p class="text-xs text-muted-foreground uppercase tracking-wider mb-1">结构</p>
                <h3 class="text-lg font-medium font-serif">能力雷达</h3>
                <p class="text-sm text-muted-foreground">展示最近面试在三项核心维度上的平均水平。</p>
              </div>
              <Badge variant="secondary">{{ radar.sessionCount }} 场</Badge>
            </div>
            <div ref="radarRef" class="chart-surface chart-surface--tall h-64" />
          </Card>

          <Card class="flex flex-col border-none shadow-none bg-surface p-5 rounded-xl">
            <div class="flex justify-between items-start mb-4">
              <div>
                <p class="text-xs text-muted-foreground uppercase tracking-wider mb-1">走势</p>
                <h3 class="text-lg font-medium font-serif">分数趋势</h3>
                <p class="text-sm text-muted-foreground">按时间查看技术、表达与逻辑评分变化。</p>
              </div>
            </div>
            <div ref="trendRef" class="chart-surface chart-surface--tall h-64" />
          </Card>
        </div>

        <div class="page-grid page-grid--single">
          <Card class="flex flex-col border-none shadow-none bg-surface p-5 rounded-xl">
            <div class="flex justify-between items-start mb-4">
              <div>
                <p class="text-xs text-muted-foreground uppercase tracking-wider mb-1">聚合</p>
                <h3 class="text-lg font-medium font-serif">薄弱点列表</h3>
                <p class="text-sm text-muted-foreground">按出现频率汇总薄弱点。</p>
              </div>
              <Badge variant="secondary">{{ weaknesses.length }} 类问题</Badge>
            </div>

            <div v-if="weaknesses.length" class="weakness-list">
              <article v-for="item in weaknesses" :key="item.category" class="weakness-item">
                <div class="weakness-item__head">
                  <div>
                    <h4 class="weakness-item__title">{{ item.category }}</h4>
                  </div>
                  <Badge variant="secondary">{{ item.count }} 次</Badge>
                </div>
                <ul class="weakness-item__descriptions">
                  <li v-for="description in item.descriptions" :key="description">{{ description }}</li>
                </ul>
              </article>
            </div>
            <EmptyState v-else description="还没有可聚合的薄弱点。" />
          </Card>
        </div>
      </template>

      <Card v-else class="flex flex-col border-none shadow-none bg-surface p-5 rounded-xl">
        <EmptyState description="暂无历史面试数据，完成至少一场面试后再回来查看。" />
      </Card>
    </div>
  </section>
</template>

<style scoped>
.page-grid--dashboard {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(400px, 1fr));
  gap: var(--spacing-lg);
}
.weakness-list {
  display: flex;
  flex-direction: column;
  gap: var(--spacing-md);
  margin-top: var(--spacing-md);
}
.weakness-item {
  padding: var(--spacing-sm);
  border-radius: var(--radius-lg);
  background: var(--color-surface);
  border: 1px solid var(--color-border);
  page-break-inside: avoid;
  break-inside: avoid;
}
.weakness-item__head {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: var(--spacing-sm);
}
.weakness-item__title {
  margin: 0 0 var(--spacing-xs) 0;
  font-size: 15px;
  font-weight: 500;
  color: var(--color-text-primary);
}
.weakness-item__summary {
  margin: 0;
  font-size: 13px;
  color: var(--color-text-secondary);
}
.weakness-item__descriptions {
  margin: 0;
  padding-left: var(--spacing-xl);
  font-size: 13.5px;
  color: var(--color-text-tertiary);
  list-style-type: disc;
}
.weakness-item__descriptions li {
  margin-bottom: var(--spacing-xs);
  line-height: 1.5;
}
</style>
