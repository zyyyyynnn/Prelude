<script setup lang="ts">
import { computed } from 'vue'
import { Button } from '@/shared/ui/button'
import type { InterviewStageName } from '../model/types'

const props = defineProps<{
  currentStage?: InterviewStageName
  activeSessionId?: number | null
  sending: boolean
  finishing: boolean
  isFinished: boolean
}>()

const emit = defineEmits<{
  (e: 'finish'): void
}>()

const canFinish = computed(
  () => props.currentStage === 'closing' && !props.isFinished && !props.finishing && !props.sending,
)
</script>

<template>
  <div class="stage-bar">
    <div class="stage-actions" v-if="activeSessionId">
      <Button
        variant="secondary"
        size="sm"
        class="!font-serif"
        :disabled="!canFinish"
        :loading="finishing"
        @click="emit('finish')"
      >
        生成报告
      </Button>
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
  gap: var(--spacing-sm);
}
</style>
