<script setup lang="ts">
import { computed, nextTick, onMounted, ref, watch } from 'vue'
import { Badge } from '@/components/ui/badge'
import { TooltipText } from '@/components/ui/tooltip'
import type { InterviewMessageRecord, InterviewMessageRole } from '../../api/contracts'

const props = defineProps<{
  messages: InterviewMessageRecord[]
  reconnectingStatus?: string
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

onMounted(() => {
  nextTick(() => {
    requestAnimationFrame(() => {
      if (threadRef.value) {
        threadRef.value.scrollTop = threadRef.value.scrollHeight
      }
    })
  })
})
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
          <Badge variant="secondary">
            {{ message.role === 'assistant' ? '面试官' : '我' }}
          </Badge>
        </div>
        <div class="message-bubble__content">
          <span v-if="message.role === 'assistant' && !message.content" class="thinking-dots">思考中</span>
          <span v-else>{{ message.content }}</span>
        </div>
        <div v-if="message.role === 'user'" class="message-bubble__judge-container">
          <transition name="fade">
            <div v-if="message.score" class="judge-badge text-xs">
              <span class="judge-badge__score">评分：{{ message.score }}/10</span>
              <TooltipText v-if="message.hint" class="judge-badge__hint" :text="message.hint" />
            </div>
          </transition>
        </div>
      </article>

      <div v-if="reconnectingStatus" class="reconnecting-status">
        {{ reconnectingStatus }}
      </div>
    </template>
  </div>
</template>

<style scoped>
.message-thread {
  flex: 1;
  overflow-y: auto;
  scrollbar-gutter: stable;
  padding: var(--spacing-lg) var(--spacing-2xl) var(--composer-height);
  display: flex;
  flex-direction: column;
  gap: var(--spacing-lg);
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
  max-width: min(80%, 760px);
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
  background: var(--color-surface-muted);
  border-color: var(--color-border);
  border-top-right-radius: 4px;
}
.message-bubble--assistant .message-bubble__content {
  background: var(--color-surface);
  border: 1px solid var(--color-border);
  box-shadow: var(--shadow-whisper);
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
.reconnecting-status {
  align-self: center;
  padding: 8px 16px;
  background: var(--color-surface);
  border: 1px dashed var(--color-border);
  border-radius: var(--radius-lg);
  color: var(--color-text-secondary);
  font-size: 13px;
  box-shadow: var(--shadow-whisper);
  margin-top: 8px;
}
.reconnecting-status::after {
  content: '';
  animation: thinking-ellipsis 1.5s infinite;
}

.message-bubble__judge-container {
  margin-top: var(--spacing-xs);
  display: flex;
  justify-content: flex-end;
  width: 100%;
}
.judge-badge {
  display: flex;
  align-items: center;
  gap: var(--spacing-xs);
  max-width: min(100%, calc(var(--ui-height-base) * 15));
  color: var(--color-text-secondary);
  font-family: var(--font-serif);
  letter-spacing: 0.05em;
}
.judge-badge__score {
  flex-shrink: 0;
  padding: var(--spacing-xs) var(--spacing-sm);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-sm);
  background: var(--color-surface-muted);
  font-weight: 600;
  color: var(--color-brand);
}
.judge-badge__hint {
  min-width: 0;
  max-width: calc(var(--ui-height-base) * 10);
  padding: var(--spacing-xs) var(--spacing-sm);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-sm);
  background: var(--color-surface);
}

.fade-enter-active,
.fade-leave-active {
  transition: opacity 0.3s ease-in-out;
}
.fade-enter-from,
.fade-leave-to {
  opacity: 0;
}
</style>
