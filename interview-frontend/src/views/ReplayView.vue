<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { ElCard, ElEmpty, ElTag } from 'element-plus'
import { fetchInterviewMessages } from '../api/interview'
import type { InterviewReplayResponse } from '../api/contracts'
import { usePageNotice } from '../composables/usePageNotice'
import { stageLabel } from '../utils/interview'

const route = useRoute()
const { showNotice } = usePageNotice()

const loading = ref(false)
const replay = ref<InterviewReplayResponse | null>(null)

const sessionId = computed(() => Number(route.params.sessionId))

function getErrorMessage(error: unknown) {
  return error instanceof Error ? error.message : '请求失败'
}

async function loadReplay() {
  loading.value = true
  try {
    replay.value = await fetchInterviewMessages(sessionId.value)
  } catch (error) {
    showNotice(getErrorMessage(error), 'error')
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
    <div class="page__hero">
      <div class="page__hero-main">
        <p class="eyebrow">回放</p>
        <h2 class="page__title">会话回放</h2>
        <p class="page__lead">查看阶段时间线、消息记录与报告结果。</p>
      </div>
      <div class="page__hero-actions">
        <ElTag v-if="replay" class="ui-badge" effect="light">会话 #{{ replay.sessionId }}</ElTag>
      </div>
    </div>

    <div v-if="replay" class="page__grid page__grid--single">
      <div class="page__grid page__grid--dashboard">
        <ElCard class="ui-card panel">
          <div class="panel__head">
            <div>
              <p class="panel__eyebrow">概览</p>
              <h3 class="panel__title">{{ replay.targetPosition || '面试回放' }}</h3>
              <p class="panel__lead">当前会话状态与阶段概览。</p>
            </div>
            <div class="panel__actions">
              <ElTag class="ui-badge" effect="light">{{ replay.status || 'unknown' }}</ElTag>
              <ElTag class="ui-badge" effect="light">{{ stageLabel(replay.currentStage) }}</ElTag>
            </div>
          </div>

          <div class="detail-grid">
            <article class="detail-card">
              <p class="panel__eyebrow">阶段数</p>
              <h4 class="detail-card__title">{{ replay.stages.length }}</h4>
              <p class="detail-card__meta">包含破冰、技术、深挖与收尾</p>
            </article>
            <article class="detail-card">
              <p class="panel__eyebrow">消息数</p>
              <h4 class="detail-card__title">{{ replay.messages.length }}</h4>
              <p class="detail-card__meta">含 system、面试官与候选人消息</p>
            </article>
          </div>
        </ElCard>

        <ElCard class="ui-card panel">
          <div class="panel__head">
            <div>
              <p class="panel__eyebrow">时间线</p>
              <h3 class="panel__title">阶段推进</h3>
              <p class="panel__lead">按阶段查看起止时间。</p>
            </div>
          </div>

          <div class="stage-timeline">
            <article
              v-for="stage in replay.stages"
              :key="`${stage.stageName}-${stage.startedAt}`"
              class="stage-timeline__item"
            >
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
      </div>

      <ElCard class="ui-card panel">
        <div class="panel__head">
          <div>
            <p class="panel__eyebrow">消息</p>
            <h3 class="panel__title">完整记录</h3>
            <p class="panel__lead">保留系统、面试官和候选人消息。</p>
          </div>
        </div>

        <div class="replay-list replay-list--spacious">
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

    <ElCard v-else class="ui-card panel">
      <ElEmpty :description="loading ? '正在加载会话回放…' : '未找到可回放的会话。'" />
    </ElCard>
  </section>
</template>
