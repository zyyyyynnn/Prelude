<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import {
  ElButton,
  ElCard,
  ElInput,
  ElOption,
  ElSelect,
  ElTag,
} from 'element-plus'
import { fetchPositions } from '../api/auth'
import type {
  InterviewMessageRecord,
  InterviewMessageRole,
  InterviewReplayResponse,
  InterviewSessionItem,
  InterviewStageName,
  PositionTemplate,
  ResumeItem,
} from '../api/contracts'
import {
  changeInterviewStage,
  fetchInterviewMessages,
  fetchInterviewSessions,
  finishInterview,
  startInterview,
  streamInterviewChat,
} from '../api/interview'
import { usePageNotice } from '../composables/usePageNotice'
import { fetchResumes, uploadResume } from '../api/resume'
import { useAuthStore } from '../stores/auth'
import { nextStage, stageLabel, STAGE_ORDER } from '../utils/interview'
import { renderMarkdown } from '../utils/markdown'

const router = useRouter()
const authStore = useAuthStore()
const { showNotice } = usePageNotice()

const loading = ref(false)
const sessionLoading = ref(false)
const creating = ref(false)
const uploading = ref(false)
const sending = ref(false)
const finishing = ref(false)
const stageUpdating = ref(false)
const uploadInput = ref<HTMLInputElement | null>(null)

const uploadDisplayName = ref('未选择任何文件')
const answer = ref('')

const resumes = ref<ResumeItem[]>([])
const positions = ref<PositionTemplate[]>([])
const sessions = ref<InterviewSessionItem[]>([])
const replay = ref<InterviewReplayResponse | null>(null)
const activeSessionId = ref<number | null>(null)
const reportMarkdown = ref('')

const selectedResumeId = ref<number | null>(null)
const selectedPositionId = ref<number | null>(null)

const activeSession = computed(() =>
  sessions.value.find((item) => item.sessionId === activeSessionId.value) ?? null,
)
const selectedResume = computed(() =>
  resumes.value.find((item) => item.id === selectedResumeId.value) ?? null,
)
const selectedPosition = computed(() =>
  positions.value.find((item) => item.id === selectedPositionId.value) ?? null,
)
const messages = computed(() => replay.value?.messages ?? [])
const currentStage = computed(() => replay.value?.currentStage ?? activeSession.value?.currentStage)
const nextStageName = computed(() => nextStage(currentStage.value))
const conversationStarted = computed(() =>
  messages.value.some((message) => message.role === 'user' || message.role === 'assistant'),
)
const canCreate = computed(
  () => Boolean(selectedResumeId.value && selectedPositionId.value) && !creating.value && !loading.value,
)
const canSend = computed(
  () => Boolean(activeSessionId.value && answer.value.trim()) && !sending.value && !stageUpdating.value,
)
const canAutoStart = computed(
  () => Boolean(activeSessionId.value) && !conversationStarted.value && !sending.value && !stageUpdating.value,
)
const canFinish = computed(
  () => Boolean(activeSessionId.value) && !finishing.value && !sending.value,
)
const primarySessionList = computed(() => sessions.value.filter((item) => item.status !== 'finished'))
const finishedSessionList = computed(() => sessions.value.filter((item) => item.status === 'finished'))
const reportSource = computed(() => {
  if (reportMarkdown.value || replay.value?.summaryReport) {
    return {
      title: activeSession.value?.targetPosition || '面试报告预览',
      report: reportMarkdown.value || replay.value?.summaryReport || '',
      sourceLabel: activeSession.value?.status === 'finished' ? '当前会话' : '最近可预览报告',
    }
  }

  const fallback = finishedSessionList.value[0]
  if (!fallback?.summaryReport) {
    return null
  }

  return {
    title: fallback.targetPosition || '最近已完成面试',
    report: fallback.summaryReport,
    sourceLabel: '已完成会话',
  }
})
const renderedReport = computed(() => renderMarkdown(reportSource.value?.report || ''))

