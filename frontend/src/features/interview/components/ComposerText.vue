<script setup lang="ts">
import { Textarea } from '@/components/ui/textarea'

defineProps<{
  modelValue: string
  activeSessionId?: number | null
  disabled?: boolean
  canSend: boolean
  showJdInput: boolean
  jdText: string
}>()

const emit = defineEmits<{
  (event: 'update:modelValue', value: string): void
  (event: 'update:jdText', value: string): void
  (event: 'send'): void
}>()
</script>

<template>
  <div class="composer-mode-text">
    <Textarea
      :model-value="modelValue"
      @update:model-value="(value: string | number) => emit('update:modelValue', String(value))"
      :rows="3"
      class="composer-textarea resize-none border-0 bg-transparent shadow-none p-[var(--spacing-sm)] text-base focus-visible:ring-0 focus-visible:ring-offset-0 disabled:cursor-default disabled:opacity-50"
      :placeholder="activeSessionId ? '输入回答...' : '请先选择简历与岗位，然后点击「开始面试」'"
      :disabled="disabled || !activeSessionId"
      @keydown.ctrl.enter="canSend && emit('send')"
      @keydown.meta.enter="canSend && emit('send')"
    />
    <transition name="jd-fade-float">
      <div
        v-if="!activeSessionId && showJdInput"
        class="absolute inset-0 z-[var(--z-index-workspace-composer)] bg-surface"
      >
        <Textarea
          :model-value="jdText"
          @update:model-value="(value: string | number) => emit('update:jdText', String(value))"
          :rows="3"
          class="composer-textarea h-full w-full resize-none border-0 bg-transparent shadow-none p-[var(--spacing-sm)] text-base focus-visible:ring-0 focus-visible:ring-offset-0"
          placeholder="粘贴目标岗位职责或 JD 文本，系统将通过 RAG 算法进行智能分块和背景匹配发问..."
        />
      </div>
    </transition>
  </div>
</template>

<style scoped>
.composer-mode-text {
  inline-size: 100%;
}

.jd-fade-float-enter-active,
.jd-fade-float-leave-active {
  transition:
    opacity var(--motion-duration-base) var(--motion-ease-standard),
    transform var(--motion-duration-base) var(--motion-ease-standard);
}
.jd-fade-float-enter-from {
  opacity: 0;
  transform: translateY(var(--spacing-xs));
}
.jd-fade-float-leave-to {
  opacity: 0;
  transform: translateY(var(--spacing-neg-xs));
}
.jd-fade-float-leave-active {
  pointer-events: none;
}
</style>
