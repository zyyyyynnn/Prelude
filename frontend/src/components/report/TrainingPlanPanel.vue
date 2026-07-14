<script setup lang="ts">
import type { StructuredTrainingPlan } from '../../api/contracts'

const props = defineProps<{
  plan: StructuredTrainingPlan
}>()

const groups = [
  { key: 'threeDay', label: '3 天补强' },
  { key: 'sevenDay', label: '7 天专项' },
  { key: 'nextInterviewFocus', label: '下次模拟重点' },
] as const
</script>

<template>
  <section class="report-section training-plan" aria-labelledby="training-plan-title">
    <header>
      <p>下一步行动</p>
      <h2 id="training-plan-title">训练计划</h2>
    </header>
    <div class="training-plan__grid">
      <section v-for="(group, index) in groups" :key="group.key" class="training-plan__group">
        <span class="training-plan__step" aria-hidden="true">0{{ index + 1 }}</span>
        <div>
          <h3>{{ group.label }}</h3>
          <ol>
            <li v-for="item in props.plan[group.key]" :key="item">{{ item }}</li>
            <li v-if="!props.plan[group.key].length">按逐题复盘中的建议完成一次定向练习。</li>
          </ol>
        </div>
      </section>
    </div>
  </section>
</template>

<style scoped>
.report-section {
  padding-block: var(--spacing-xl);
  border-top: 1px solid var(--color-border);
}
.training-plan > header {
  margin-bottom: var(--spacing-lg);
}
.training-plan > header p {
  margin: 0 0 var(--spacing-xs);
  color: var(--color-text-tertiary);
  font-family: var(--font-serif);
  font-size: var(--font-size-xs);
}
h2,
h3 {
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
.training-plan__grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
}
.training-plan__group {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr);
  gap: var(--spacing-sm);
  min-inline-size: 0;
  padding-inline: var(--spacing-lg);
}
.training-plan__group:first-child {
  padding-inline-start: 0;
}
.training-plan__group + .training-plan__group {
  border-inline-start: 1px solid var(--color-border);
}
.training-plan__step {
  color: var(--color-text-tertiary);
  font-family: var(--font-sans);
  font-size: var(--font-size-xs);
  font-variant-numeric: tabular-nums;
}
.training-plan ol {
  display: grid;
  gap: var(--spacing-xs);
  margin: var(--spacing-sm) 0 0;
  padding: 0;
  color: var(--color-text-secondary);
  font-family: var(--font-sans);
  font-size: var(--font-size-sm);
  line-height: 1.7;
  list-style: none;
}
.training-plan li {
  display: grid;
  grid-template-columns: var(--spacing-sm) minmax(0, 1fr);
  gap: var(--spacing-sm);
}
.training-plan li::before {
  inline-size: var(--spacing-xs);
  block-size: var(--spacing-xs);
  margin-top: 0.65em;
  border-radius: 50%;
  background: var(--color-text-tertiary);
  content: '';
}
@media (max-width: 45rem) {
  .training-plan__grid {
    grid-template-columns: 1fr;
  }
  .training-plan__group,
  .training-plan__group:first-child {
    padding: var(--spacing-md) 0;
  }
  .training-plan__group + .training-plan__group {
    border-inline-start: 0;
    border-top: 1px solid var(--color-border);
  }
}
</style>
