<script setup lang="ts">
import { LineChart, RadarChart } from 'echarts/charts'
import { GridComponent, RadarComponent, TooltipComponent } from 'echarts/components'
import { init, use, type ECharts } from 'echarts/core'
import { CanvasRenderer } from 'echarts/renderers'
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { ElCard, ElEmpty, ElTag } from 'element-plus'
import { fetchRadarAnalytics, fetchTrendAnalytics, fetchWeaknessAnalytics } from '../api/analytics'
import type { AnalyticsRadarResponse, AnalyticsTrendPoint, AnalyticsWeaknessItem } from '../api/contracts'
import { usePageNotice } from '../composables/usePageNotice'

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

function getErrorMessage(error: unknown) {
  return error instanceof Error ? error.message : '请求失败'
}

function weaknessDetails(item: AnalyticsWeaknessItem) {
  return item.descriptions.slice(1)
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
      tooltip: { trigger: 'axis' },
      grid: { left: 44, right: 18, top: 30, bottom: 30 },
      xAxis: {
        type: 'category',
        boundaryGap: false,
        data: trend.value.map((item) => new Date(item.createdAt).toLocaleDateString()),
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
  <section class="page">
    <div class="page__hero">
      <div class="page__hero-main">
        <p class="eyebrow">分析</p>
        <h2 class="page__title">数据看板</h2>
        <p class="page__lead">查看评分、趋势和薄弱点统计。</p>
      </div>
    </div>

    <template v-if="radar && radar.sessionCount > 0">
      <div class="insight-strip insight-strip--compact">
        <article v-for="item in scoreCards" :key="item.label" class="insight-card">
          <p class="panel__eyebrow">{{ item.label }}</p>
          <h3 class="insight-card__value">{{ item.value }}</h3>
          <p class="insight-card__meta">{{ item.hint }}</p>
        </article>
      </div>

      <div class="page__grid page__grid--dashboard">
        <ElCard class="ui-card panel">
          <div class="panel__head">
            <div>
              <p class="panel__eyebrow">结构</p>
              <h3 class="panel__title">能力雷达</h3>
              <p class="panel__lead">展示最近面试在三项核心维度上的平均水平。</p>
            </div>
            <ElTag class="ui-badge" effect="light">{{ radar.sessionCount }} 场</ElTag>
          </div>
          <div ref="radarRef" class="chart-surface chart-surface--tall" />
        </ElCard>

        <ElCard class="ui-card panel">
          <div class="panel__head">
            <div>
              <p class="panel__eyebrow">走势</p>
              <h3 class="panel__title">分数趋势</h3>
              <p class="panel__lead">按时间查看技术、表达与逻辑评分变化。</p>
            </div>
          </div>
          <div ref="trendRef" class="chart-surface chart-surface--tall" />
        </ElCard>
      </div>

      <div class="page__grid page__grid--single">
        <ElCard class="ui-card panel">
          <div class="panel__head">
            <div>
              <p class="panel__eyebrow">聚合</p>
              <h3 class="panel__title">薄弱点列表</h3>
              <p class="panel__lead">按出现频率汇总薄弱点。</p>
            </div>
            <ElTag class="ui-badge" effect="light">{{ weaknesses.length }} 类问题</ElTag>
          </div>

          <div v-if="weaknesses.length" class="weakness-list">
            <article v-for="item in weaknesses" :key="item.category" class="weakness-item">
              <div class="weakness-item__head">
                <div>
                  <h4 class="weakness-item__title">{{ item.category }}</h4>
                  <p class="weakness-item__summary">{{ item.descriptions[0] || '待补充说明' }}</p>
                </div>
                <ElTag class="ui-badge" effect="light">{{ item.count }} 次</ElTag>
              </div>
              <ul v-if="weaknessDetails(item).length" class="weakness-item__descriptions">
                <li v-for="description in weaknessDetails(item)" :key="description">{{ description }}</li>
              </ul>
            </article>
          </div>
          <ElEmpty v-else description="还没有可聚合的薄弱点。" />
        </ElCard>
      </div>
    </template>

    <ElCard v-else class="ui-card panel">
      <ElEmpty description="暂无历史面试数据，完成至少一场面试后再回来查看。" />
    </ElCard>
  </section>
</template>
