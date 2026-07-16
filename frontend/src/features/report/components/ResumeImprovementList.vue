<script setup lang="ts">
import { Badge } from '@/shared/ui/badge'
import { Button } from '@/shared/ui/button'
import type { ReportResumeImprovement } from '../model/types'

defineProps<{
  improvements: ReportResumeImprovement[]
  busyId?: number | null
}>()

const emit = defineEmits<{
  (event: 'accept', improvement: ReportResumeImprovement): void
  (event: 'reject', improvement: ReportResumeImprovement): void
}>()

function fieldLabel(path: string) {
  if (path === 'summary') return '个人摘要'
  const projectBullet = path.match(/^projects\[(\d+)]\.bullets\[(\d+)]$/)
  if (projectBullet)
    return `项目 ${Number(projectBullet[1]) + 1} · 要点 ${Number(projectBullet[2]) + 1}`
  const projectOutcome = path.match(/^projects\[(\d+)]\.outcome$/)
  if (projectOutcome) return `项目 ${Number(projectOutcome[1]) + 1} · 成果`
  const experienceBullet = path.match(/^experiences\[(\d+)]\.bullets\[(\d+)]$/)
  if (experienceBullet) {
    return `经历 ${Number(experienceBullet[1]) + 1} · 要点 ${Number(experienceBullet[2]) + 1}`
  }
  return '简历字段'
}

function statusLabel(status: ReportResumeImprovement['status']) {
  if (status === 'accepted') return '已接受'
  if (status === 'rejected') return '已拒绝'
  return '待决定'
}
</script>

<template>
  <section
    v-if="improvements.length"
    class="report-section resume-improvements"
    aria-labelledby="resume-improvements-title"
  >
    <header>
      <p>简历闭环</p>
      <h2 id="resume-improvements-title">基于本场证据的改写建议</h2>
    </header>

    <div class="resume-improvements__list">
      <article v-for="item in improvements" :key="item.id" class="resume-improvement">
        <div class="resume-improvement__header">
          <h3>{{ fieldLabel(item.targetPath) }}</h3>
          <Badge variant="secondary">{{ statusLabel(item.status) }}</Badge>
        </div>
        <dl class="resume-improvement__diff">
          <div>
            <dt>当前表述</dt>
            <dd>{{ item.currentText || '暂无内容' }}</dd>
          </div>
          <div>
            <dt>建议表述</dt>
            <dd>{{ item.proposedText }}</dd>
          </div>
        </dl>
        <div class="resume-improvement__evidence">
          <p><strong>面试证据</strong>{{ item.evidence }}</p>
          <p><strong>改写理由</strong>{{ item.rationale }}</p>
        </div>
        <div
          v-if="item.status === 'pending'"
          class="resume-improvement__actions"
          data-html2canvas-ignore="true"
        >
          <Button
            size="sm"
            :loading="busyId === item.id"
            :disabled="busyId != null"
            @click="emit('accept', item)"
          >
            接受并写入简历
          </Button>
          <Button
            size="sm"
            variant="secondary"
            :disabled="busyId != null"
            @click="emit('reject', item)"
          >
            拒绝
          </Button>
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
.report-section > header {
  margin-bottom: var(--spacing-lg);
}
.report-section > header > p {
  margin: 0 0 var(--spacing-xs);
  color: var(--color-text-tertiary);
  font-family: var(--font-serif);
  font-size: var(--font-size-xs);
}
.report-section h2,
.report-section h3 {
  margin: 0;
  color: var(--color-text-primary);
  font-family: var(--font-serif);
}
.report-section h2 {
  font-size: var(--font-size-lg);
}
.report-section h3 {
  font-size: var(--font-size-md);
}
.resume-improvements__list {
  display: grid;
  gap: var(--spacing-md);
}
.resume-improvement {
  display: grid;
  gap: var(--spacing-md);
  padding: var(--spacing-lg);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  background: var(--color-surface);
}
.resume-improvement__header,
.resume-improvement__actions {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--spacing-sm);
}
.resume-improvement__diff {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: var(--spacing-md);
  margin: 0;
}
.resume-improvement__diff > div {
  min-inline-size: 0;
  padding: var(--spacing-md);
  border-radius: var(--radius-sm);
  background: var(--color-surface-muted);
}
.resume-improvement dt,
.resume-improvement__evidence strong {
  display: block;
  margin-bottom: var(--spacing-xs);
  color: var(--color-text-tertiary);
  font-size: var(--font-size-xs);
  font-weight: 500;
}
.resume-improvement dd,
.resume-improvement__evidence p {
  margin: 0;
  color: var(--color-text-secondary);
  font-size: var(--font-size-sm);
  line-height: 1.7;
  overflow-wrap: anywhere;
}
.resume-improvement__evidence {
  display: grid;
  gap: var(--spacing-sm);
}
.resume-improvement__actions {
  justify-content: flex-end;
}
@media (max-width: 45rem) {
  .resume-improvement__diff {
    grid-template-columns: 1fr;
  }
  .resume-improvement__header {
    align-items: flex-start;
  }
}
</style>
