<script setup lang="ts">
import { computed } from 'vue'
import { ElButton } from 'element-plus'
import type { InterviewStageName } from '../../api/contracts'

const props = defineProps<{
  currentStage?: InterviewStageName
  activeSessionId?: number | null
  hasPendingAssistantPrompt: boolean
  stageUpdating: boolean
  sending: boolean
  finishing: boolean
  isFinished: boolean
}>()

const emit = defineEmits<{
  (e: 'advance', stage: InterviewStageName): void
  (e: 'auto-start'): void
  (e: 'finish'): void
}>()

const canFinish = computed(
  () => props.currentStage === 'closing' && !props.isFinished && !props.finishing && !props.sending && !props.stageUpdating
)
</script>

<template>
  <div class="stage-bar">
    <div class="stage-actions" v-if="activeSessionId">
      <ElButton
        class="ui-button ui-button--secondary"
        :disabled="!canFinish"
        :loading="finishing"
        @click="emit('finish')"
      >
        生成报告
      </ElButton>
    </div>
  </div>
</template>

<style scoped>
.stage-bar {
  display: inline-flex;
  align-items: center;
}
.stage-actions {
  display: flex;
  gap: 10px;
}
</style>
