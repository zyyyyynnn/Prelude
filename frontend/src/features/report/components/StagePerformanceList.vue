<script setup lang="ts">
import { Badge } from '@/shared/ui/badge'
import type { StructuredStagePerformance } from '../model/types'

defineProps<{
  stages: StructuredStagePerformance[]
}>()

const labels: Record<string, string> = {
  warmup: '破冰',
  technical: '技术问答',
  deep_dive: '深度追问',
  closing: '收尾复盘',
}
</script>

<template>
  <section class="report-section" aria-labelledby="stage-performance-title">
    <header class="report-section__header">
      <div>
        <p>阶段复盘</p>
        <h2 id="stage-performance-title">分阶段表现</h2>
      </div>
    </header>
    <div class="stage-performance-list">
      <article v-for="stage in stages" :key="stage.stageName" class="stage-performance">
        <header>
          <h3>{{ labels[stage.stageName] || stage.stageName }}</h3>
          <Badge variant="secondary">{{
            stage.score == null ? '暂无评分' : `${stage.score.toFixed(1)} / 10`
          }}</Badge>
        </header>
        <p>{{ stage.summary }}</p>
        <div class="stage-performance__signals">
          <section v-if="stage.positiveSignals.length" class="stage-performance__signal">
            <h4>正向信号</h4>
            <ul>
              <li v-for="item in stage.positiveSignals" :key="item">{{ item }}</li>
            </ul>
          </section>
          <section v-if="stage.negativeSignals.length" class="stage-performance__signal">
            <h4>风险信号</h4>
            <ul>
              <li v-for="item in stage.negativeSignals" :key="item">{{ item }}</li>
            </ul>
          </section>
          <section v-if="stage.improvementSuggestions.length" class="stage-performance__signal">
            <h4>改进建议</h4>
            <ul>
              <li v-for="item in stage.improvementSuggestions" :key="item">{{ item }}</li>
            </ul>
          </section>
        </div>
      </article>
    </div>
  </section>
</template>

<style scoped>
.report-section {
  padding-block: var(--spacing-xl);
  border-top: 1px solid var(--color-border);
}
.report-section__header {
  margin-bottom: var(--spacing-lg);
}
.report-section__header p {
  margin: 0 0 var(--spacing-xs);
  color: var(--color-text-tertiary);
  font-family: var(--font-serif);
  font-size: var(--font-size-xs);
}
h2,
h3,
h4 {
  margin: 0;
  color: var(--color-text-primary);
  font-family: var(--font-serif);
}
h2 {
  font-size: var(--font-size-lg);
}
h3 {
  font-size: var(--font-size-md);
}
h4 {
  font-size: var(--font-size-sm);
}
.stage-performance {
  padding-block: var(--spacing-lg);
  border-top: 1px solid var(--color-border);
}
.stage-performance:first-child {
  padding-top: 0;
  border-top: 0;
}
.stage-performance > header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--spacing-md);
}
.stage-performance > p,
.stage-performance ul {
  color: var(--color-text-secondary);
  font-family: var(--font-sans);
  font-size: var(--font-size-sm);
  line-height: 1.7;
}
.stage-performance > p {
  max-inline-size: var(--content-reading-max-inline-size);
  margin: var(--spacing-sm) 0 0;
}
.stage-performance__signals {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: var(--spacing-md);
  margin-top: var(--spacing-md);
}
.stage-performance__signal {
  min-inline-size: 0;
  padding-top: var(--spacing-sm);
  border-top: 1px solid var(--color-border);
}
.stage-performance ul {
  margin: var(--spacing-sm) 0 0;
  padding: 0;
  list-style: none;
}
.stage-performance li {
  display: grid;
  grid-template-columns: var(--spacing-sm) minmax(0, 1fr);
  gap: var(--spacing-sm);
}
.stage-performance li + li {
  margin-top: var(--spacing-xs);
}
.stage-performance li::before {
  inline-size: var(--spacing-xs);
  block-size: var(--spacing-xs);
  margin-top: 0.65em;
  border-radius: 50%;
  background: var(--color-text-tertiary);
  content: '';
}
@media (max-width: 45rem) {
  .stage-performance__signals {
    grid-template-columns: 1fr;
  }
}
</style>
