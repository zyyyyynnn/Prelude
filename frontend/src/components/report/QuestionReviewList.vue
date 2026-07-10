<script setup lang="ts">
import { ref, watch } from 'vue'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { ChevronLeft, ChevronRight } from '@lucide/vue'
import type { StructuredQuestionReview } from '../../api/contracts'

const props = defineProps<{
  reviews: StructuredQuestionReview[]
}>()

const activeIndex = ref(0)
function showReview(index: number) {
  activeIndex.value = Math.min(Math.max(index, 0), Math.max(props.reviews.length - 1, 0))
}

watch(() => props.reviews.length, () => showReview(activeIndex.value))

const labels: Record<string, string> = {
  warmup: '破冰', technical: '技术问答', deep_dive: '深度追问', closing: '收尾复盘',
}
</script>

<template>
  <section
    class="report-section question-review-carousel"
    aria-labelledby="question-review-title"
    tabindex="0"
    @keydown.left.prevent="showReview(activeIndex - 1)"
    @keydown.right.prevent="showReview(activeIndex + 1)"
  >
    <header class="report-section__header">
      <div>
        <p>回答证据</p>
        <h2 id="question-review-title">逐题复盘</h2>
      </div>
      <div v-if="reviews.length" class="question-review-carousel__nav" aria-label="逐题复盘导航">
        <span class="question-review-carousel__counter" aria-live="polite">{{ activeIndex + 1 }} / {{ reviews.length }}</span>
        <Button
          variant="ghost"
          size="icon"
          type="button"
          aria-label="上一题"
          :disabled="activeIndex === 0"
          @click="showReview(activeIndex - 1)"
        >
          <ChevronLeft class="size-4" />
        </Button>
        <Button
          variant="ghost"
          size="icon"
          type="button"
          aria-label="下一题"
          :disabled="activeIndex === reviews.length - 1"
          @click="showReview(activeIndex + 1)"
        >
          <ChevronRight class="size-4" />
        </Button>
      </div>
    </header>
    <div v-if="reviews.length" class="question-review-carousel__viewport">
      <ol
        id="question-review-track"
        class="question-review-list"
        :style="{ transform: `translate3d(-${activeIndex * 100}%, 0, 0)` }"
      >
        <li
          v-for="(review, index) in reviews"
          :key="`${review.stageName}-${index}`"
          class="question-review"
          :aria-hidden="index !== activeIndex"
        >
          <header>
            <span>第 {{ index + 1 }} 题 · {{ labels[review.stageName] || review.stageName }}</span>
            <Badge variant="secondary">{{ review.score == null ? '暂无评分' : `${review.score} / 10` }}</Badge>
          </header>
          <div class="question-review__body">
            <h3>{{ review.question }}</h3>
            <dl>
              <div><dt>回答摘要</dt><dd>{{ review.answerSummary }}</dd></div>
              <div><dt>评分依据</dt><dd>{{ review.scoringReason }}</dd></div>
              <div><dt>改进建议</dt><dd>{{ review.improvementSuggestion }}</dd></div>
            </dl>
          </div>
        </li>
      </ol>
    </div>
    <p v-else class="question-review-list__empty">当前报告没有可复盘的有效回答。</p>
  </section>
</template>

<style scoped>
.report-section {
  padding-block: var(--spacing-xl);
  border-top: 1px solid var(--color-border);
}
.report-section__header,
.question-review > header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--spacing-md);
}
.report-section__header { margin-bottom: var(--spacing-lg); }
.question-review-carousel:focus-visible {
  outline: none;
  box-shadow: inset 0 0 0 var(--spacing-0-5) var(--color-brand);
}
.question-review-carousel__nav {
  display: flex;
  align-items: center;
  gap: var(--spacing-xs);
}
.question-review-carousel__counter {
  min-inline-size: 3.5rem;
  color: var(--color-text-secondary);
  font-family: var(--font-sans);
  font-size: var(--font-size-sm);
  text-align: center;
}
.report-section__header p {
  margin: 0 0 var(--spacing-xs);
  color: var(--color-text-tertiary);
  font-family: var(--font-serif);
  font-size: var(--font-size-xs);
}
h2, h3 { margin: 0; color: var(--color-text-primary); font-family: var(--font-serif); }
h2 { font-size: var(--font-size-lg); }
h3 { margin-top: var(--spacing-md); font-size: var(--font-size-md); line-height: 1.5; }
.question-review-carousel__viewport {
  overflow: hidden;
}
.question-review-list {
  display: grid;
  grid-auto-columns: 100%;
  grid-auto-flow: column;
  margin: 0;
  padding: 0;
  list-style: none;
  transition: transform var(--motion-duration-base) var(--motion-ease-standard);
  will-change: transform;
}
.question-review {
  min-inline-size: 0;
  padding: var(--spacing-lg);
  background: var(--color-surface-muted);
  border-radius: var(--radius-lg);
}
.question-review > header > span {
  color: var(--color-text-tertiary);
  font-family: var(--font-serif);
  font-size: var(--font-size-xs);
}
.question-review__body {
  max-block-size: 24rem;
  overflow-y: auto;
  scrollbar-width: thin;
}
.question-review dl { margin: var(--spacing-md) 0 0; }
.question-review dl > div {
  display: grid;
  grid-template-columns: minmax(5rem, 0.25fr) 1fr;
  gap: var(--spacing-md);
  padding-block: var(--spacing-sm);
  border-top: 1px solid var(--color-border);
}
.question-review dt {
  color: var(--color-text-tertiary);
  font-family: var(--font-serif);
  font-size: var(--font-size-sm);
}
.question-review dd,
.question-review-list__empty {
  margin: 0;
  color: var(--color-text-secondary);
  font-family: var(--font-sans);
  font-size: var(--font-size-sm);
  line-height: 1.7;
}
@media (max-width: 40rem) {
  .report-section__header { align-items: flex-end; }
  .question-review { padding: var(--spacing-md); }
  .question-review dl > div { grid-template-columns: 1fr; gap: var(--spacing-xs); }
}
@media (prefers-reduced-motion: reduce) {
  .question-review-list { transition-duration: 0.01ms; }
}
:global(.pdf-export-clone) .question-review-carousel {
  box-shadow: none;
}
:global(.pdf-export-clone) .question-review-carousel__nav {
  display: none;
}
:global(.pdf-export-clone) .question-review-carousel__viewport {
  overflow: visible;
}
:global(.pdf-export-clone) .question-review-list {
  display: block;
  transform: none !important;
}
:global(.pdf-export-clone) .question-review {
  margin-top: var(--spacing-md);
}
:global(.pdf-export-clone) .question-review__body {
  max-block-size: none;
  overflow: visible;
}
</style>
