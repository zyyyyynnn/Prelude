<script setup lang="ts">
import { computed } from 'vue'
import StageBar from './StageBar.vue'
import type { InterviewStageName } from '@/api/contracts'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { TooltipText } from '@/components/ui/tooltip'
import SegmentedControl from '@/components/ui/segmented-control/SegmentedControl.vue'

const props = defineProps<{
  activeSessionId?: number | null
  targetPosition?: string
  currentStage?: InterviewStageName
  sessionStatus?: string
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

const stageLabels: Record<InterviewStageName, string> = {
  warmup: '破冰',
  technical: '技术问答',
  deep_dive: '深挖追问',
  closing: '收尾',
}

const statusLabel = computed(() => {
  if (props.sessionStatus === 'generating') return '报告生成中'
  if (props.sessionStatus === 'finished' || props.isFinished) return '已完成'
  return stageLabels[props.currentStage || 'warmup']
})
</script>

<template>
  <header class="workspace-header">
    <div class="workspace-header__main">
      <div class="workspace-header__title-area">
        <TooltipText
          as="h2"
          class="workspace-header__title"
          :text="targetPosition || '新面试会话'"
        />
        <Badge v-if="activeSessionId" variant="secondary">{{ statusLabel }}</Badge>
      </div>

      <div class="workspace-header__right">
        <!-- PDF Export -->
        <div class="workspace-header__actions" v-if="activeSessionId && hasReport && showingReport">
          <Button
            variant="secondary"
            size="sm"
            class="!font-serif"
            :disabled="exporting"
            @click="emit('export-pdf')"
          >
            {{ exporting ? '导出中...' : '导出 PDF' }}
          </Button>
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
        <div class="workspace-header__actions" v-if="activeSessionId && hasReport">
          <SegmentedControl
            :items="['面试', '报告']"
            :model-value="showingReport ? '报告' : '面试'"
            @update:model-value="(val) => emit('toggle-report', val === '报告')"
          />
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
.workspace-header__actions {
  display: flex;
  align-items: center;
}
.workspace-header__stage-wrap {
  display: flex;
  align-items: center;
}
</style>