function getErrorMessage(error: unknown) {
  return error instanceof Error ? error.message : '请求失败'
}

function setResumeDefaults(items: ResumeItem[]) {
  if (!selectedResumeId.value || !items.some((item) => item.id === selectedResumeId.value)) {
    selectedResumeId.value = items[0]?.id ?? null
  }
  uploadDisplayName.value = items.find((item) => item.id === selectedResumeId.value)?.fileName || '未选择任何文件'
}

function setPositionDefaults(items: PositionTemplate[]) {
  if (!selectedPositionId.value || !items.some((item) => item.id === selectedPositionId.value)) {
    selectedPositionId.value = items[0]?.id ?? null
  }
}

async function refreshSessionList() {
  sessions.value = await fetchInterviewSessions()
}

async function loadSession(sessionId: number, silent = false) {
  sessionLoading.value = true
  try {
    const detail = await fetchInterviewMessages(sessionId)
    replay.value = detail
    activeSessionId.value = sessionId
    reportMarkdown.value = detail.summaryReport || ''
  } catch (error) {
    if (!silent) {
      showNotice(getErrorMessage(error), 'error')
    }
  } finally {
    sessionLoading.value = false
  }
}

async function loadDashboard() {
  loading.value = true
  try {
    const [resumeList, positionList] = await Promise.all([fetchResumes(), fetchPositions(), refreshSessionList()])
    resumes.value = resumeList
    positions.value = positionList
    setResumeDefaults(resumeList)
    setPositionDefaults(positionList)

    if (activeSessionId.value && sessions.value.some((item) => item.sessionId === activeSessionId.value)) {
      await loadSession(activeSessionId.value, true)
    } else if (primarySessionList.value[0]) {
      await loadSession(primarySessionList.value[0].sessionId, true)
    } else if (sessions.value[0]) {
      await loadSession(sessions.value[0].sessionId, true)
    } else {
      replay.value = null
      activeSessionId.value = null
      reportMarkdown.value = ''
    }
  } catch (error) {
    showNotice(getErrorMessage(error), 'error')
  } finally {
    loading.value = false
  }
}

function openResumePicker() {
  if (!uploading.value) {
    uploadInput.value?.click()
  }
}

async function handleUpload(event: Event) {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  input.value = ''
  if (!file) {
    return
  }

  uploading.value = true
  try {
    const result = await uploadResume(file)
    const updated = await fetchResumes()
    resumes.value = updated
    selectedResumeId.value = result.resumeId
    uploadDisplayName.value = updated.find((item) => item.id === result.resumeId)?.fileName || file.name
    showNotice('简历已上传', 'success')
  } catch (error) {
    showNotice(getErrorMessage(error), 'error')
  } finally {
    uploading.value = false
  }
}

async function createNewInterview() {
  if (!selectedResumeId.value || !selectedPositionId.value) {
    showNotice('请选择简历和岗位', 'warning')
    return
  }

  creating.value = true
  try {
    const result = await startInterview({
      resumeId: selectedResumeId.value,
      positionId: selectedPositionId.value,
    })
    await refreshSessionList()
    await loadSession(result.sessionId, true)
    answer.value = ''
    reportMarkdown.value = ''
    showNotice('面试已创建', 'success')
  } catch (error) {
    showNotice(getErrorMessage(error), 'error')
  } finally {
    creating.value = false
  }
}

function appendMessage(message: InterviewMessageRecord) {
  if (!replay.value) {
    return
  }
  replay.value.messages = [...replay.value.messages, message]
}

function removeMessageById(id: number | null) {
  if (!replay.value || id == null) {
    return
  }
  replay.value.messages = replay.value.messages.filter((message) => message.id !== id)
}

function ensureAssistantPlaceholder(id: number) {
  if (!replay.value || replay.value.messages.some((message) => message.id === id)) {
    return
  }

  appendMessage({
    id,
    role: 'assistant',
    content: '',
    createdAt: new Date().toISOString(),
  })
}

