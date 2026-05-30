<script setup lang="ts">
import { nextTick, ref, watch } from 'vue'
import { ElTag } from 'element-plus'
import type { InterviewMessageRecord, InterviewMessageRole } from '../../api/contracts'

const props = defineProps<{
  messages: InterviewMessageRecord[]
}>()

const threadRef = ref<HTMLElement | null>(null)

watch(() => props.messages, () => {
  nextTick(() => {
    if (threadRef.value) {
      threadRef.value.scrollTop = threadRef.value.scrollHeight
    }
  })
}, { deep: true })
</script>

<template>
  <div class="message-thread scrollable" ref="threadRef">
    <div v-if="!messages.length" class="message-thread__empty">
      <p class="message-thread__empty-copy">会话已准备就绪，可以开始面试了。</p>
    </div>

    <template v-else>
      <article
        v-for="message in messages"
        :key="`${message.id}-${message.createdAt}`"
        :class="['message-bubble', `message-bubble--${message.role as InterviewMessageRole}`]"
      >
        <div class="message-bubble__head" v-if="message.role !== 'system'">
          <ElTag class="ui-badge" effect="light">
            {{ message.role === 'assistant' ? '面试官' : '我' }}
          </ElTag>
        </div>
        <div class="message-bubble__content">{{ message.content || '...' }}</div>
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
.message-bubble--system {
  align-self: center;
  align-items: center;
  max-width: 90%;
  margin: 16px 0;
}
.message-bubble--system .message-bubble__content {
  background: transparent;
  color: var(--color-text-tertiary);
  font-size: 13px;
  text-align: center;
  border: none;
  padding: 0;
  display: flex;
  align-items: center;
  gap: 16px;
  width: 100%;
}
.message-bubble--system .message-bubble__content::before,
.message-bubble--system .message-bubble__content::after {
  content: '';
  flex: 1;
  height: 1px;
  background: var(--color-border);
  min-width: 40px;
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
}
.message-bubble--assistant .message-bubble__content {
  border-top-left-radius: 4px;
}
.message-bubble--user .message-bubble__content {
  border-top-right-radius: 4px;
}
</style>
