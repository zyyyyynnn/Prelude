<script setup lang="ts">
import { Button } from '@/shared/ui/button'

withDefaults(
  defineProps<{
    message?: string | null
    retryLabel?: string
  }>(),
  {
    message: '加载失败，请重试。',
    retryLabel: '重试',
  },
)

defineEmits<{
  (event: 'retry'): void
}>()
</script>

<template>
  <div class="inline-async-error" role="alert" aria-live="polite">
    <p class="inline-async-error__message">{{ message || '加载失败，请重试。' }}</p>
    <Button type="button" variant="secondary" size="sm" @click="$emit('retry')">
      {{ retryLabel }}
    </Button>
  </div>
</template>

<style scoped>
.inline-async-error {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: var(--spacing-sm);
  padding: var(--spacing-md);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  background: var(--color-surface);
  color: var(--color-error);
}

.inline-async-error__message {
  margin: 0;
  font-size: var(--font-size-sm);
}
</style>
