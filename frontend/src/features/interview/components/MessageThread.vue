<script setup lang="ts">
import { computed, nextTick, onMounted, ref, watch } from 'vue'
import { Badge } from '@/components/ui/badge'
import type { InterviewMessageRecord, InterviewMessageRole } from '@/api/contracts'

const props = defineProps<{
  messages: InterviewMessageRecord[]
  reconnectingStatus?: string
}>()

const threadRef = ref<HTMLElement | null>(null)

const displayMessages = computed(() => {
  return props.messages
    .filter((m) => m.role !== 'system')
    .map((m) => ({
      ...m,
      content: m.content
        ? m.content
            .replace(/\[STAGE[_\s]?COMPLETE\]?/g, '')
            .replace(/\[STAGE(?:_(?:COM(?:P(?:L(?:E(?:TE?)?)?)?)?)?)?$/, '')
        : '',
    }))
})

let scrollRafId: number | null = null

watch(
  () => props.messages,
  () => {
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
  },
  { deep: true },
)

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
          <span v-if="message.role === 'assistant' && !message.content" class="thinking-dots"
            >思考中</span
          >
          <span v-else>{{ message.content }}</span>
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
  /* 消息流的几何约束：bubble 按内容宽度控制，不使用控件高度倍数。 */
  --message-bubble-gap: var(--spacing-sm);
  --message-bubble-max-inline-size: min(80%, var(--content-message-max-inline-size));

  flex: 1;
  overflow-y: auto;
  scrollbar-gutter: stable;
  padding: var(--spacing-lg) var(--spacing-2xl) var(--composer-height);
  display: flex;
  flex-direction: column;
  gap: var(--spacing-lg);
  min-block-size: 0;
}
.message-thread__empty {
  display: flex;
  align-items: center;
  justify-content: center;
  block-size: 100%;
  color: var(--color-text-tertiary);
}
.message-bubble {
  display: flex;
  flex-direction: column;
  gap: var(--message-bubble-gap);
  max-inline-size: var(--message-bubble-max-inline-size);
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
  padding: var(--spacing-sm) var(--spacing-md);
  border-radius: var(--radius-lg);
  background: var(--color-surface);
  border: 1px solid var(--color-border);
  line-height: 1.6;
  white-space: pre-wrap;
  word-break: break-word;
  box-shadow: var(--shadow-whisper);
  font-family: var(--font-sans);
}
.message-bubble--user .message-bubble__content {
  background: var(--color-surface-muted);
  border-color: var(--color-border);
  border-top-right-radius: var(--radius-sm);
}
.message-bubble--assistant .message-bubble__content {
  background: var(--color-surface);
  border: 1px solid var(--color-border);
  box-shadow: var(--shadow-whisper);
  border-top-left-radius: var(--radius-sm);
}
.thinking-dots {
  color: var(--color-text-tertiary);
}
.thinking-dots::after {
  content: '';
  animation: thinking-ellipsis var(--motion-duration-thinking) infinite;
}
@keyframes thinking-ellipsis {
  0% {
    content: '.';
  }
  33% {
    content: '..';
  }
  66% {
    content: '...';
  }
}
.reconnecting-status {
  align-self: center;
  padding: var(--spacing-sm) var(--spacing-md);
  background: var(--color-surface);
  border: 1px dashed var(--color-border);
  border-radius: var(--radius-lg);
  color: var(--color-text-secondary);
  font-size: var(--font-size-sm);
  box-shadow: var(--shadow-whisper);
  margin-top: var(--spacing-sm);
}
.reconnecting-status::after {
  content: '';
  animation: thinking-ellipsis var(--motion-duration-thinking) infinite;
}
</style>
