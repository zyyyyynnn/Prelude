<script setup lang="ts">
import { computed, nextTick, ref, watch } from 'vue'
import { ElTag } from 'element-plus'
import type { InterviewMessageRecord, InterviewMessageRole } from '../../api/contracts'

const props = defineProps<{
  messages: InterviewMessageRecord[]
}>()

const threadRef = ref<HTMLElement | null>(null)

const displayMessages = computed(() => {
  return props.messages
    .filter(m => m.role !== 'system')
    .map(m => ({
      ...m,
      content: m.content
        ? m.content
            .replace(/\[STAGE[_\s]?COMPLETE\]?/g, '')
            .replace(/\[STAGE(?:_(?:COM(?:P(?:L(?:E(?:TE?)?)?)?)?)?)?$/, '')
        : ''
    }))
})

let scrollRafId: number | null = null

watch(() => props.messages, () => {
  if (scrollRafId != null) {
    cancelAnimationFrame(scrollRafId)
  }
  nextTick(() => {
    scrollRafId = requestAnimationFrame(() => {
      scrollRafId = null
      if (threadRef.value) {
        threadRef.value.scrollTop = threadRef.value.scrollHeight
      }
    })
  })
}, { deep: true })
</script>

<template>
  <div class="message-thread scrollable" ref="threadRef">
    <div v-if="!displayMessages.length" class="message-thread__empty">
      <p class="message-thread__empty-copy">会话已准备就绪，可以开始面试了。</p>
    </div>

    <template v-else>
      <article
        v-for="message in displayMessages"
        :key="`${message.id}-${message.createdAt}`"
        :class="['message-bubble', `message-bubble--${message.role as InterviewMessageRole}`]"
      >
        <div class="message-bubble__head">
          <ElTag class="ui-badge" effect="light">
            {{ message.role === 'assistant' ? '面试官' : '我' }}
          </ElTag>
        </div>
        <div class="message-bubble__content">
          <span v-if="message.role === 'assistant' && !message.content" class="thinking-dots">思考中</span>
          <span v-else>{{ message.content }}</span>
        </div>
      </article>
    </template>
  </div>
</template>

<style scoped>
.message-thread {
  flex: 1;
  overflow-y: auto;
  padding: 24px 24px 260px 24px;
  display: flex;
  flex-direction: column;
  gap: 24px;
  min-height: 0;
}
.message-thread__empty {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 100%;
  color: var(--color-text-tertiary);
}
.message-bubble {
  display: flex;
  flex-direction: column;
  gap: 8px;
  max-width: 80%;
}
.message-bubble--user {
  align-self: flex-end;
  align-items: flex-end;
}
.message-bubble--assistant {
  align-self: flex-start;
  align-items: flex-start;
}
.message-bubble__content {
  padding: 12px 16px;
  border-radius: var(--radius-lg);
  background: var(--color-surface);
  border: 1px solid var(--color-border);
  line-height: 1.6;
  white-space: pre-wrap;
  word-break: break-word;
  box-shadow: var(--shadow-whisper);
}
.message-bubble--user .message-bubble__content {
  background: color-mix(in srgb, var(--color-brand) 8%, var(--color-surface));
  border-color: color-mix(in srgb, var(--color-brand) 20%, var(--color-border));
  border-top-right-radius: 4px;
}
.message-bubble--assistant .message-bubble__content {
  border-top-left-radius: 4px;
}
.thinking-dots {
  color: var(--color-text-tertiary);
}
.thinking-dots::after {
  content: '';
  animation: thinking-ellipsis 1.5s infinite;
}
@keyframes thinking-ellipsis {
  0% { content: '.'; }
  33% { content: '..'; }
  66% { content: '...'; }
}
</style>