function appendAssistantDelta(id: number, delta: string) {
  if (!replay.value) {
    return
  }

  const list = [...replay.value.messages]
  const target = list.find((message) => message.id === id)

  if (!target || target.role !== 'assistant') {
    list.push({
      id,
      role: 'assistant',
      content: delta,
      createdAt: new Date().toISOString(),
    })
  } else {
    target.content += delta
  }

  replay.value.messages = list
}

async function streamReply(content: string, autoStart = false) {
  if (!activeSessionId.value) {
    showNotice('请先创建或选择一场面试', 'warning')
    return false
  }

  if (!replay.value) {
    await loadSession(activeSessionId.value, true)
  }

  const optimisticUserId = autoStart ? null : Date.now()
  const assistantMessageId = Date.now() + 1

  if (!autoStart) {
    appendMessage({
      id: optimisticUserId!,
      role: 'user',
      content,
      createdAt: new Date().toISOString(),
    })
  }
  ensureAssistantPlaceholder(assistantMessageId)

  try {
    await streamInterviewChat(
      authStore.token,
      activeSessionId.value,
      { content },
      autoStart,
      {
        onChunk(chunk) {
          appendAssistantDelta(assistantMessageId, chunk)
        },
      },
    )
    await refreshSessionList()
    await loadSession(activeSessionId.value, true)
    showNotice(autoStart ? '面试官已开始提问' : '回答已发送', 'success')
    return true
  } catch (error) {
    removeMessageById(assistantMessageId)
    removeMessageById(optimisticUserId)
    const message = getErrorMessage(error)
    if (message.includes('登录已失效')) {
      authStore.clearSession()
      await router.replace('/login?reason=expired')
      return false
    }
    showNotice(message, 'error')
    return false
  }
}

async function handleAutoStart() {
  if (!canAutoStart.value) {
    return
  }
  sending.value = true
  try {
    await streamReply('', true)
  } finally {
    sending.value = false
  }
}

async function handleSend() {
  const content = answer.value.trim()
  if (!content) {
    showNotice('请输入回答内容', 'warning')
    return
  }

  sending.value = true
  try {
    const success = await streamReply(content, false)
    if (success) {
      answer.value = ''
    }
  } finally {
    sending.value = false
  }
}

async function handleAdvanceStage() {
  if (!activeSessionId.value || !nextStageName.value) {
    return
  }
  stageUpdating.value = true
  try {
    await changeInterviewStage(activeSessionId.value, { stageName: nextStageName.value })
    await refreshSessionList()
    await loadSession(activeSessionId.value, true)
    showNotice(`已进入${stageLabel(nextStageName.value)}阶段`, 'success')
  } catch (error) {
    showNotice(getErrorMessage(error), 'error')
  } finally {
    stageUpdating.value = false
  }
}

async function handleFinish() {
  if (!activeSessionId.value) {
    return
  }
  finishing.value = true
  try {
    const result = await finishInterview(activeSessionId.value)
    reportMarkdown.value = result.summaryReport || ''
    await refreshSessionList()
    const target = sessions.value.find((item) => item.sessionId === activeSessionId.value)
    if (target) {
      target.summaryReport = result.summaryReport
      target.status = result.status || 'finished'
    }
    if (replay.value) {
      replay.value.summaryReport = result.summaryReport || ''
    }
    if (activeSessionId.value) {
      await loadSession(activeSessionId.value, true)
    }
    showNotice('报告已生成', 'success')
  } catch (error) {
    showNotice(getErrorMessage(error), 'error')
  } finally {
    finishing.value = false
  }
}

function isStageComplete(stage: InterviewStageName) {
  const current = currentStage.value
  if (!current) {
    return false
  }
  return STAGE_ORDER.indexOf(stage) < STAGE_ORDER.indexOf(current)
}

function isStageCurrent(stage: InterviewStageName) {
  return currentStage.value === stage
}

onMounted(() => {
  void loadDashboard()
})
</script>

