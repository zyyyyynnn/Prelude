<script setup lang="ts">
import type { StructuredReportScores } from '../../api/contracts'

const props = defineProps<{
  scores: StructuredReportScores
}>()

const items = [
  { key: 'technical', label: '技术能力' },
  { key: 'expression', label: '表达清晰度' },
  { key: 'logic', label: '逻辑思维' },
] as const
</script>

<template>
  <section class="report-section report-scores" aria-labelledby="report-scores-title">
    <header class="report-section__header">
      <div>
        <p class="report-section__eyebrow">能力画像</p>
        <h2 id="report-scores-title">三维评分</h2>
      </div>
      <div class="report-scores__overall">
        <span>总体</span>
        <strong>{{ props.scores.overall.toFixed(1) }}</strong>
        <small>/ 10</small>
      </div>
    </header>
    <div class="report-scores__grid">
      <div v-for="item in items" :key="item.key" class="report-score-item">
        <span>{{ item.label }}</span>
        <strong>{{ props.scores[item.key].toFixed(1) }}</strong>
        <div class="report-score-item__track" aria-hidden="true">
          <span :style="{ inlineSize: `${props.scores[item.key] * 10}%` }" />
        </div>
      </div>
    </div>
  </section>
</template>

<style scoped>
.report-section {
  padding-block: var(--spacing-xl);
  border-top: 1px solid var(--color-border);
}
.report-section__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--spacing-lg);
  margin-bottom: var(--spacing-lg);
}
.report-section__eyebrow {
  margin: 0 0 var(--spacing-xs);
  color: var(--color-text-tertiary);
  font-family: var(--font-serif);
  font-size: var(--font-size-xs);
}
h2 {
  margin: 0;
  color: var(--color-text-primary);
  font-family: var(--font-serif);
  font-size: var(--font-size-lg);
}
.report-scores__overall {
  display: flex;
  align-items: baseline;
  gap: var(--spacing-xs);
  color: var(--color-text-secondary);
  font-family: var(--font-serif);
}
.report-scores__overall strong {
  color: var(--color-brand);
  font-size: var(--font-size-2xl);
  font-weight: 600;
}
.report-scores__grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: var(--spacing-md);
}
.report-score-item {
  padding: var(--spacing-md);
  background: var(--color-surface-muted);
  border-radius: var(--radius-lg);
}
.report-score-item > span {
  color: var(--color-text-secondary);
  font-family: var(--font-serif);
  font-size: var(--font-size-sm);
}
.report-score-item > strong {
  display: block;
  margin-block: var(--spacing-sm);
  color: var(--color-text-primary);
  font-family: var(--font-serif);
  font-size: var(--font-size-xl);
}
.report-score-item__track {
  block-size: var(--spacing-xs);
  overflow: hidden;
  background: var(--color-border);
  border-radius: var(--radius-full);
}
.report-score-item__track span {
  display: block;
  block-size: 100%;
  max-inline-size: 100%;
  background: var(--color-brand);
  border-radius: inherit;
}
@media (max-width: 40rem) {
  .report-scores__grid {
    grid-template-columns: 1fr;
  }
}
</style>
