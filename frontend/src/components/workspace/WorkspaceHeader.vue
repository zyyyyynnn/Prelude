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
}>()

const emit = defineEmits<{
  (e: 'finish'): void
  (e: 'toggle-report', show: boolean): void
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
.workspace-header {
  display: flex;
  align-items: center;
  padding: 16px 24px;
  border-bottom: 1px solid var(--color-border);
  background: rgba(250, 249, 245, 0.85);
  backdrop-filter: blur(12px);
  position: sticky;
  top: 0;
  z-index: 100;
  flex-shrink: 0;
  height: 72px;
  box-sizing: border-box;
}
.workspace-header__main {
  display: flex;
  align-items: center;
  justify-content: space-between;
  width: 100%;
}
.workspace-header__right {
  display: flex;
  align-items: center;
  gap: 20px;
}
.workspace-header__stage-wrap {
  display: flex;
  align-items: center;
}
.workspace-header__title-area {
  display: flex;
  align-items: center;
  gap: 12px;
}
.workspace-header__title {
  margin: 0;
  font-family: var(--font-serif);
  font-size: 20px;
  font-weight: 500;
  color: var(--color-text-primary);
}
.segmented-control {
  display: flex;
  background: color-mix(in srgb, var(--color-border) 30%, var(--color-surface));
  padding: 4px;
  border-radius: 8px;
  border: 1px solid var(--color-border);
}
.segmented-control__item {
  border: none;
  background: transparent;
  padding: 6px 16px;
  font-size: 13px;
  font-weight: 500;
  border-radius: 6px;
  color: var(--color-text-secondary);
  cursor: pointer;
  transition: all 0.2s ease;
}
.segmented-control__item:hover {
  color: var(--color-text-primary);
}
.segmented-control__item.is-active {
  background: var(--color-surface);
  color: var(--color-text-primary);
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.08);
}
</style>
