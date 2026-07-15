<script setup lang="ts">
import { Card } from '@/shared/ui/card'
import type { StructuredInterviewReport } from '../model/types'
import QuestionReviewList from './QuestionReviewList.vue'
import ReportScoreCard from './ReportScoreCard.vue'
import StagePerformanceList from './StagePerformanceList.vue'
import TrainingPlanPanel from './TrainingPlanPanel.vue'

const props = defineProps<{
  report: StructuredInterviewReport
}>()
</script>

<template>
  <article class="structured-report">
    <header class="structured-report__hero">
      <p>Interview Review</p>
      <h1>求职训练报告</h1>
      <p class="structured-report__lede">{{ report.summary.fitAssessment }}</p>
    </header>

    <Card class="structured-report__summary border-none bg-surface-muted shadow-none">
      <section>
        <h2>行动建议</h2>
        <p>{{ report.summary.actionRecommendation }}</p>
      </section>
      <section>
        <h2>总体风险</h2>
        <p>{{ report.summary.overallRisk }}</p>
      </section>
    </Card>

    <ReportScoreCard :scores="report.scores" />
    <StagePerformanceList :stages="report.stagePerformances" />
    <QuestionReviewList :reviews="report.questionReviews" />

    <section class="report-section structured-report__traits" aria-labelledby="traits-title">
      <header>
        <p>能力沉淀</p>
        <h2 id="traits-title">优势与短板</h2>
      </header>
      <div>
        <section class="structured-report__trait">
          <h3>核心优势</h3>
          <ul>
            <li v-for="item in report.strengths" :key="item">{{ item }}</li>
          </ul>
          <p v-if="!report.strengths.length">暂无可归纳的优势。</p>
        </section>
        <section class="structured-report__trait">
          <h3>主要短板</h3>
          <ul>
            <li v-for="item in report.weaknesses" :key="item">{{ item }}</li>
          </ul>
          <p v-if="!report.weaknesses.length">暂无已沉淀的薄弱点。</p>
        </section>
      </div>
    </section>

    <TrainingPlanPanel :plan="report.trainingPlan" />

    <section class="report-section structured-report__advice">
      <p>总结建议</p>
      <h2>{{ report.finalAdvice }}</h2>
    </section>
  </article>
</template>

<style scoped>
.structured-report {
  inline-size: 100%;
  padding: var(--spacing-2xl);
  background: var(--color-surface);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  box-shadow: var(--shadow-whisper);
}
.structured-report__hero {
  display: grid;
  gap: var(--spacing-xs);
  min-inline-size: 0;
  padding-bottom: var(--spacing-xl);
}
.structured-report__hero > p:first-child,
.report-section > header > p,
.structured-report__advice > p {
  margin: 0 0 var(--spacing-xs);
  color: var(--color-text-tertiary);
  font-family: var(--font-serif);
  font-size: var(--font-size-xs);
}
.structured-report h1,
.structured-report h2,
.structured-report h3 {
  margin: 0;
  color: var(--color-text-primary);
  font-family: var(--font-serif);
}
.structured-report h1 {
  max-inline-size: 100%;
  font-size: var(--font-size-xl);
  font-weight: 600;
  white-space: nowrap;
}
.structured-report h2 {
  font-size: var(--font-size-lg);
}
.structured-report h3 {
  font-size: var(--font-size-md);
}
.structured-report__lede {
  max-inline-size: var(--content-reading-max-inline-size);
  margin: var(--spacing-sm) 0 0;
  color: var(--color-text-secondary);
  font-family: var(--font-serif);
  font-size: var(--font-size-md);
  line-height: 1.7;
  text-wrap: pretty;
}
.structured-report__summary {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: var(--spacing-lg);
  padding: var(--spacing-lg);
  border-radius: var(--radius-lg);
}
.structured-report__summary section + section {
  padding-inline-start: var(--spacing-lg);
  border-inline-start: 1px solid var(--color-border);
}
.structured-report__summary p,
.structured-report__traits p,
.structured-report__traits ul {
  margin: var(--spacing-sm) 0 0;
  color: var(--color-text-secondary);
  font-family: var(--font-sans);
  font-size: var(--font-size-sm);
  line-height: 1.7;
}
.report-section {
  padding-block: var(--spacing-xl);
  border-top: 1px solid var(--color-border);
}
.report-section > header {
  margin-bottom: var(--spacing-lg);
}
.structured-report__traits > div {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: var(--spacing-xl);
}
.structured-report__trait {
  padding-top: var(--spacing-md);
  border-top: 1px solid var(--color-border);
}
.structured-report__traits ul {
  display: grid;
  gap: var(--spacing-sm);
  padding: 0;
  list-style: none;
}
.structured-report__traits li {
  display: grid;
  grid-template-columns: var(--spacing-sm) minmax(0, 1fr);
  gap: var(--spacing-sm);
}
.structured-report__traits li::before {
  inline-size: var(--spacing-xs);
  block-size: var(--spacing-xs);
  margin-top: 0.65em;
  border-radius: 50%;
  background: var(--color-text-tertiary);
  content: '';
}
.structured-report__advice h2 {
  max-inline-size: var(--content-reading-max-inline-size);
  line-height: 1.6;
}
@media (max-width: 45rem) {
  .structured-report {
    padding: var(--spacing-lg);
  }
  .structured-report__summary,
  .structured-report__traits > div {
    grid-template-columns: 1fr;
  }
  .structured-report__summary section + section {
    padding-inline-start: 0;
    padding-top: var(--spacing-md);
    border-inline-start: 0;
    border-top: 1px solid var(--color-border);
  }
}
</style>
