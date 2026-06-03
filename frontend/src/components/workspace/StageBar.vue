<script setup lang="ts">
import { computed } from 'vue'
import { Button } from '@/components/ui/button'
import { Loader2 } from 'lucide-vue-next'
import type { InterviewStageName } from '../../api/contracts'

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
  () => props.currentStage === 'closing' && !props.isFinished && !props.finishing && !props.sending
)
</script>

<template>
  <div class="stage-bar">
    <div class="stage-actions" v-if="activeSessionId">
      <Button
        variant="secondary"
        size="sm"
        :disabled="!canFinish"
        @click="emit('finish')"
      >
        <Loader2 v-if="finishing" class="w-4 h-4 mr-2 animate-spin" />
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
