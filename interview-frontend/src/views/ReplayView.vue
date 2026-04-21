<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElAlert, ElButton, ElCard, ElTag } from 'element-plus'
import { fetchInterviewMessages } from '../api/interview'
import type { InterviewReplayResponse } from '../api/contracts'
import { stageLabel } from '../utils/interview'

const route = useRoute()
const router = useRouter()

const loading = ref(false)
const statusMessage = ref('')
const replay = ref<InterviewReplayResponse | null>(null)

const sessionId = computed(() => Number(route.params.sessionId))

function getErrorMessage(error: unknown) {
  return error instanceof Error ? error.message : '请求失败'
}

async function loadReplay() {
  loading.value = true
  try {
    replay.value = await fetchInterviewMessages(sessionId.value)
    statusMessage.value = ''
  } catch (error) {
    statusMessage.value = getErrorMessage(error)
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  void loadReplay()
})
</script>

<template>
  <section class="page">
    <div class="page__header">
      <p class="eyebrow">回放</p>
      <h2 class="page__title">会话回放</h2>
      <p class="page__lead">查看完整消息记录、system 提示和阶段推进时间线。</p>
    </div>

    <div class="page__subnav">
      <ElButton class="ui-button ui-button--secondary" size="large" @click="router.push('/interview')">
        返回主工作台
      </ElButton>
    </div>

    <ElAlert
      v-if="statusMessage"
      class="status-banner"
      :closable="false"
      :title="statusMessage"
      type="error"
    />

    <div v-if="replay" class="page__grid page__grid--single">
      <ElCard class="ui-card panel">
        <div class="panel__head">
          <div>
            <p class="panel__eyebrow">会话 #{{ replay.sessionId }}</p>
            <h3 class="panel__title">{{ replay.targetPosition || '面试回放' }}</h3>
          </div>
          <div class="button-row">
            <ElTag class="ui-badge" effect="light">{{ replay.status || 'unknown' }}</ElTag>
            <ElTag class="ui-badge" effect="light">{{ stageLabel(replay.currentStage) }}</ElTag>
          </div>
        </div>

        <div class="stage-timeline">
          <article v-for="stage in replay.stages" :key="`${stage.stageName}-${stage.startedAt}`" class="stage-timeline__item">
            <div class="stage-timeline__dot" />
            <div>
              <h4 class="stage-timeline__title">{{ stageLabel(stage.stageName) }}</h4>
              <p class="stage-timeline__meta">
                {{ stage.startedAt ? new Date(stage.startedAt).toLocaleString() : '--' }}
                <span v-if="stage.endedAt"> - {{ new Date(stage.endedAt).toLocaleString() }}</span>
              </p>
            </div>
          </article>
        </div>
      </ElCard>

      <ElCard class="ui-card panel">
        <div class="panel__head">
          <div>
            <p class="panel__eyebrow">消息</p>
            <h3 class="panel__title">完整记录</h3>
          </div>
        </div>

        <div class="replay-list">
          <article
            v-for="message in replay.messages"
            :key="message.id"
            :class="['replay-item', `replay-item--${message.role}`]"
          >
            <div class="replay-item__head">
              <ElTag class="ui-badge" effect="light">
                {{ message.role === 'system' ? '系统' : message.role === 'assistant' ? '面试官' : '我' }}
              </ElTag>
              <span class="replay-item__meta">
                {{ message.createdAt ? new Date(message.createdAt).toLocaleString() : '' }}
              </span>
            </div>
            <p class="replay-item__content">{{ message.content }}</p>
          </article>
        </div>
      </ElCard>
    </div>
  </section>
</template>
