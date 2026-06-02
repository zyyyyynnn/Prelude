<script setup lang="ts">
import { ElTag } from 'element-plus'
import StageBar from './StageBar.vue'
import type { InterviewStageName } from '../../api/contracts'

const props = defineProps<{
  activeSessionId?: number | null
  targetPosition?: string
  currentStage?: InterviewStageName
  sending: boolean
  finishing: boolean
  hasReport: boolean
  showingReport: boolean
  isFinished: boolean
  exporting?: boolean
}>()

const emit = defineEmits<{
  (e: 'finish'): void
  (e: 'toggle-report', show: boolean): void
  (e: 'export-pdf'): void
}>()
</script>

<template>
  <header class="workspace-header">
    <div class="workspace-header__main">
      <div class="workspace-header__title-area">
        <h2 class="workspace-header__title">{{ targetPosition || '新面试会话' }}</h2>
        <ElTag v-if="activeSessionId" class="ui-badge" effect="light">#{{ activeSessionId }}</ElTag>
      </div>
      
      <div class="workspace-header__right">
        <!-- PDF Export -->
        <div class="workspace-header__actions" v-if="activeSessionId && hasReport && showingReport">
          <button 
            class="ui-button ui-button--secondary ui-button--compact"
            :disabled="exporting"
            @click="emit('export-pdf')"
            type="button"
          >
            {{ exporting ? '导出中...' : '导出 PDF' }}
          </button>
        </div>

        <!-- Stage actions -->
        <div class="workspace-header__stage-wrap" v-if="activeSessionId && !showingReport">
          <StageBar 
            :current-stage="currentStage"
            :active-session-id="activeSessionId"
            :sending="sending"
            :finishing="finishing"
            :is-finished="isFinished"
            @finish="emit('finish')"
          />
        </div>

        <!-- Segmented control (面试 / 报告) -->
        <div class="workspace-header__actions segmented-control" v-if="activeSessionId && hasReport">
          <button 
            :class="['segmented-control__item', { 'is-active': !showingReport }]"
            @click="emit('toggle-report', false)"
          >面试</button>
          <button 
            :class="['segmented-control__item', { 'is-active': showingReport }]"
            @click="emit('toggle-report', true)"
          >报告</button>
        </div>
      </div>
    </div>
  </header>
</template>

<style scoped>
.workspace-header__right {
  display: flex;
  align-items: center;
  gap: var(--spacing-lg);
}
.workspace-header__stage-wrap {
  display: flex;
  align-items: center;
}
.segmented-control {
  display: flex;
  background: color-mix(in srgb, var(--color-border) 30%, var(--color-surface));
  padding: var(--spacing-xs);
  border-radius: var(--radius-md);
  border: 1px solid var(--color-border);
}
.segmented-control__item {
  border: none;
  background: transparent;
  padding: var(--spacing-xs) var(--spacing-md);
  font-size: 13px;
  font-weight: 500;
  border-radius: var(--radius-sm);
  color: var(--color-text-secondary);
  cursor: pointer;
  transition: all 0.2s ease;
  outline: none;
}
.segmented-control__item:hover {
  color: var(--color-text-primary);
}
.segmented-control__item:focus-visible {
  outline: 2px solid var(--color-focus);
  outline-offset: -2px;
}
.segmented-control__item.is-active {
  background: var(--color-surface);
  color: var(--color-text-primary);
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.08);
}
</style>