<template>
  <section class="page page--workspace">
    <div class="page__hero">
      <div class="page__hero-main">
        <p class="eyebrow">面试</p>
        <h2 class="page__title">主工作台</h2>
        <p class="page__lead">
          在同一屏完成面试准备、实时问答、历史复盘和报告预览。
        </p>
      </div>
    </div>

    <div class="insight-strip">
      <article class="insight-card">
        <p class="panel__eyebrow">当前简历</p>
        <h3 class="insight-card__value">{{ selectedResume?.fileName || '待选择' }}</h3>
        <p class="insight-card__meta">{{ resumes.length }} 份可用简历</p>
      </article>
      <article class="insight-card">
        <p class="panel__eyebrow">当前岗位</p>
        <h3 class="insight-card__value">{{ selectedPosition?.name || '待选择' }}</h3>
        <p class="insight-card__meta">{{ positions.length }} 个岗位模板</p>
      </article>
      <article class="insight-card">
        <p class="panel__eyebrow">会话状态</p>
        <h3 class="insight-card__value">{{ primarySessionList.length }} 进行中</h3>
        <p class="insight-card__meta">{{ finishedSessionList.length }} 场已完成</p>
      </article>
    </div>

    <div class="workspace-stack">
      <div class="workflow-grid">
        <ElCard class="ui-card panel panel--workspace panel--prep">
        <div class="panel__head">
          <div>
            <p class="panel__eyebrow">1. 准备</p>
            <h3 class="panel__title">简历与岗位</h3>
            <p class="panel__lead">选择简历与岗位，创建本场会话。</p>
          </div>
          <ElTag class="ui-badge" effect="light">{{ resumes.length }} 份简历</ElTag>
        </div>

        <div class="prep-layout">
          <div class="prep-layout__form field-stack">
            <div class="field">
              <span class="field__label">上传 PDF 简历</span>
              <div class="upload-field" :class="{ 'is-uploading': uploading }">
                <input
                  ref="uploadInput"
                  class="upload-field__native"
                  accept="application/pdf"
                  type="file"
                  @change="handleUpload"
                />
                <button
                  class="upload-field__button"
                  :disabled="uploading"
                  type="button"
                  @click="openResumePicker"
                >
                  {{ uploading ? '上传中' : '选择文件' }}
                </button>
                <span class="upload-field__name">{{ uploadDisplayName }}</span>
              </div>
            </div>

            <div class="field-grid">
              <label class="field">
                <span class="field__label">简历</span>
                <ElSelect v-model="selectedResumeId" class="ui-select" placeholder="请选择简历" size="large">
                  <ElOption
                    v-for="item in resumes"
                    :key="item.id"
                    :label="item.fileName"
                    :value="item.id"
                  />
                </ElSelect>
              </label>

              <label class="field">
                <span class="field__label">岗位</span>
                <ElSelect v-model="selectedPositionId" class="ui-select" placeholder="请选择岗位" size="large">
                  <ElOption
                    v-for="item in positions"
                    :key="item.id"
                    :label="item.name"
                    :value="item.id"
                  />
                </ElSelect>
              </label>
            </div>

            <div class="prep-summary-panel">
              <section class="prep-summary-panel__section">
                <p class="panel__eyebrow">本场配置</p>
                <h4 class="detail-card__title prep-summary-panel__title">{{ selectedResume?.fileName || '未选择简历' }}</h4>
                <p class="detail-card__meta">{{ selectedPosition?.name || '未选择岗位' }}</p>
              </section>
              <section class="prep-summary-panel__section">
                <p class="panel__eyebrow">创建结果</p>
                <h4 class="detail-card__title prep-summary-panel__title">{{ activeSessionId ? `会话 #${activeSessionId}` : '尚未创建' }}</h4>
                <p class="detail-card__meta">{{ currentStage ? `${stageLabel(currentStage)}阶段` : '等待启动' }}</p>
              </section>
            </div>

            <div class="button-row panel__footer-actions prep-layout__form-actions">
              <ElButton
                class="ui-button ui-button--primary"
                :disabled="!canCreate"
                :loading="creating || loading"
                size="large"
                type="primary"
                @click="createNewInterview"
              >
                创建面试
              </ElButton>
            </div>
          </div>
        </div>
        </ElCard>

        <ElCard class="ui-card panel panel--workspace panel--conversation">
        <div class="panel__head">
          <div>
            <p class="panel__eyebrow">2. 实时面试</p>
            <h3 class="panel__title">阶段问答区</h3>
            <p class="panel__lead">按阶段推进问答，并在当前会话内实时生成内容。</p>
          </div>
          <div class="panel__actions">
            <ElTag class="ui-badge" effect="light">
              {{ activeSessionId ? `会话 #${activeSessionId}` : '未创建' }}
            </ElTag>
            <ElTag class="ui-badge" effect="light">{{ stageLabel(currentStage) }}</ElTag>
          </div>
        </div>

        <div class="stage-rail">
          <button
            v-for="stage in STAGE_ORDER"
            :key="stage"
            :class="[
              'stage-rail__item',
              { 'is-active': isStageCurrent(stage), 'is-complete': isStageComplete(stage) },
            ]"
            type="button"
            disabled
          >
            <span class="stage-rail__index">{{ STAGE_ORDER.indexOf(stage) + 1 }}</span>
            <span class="stage-rail__label">{{ stageLabel(stage) }}</span>
          </button>
        </div>

        <div class="conversation__toolbar conversation__toolbar--dense">
          <ElButton
            class="ui-button ui-button--secondary"
            :disabled="!canAutoStart"
            :loading="sending"
            size="large"
            @click="handleAutoStart"
          >
            AI 开始提问
          </ElButton>
          <ElButton
            class="ui-button ui-button--secondary"
            :disabled="!nextStageName || stageUpdating || sending"
            :loading="stageUpdating"
            size="large"
            @click="handleAdvanceStage"
          >
            {{ nextStageName ? `进入${stageLabel(nextStageName)}阶段` : '阶段已完成' }}
          </ElButton>
        </div>

        <div class="conversation">
          <div v-if="!activeSessionId && !sessionLoading" class="conversation__empty-state">
            <p class="conversation__empty-copy">创建面试后开始问答。</p>
          </div>

          <template v-else>
            <article
              v-for="message in messages"
              :key="`${message.id}-${message.createdAt}`"
              :class="['message-bubble', `message-bubble--${message.role as InterviewMessageRole}`]"
            >
              <div class="message-bubble__head">
                <ElTag class="ui-badge" effect="light">
                  {{ message.role === 'system' ? '系统' : message.role === 'assistant' ? '面试官' : '我' }}
                </ElTag>
              </div>
              <p class="message-bubble__content">{{ message.content || '...' }}</p>
            </article>
          </template>
        </div>

        <label class="field">
          <span class="field__label">你的回答</span>
          <ElInput
            v-model="answer"
            class="ui-input ui-textarea conversation-answer"
            :disabled="!activeSessionId"
            :rows="3"
            placeholder="输入回答后发送"
            resize="none"
            type="textarea"
          />
        </label>

        <div class="button-row panel__footer-actions">
          <ElButton
            class="ui-button ui-button--primary"
            :disabled="!canSend"
            :loading="sending"
            size="large"
            type="primary"
            @click="handleSend"
          >
            发送回答
          </ElButton>
          <ElButton
            class="ui-button ui-button--secondary"
            :disabled="!canFinish"
            :loading="finishing"
            size="large"
            @click="handleFinish"
          >
            生成报告
          </ElButton>
        </div>
        </ElCard>
      </div>

      <div class="history-grid">
        <ElCard class="ui-card panel panel--workspace history-panel">
        <div class="panel__head">
          <div>
            <p class="panel__eyebrow">3. 历史与结果</p>
            <h3 class="panel__title">会话清单</h3>
            <p class="panel__lead">按进行中与已完成分组查看会话，并快速进入回放。</p>
          </div>
          <ElTag class="ui-badge" effect="light">{{ sessions.length }} 场会话</ElTag>
        </div>

        <div class="history-section">
          <div class="history-section__head">
            <p class="panel__eyebrow">进行中 / 未完成</p>
            <span class="field__hint">{{ primarySessionList.length }} 场</span>
          </div>
          <div v-if="primarySessionList.length" class="session-list">
            <article
              v-for="item in primarySessionList"
              :key="item.sessionId"
              :class="['session-item', { 'is-active': item.sessionId === activeSessionId }]"
            >
              <button class="session-item__body" type="button" @click="loadSession(item.sessionId)">
                <div class="session-item__head">
                  <h4 class="session-item__title">{{ item.targetPosition || '未命名岗位' }}</h4>
                </div>
                <p class="session-item__meta">
                  {{ item.createdAt ? new Date(item.createdAt).toLocaleString() : '未知时间' }}
                </p>
                <p class="session-item__summary">
                  {{ item.llmProvider || 'deepseek' }} / {{ item.llmModel || 'default' }}
                </p>
              </button>
              <div class="session-item__side">
                <ElTag class="ui-badge ui-badge--compact" effect="light">{{ stageLabel(item.currentStage) }}</ElTag>
                <ElButton
                  class="ui-button ui-button--secondary ui-button--compact"
                  size="large"
                  @click="router.push(`/interview/replay/${item.sessionId}`)"
                >
                  回放
                </ElButton>
              </div>
            </article>
          </div>
          <div v-else class="empty-state">还没有进行中的会话。</div>
        </div>

        <div class="history-section">
          <div class="history-section__head">
            <p class="panel__eyebrow">已完成</p>
            <span class="field__hint">{{ finishedSessionList.length }} 场</span>
          </div>
          <div v-if="finishedSessionList.length" class="session-list">
            <article
              v-for="item in finishedSessionList"
              :key="item.sessionId"
              :class="['session-item', { 'is-active': item.sessionId === activeSessionId }]"
            >
              <button class="session-item__body" type="button" @click="loadSession(item.sessionId)">
                <div class="session-item__head">
                  <h4 class="session-item__title">{{ item.targetPosition || '未命名岗位' }}</h4>
                </div>
                <p class="session-item__meta">
                  {{ item.createdAt ? new Date(item.createdAt).toLocaleString() : '未知时间' }}
                </p>
                <p class="session-item__summary">{{ item.summaryReport ? '包含报告与评分' : '暂无报告摘要' }}</p>
              </button>
              <div class="session-item__side">
                <ElTag class="ui-badge ui-badge--compact" effect="light">已完成</ElTag>
                <ElButton
                  class="ui-button ui-button--secondary ui-button--compact"
                  size="large"
                  @click="router.push(`/interview/replay/${item.sessionId}`)"
                >
                  回放
                </ElButton>
              </div>
            </article>
          </div>
          <div v-else class="empty-state">还没有已完成的会话。</div>
          </div>
        </ElCard>

        <ElCard class="ui-card panel panel--workspace report-panel">
        <div class="panel__head">
          <div>
            <p class="panel__eyebrow">报告预览</p>
            <h3 class="panel__title">{{ reportSource?.title || '面试报告预览' }}</h3>
            <p class="panel__lead">查看当前或最近完成的面试报告。</p>
          </div>
          <div class="panel__actions">
            <ElTag v-if="reportSource" class="ui-badge" effect="light">{{ reportSource.sourceLabel }}</ElTag>
          </div>
        </div>

        <div v-if="renderedReport" class="markdown-surface markdown-surface--report">
          <div class="markdown-body" v-html="renderedReport" />
        </div>
        <div v-else class="report-panel__empty">
          完成面试后显示最近报告。
        </div>
        </ElCard>
      </div>
    </div>
  </section>
</template>
