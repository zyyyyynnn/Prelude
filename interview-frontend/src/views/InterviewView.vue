<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import {
  ElAlert,
  ElButton,
  ElCard,
  ElDivider,
  ElInput,
  ElOption,
  ElSelect,
  ElTag,
} from 'element-plus'
import {
  fetchPositions,
  fetchResumes,
  finishInterview as finishInterviewRequest,
  startInterview as startInterviewRequest,
  uploadResume as uploadResumeRequest,
} from '../api/auth'
import { ApiClientError } from '../api/http'
import { useAuthStore } from '../stores/auth'
import { useRouter } from 'vue-router'

type ConversationMessage = {
  role: 'user' | 'assistant'
  content: string
}

const authStore = useAuthStore()
const router = useRouter()
const apiBaseUrl = import.meta.env.VITE_API_BASE_URL || '/api'

const loading = ref(false)
const uploading = ref(false)
const sending = ref(false)
const finishing = ref(false)

const statusMessage = ref('')
const statusType = ref<'success' | 'warning' | 'error' | 'info'>('info')

const resumes = ref<{ id: number; fileName: string }[]>([])
const positions = ref<{ id: number; name: string }[]>([])
const selectedResumeId = ref<number | null>(null)
const selectedPositionId = ref<number | null>(null)
const sessionId = ref<number | null>(null)
const answer = ref('')
const conversation = ref<ConversationMessage[]>([])
const report = ref('')

const canChat = computed(() => Boolean(sessionId.value && !sending.value && !finishing.value))
const canStart = computed(() => Boolean(selectedResumeId.value && selectedPositionId.value))
const canFinish = computed(() => Boolean(sessionId.value && !finishing.value))

function setStatus(message: string, type: typeof statusType.value = 'info') {
  statusMessage.value = message
  statusType.value = type
}

function getErrorMessage(error: unknown) {
  if (error instanceof Error) {
    return error.message
  }

  return '请求失败'
}

async function loadBaseData() {
  loading.value = true
  try {
    const [resumeList, positionList] = await Promise.all([fetchResumes(), fetchPositions()])
    resumes.value = resumeList
    positions.value = positionList
    selectedResumeId.value = resumeList[0]?.id ?? null
    selectedPositionId.value = positionList[0]?.id ?? null
    setStatus('数据已加载', 'success')
  } catch (error) {
    setStatus(getErrorMessage(error), 'error')
  } finally {
    loading.value = false
  }
}

function appendAssistantDelta(delta: string) {
  const lastMessage = conversation.value[conversation.value.length - 1]
  if (!lastMessage || lastMessage.role !== 'assistant') {
    conversation.value.push({ role: 'assistant', content: delta })
    return
  }

  lastMessage.content += delta
}

function parseSseEvent(rawEvent: string) {
  const lines = rawEvent.split('\n')
  const eventName = lines.find((line) => line.startsWith('event:'))?.slice(6).trim() || 'message'
  const data = lines
    .filter((line) => line.startsWith('data:'))
    .map((line) => line.slice(5).trimStart())
    .join('\n')

  return { eventName, data }
}

async function sendAnswer() {
  if (!sessionId.value || !answer.value.trim()) {
    setStatus('请先创建面试并输入回答', 'warning')
    return
  }

  const userMessage = answer.value.trim()
  answer.value = ''
  conversation.value.push({ role: 'user', content: userMessage })
  conversation.value.push({ role: 'assistant', content: '' })
  sending.value = true
  setStatus('正在发送回答', 'info')

  try {
    const response = await fetch(`${apiBaseUrl}/interview/${sessionId.value}/chat`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        Authorization: `Bearer ${authStore.token}`,
      },
      body: JSON.stringify({ content: userMessage }),
    })

    if (response.status === 401) {
      authStore.clearSession()
      await router.replace('/login?reason=expired')
      return
    }

    if (!response.ok || !response.body) {
      throw new Error('流式接口请求失败')
    }

    const reader = response.body.getReader()
    const decoder = new TextDecoder()
    let buffer = ''

    while (true) {
      const { done, value } = await reader.read()
      if (done) {
        break
      }

      buffer += decoder.decode(value, { stream: true }).replace(/\r/g, '')

      let boundary = buffer.indexOf('\n\n')
      while (boundary !== -1) {
        const rawEvent = buffer.slice(0, boundary).trim()
        buffer = buffer.slice(boundary + 2)
        if (rawEvent) {
          const { eventName, data } = parseSseEvent(rawEvent)
          if (eventName === 'error') {
            throw new Error(data || '流式返回错误')
          }
          appendAssistantDelta(data)
        }
        boundary = buffer.indexOf('\n\n')
      }
    }

    if (buffer.trim()) {
      const { eventName, data } = parseSseEvent(buffer.trim())
      if (eventName === 'error') {
        throw new Error(data || '流式返回错误')
      }
      appendAssistantDelta(data)
    }

    setStatus('回答已发送', 'success')
  } catch (error) {
    const message = error instanceof ApiClientError ? error.message : getErrorMessage(error)
    setStatus(message, 'error')
  } finally {
    sending.value = false
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
  setStatus('正在上传简历', 'info')

  try {
    const result = await uploadResumeRequest(file)
    resumes.value = [
      {
        id: result.resumeId,
        fileName: file.name,
      },
      ...resumes.value.filter((item) => item.id !== result.resumeId),
    ]
    selectedResumeId.value = result.resumeId
    setStatus('简历已上传', 'success')
  } catch (error) {
    setStatus(getErrorMessage(error), 'error')
  } finally {
    uploading.value = false
  }
}

async function createInterview() {
  if (!selectedResumeId.value || !selectedPositionId.value) {
    setStatus('请选择简历和岗位', 'warning')
    return
  }

  loading.value = true
  setStatus('正在创建面试', 'info')

  try {
    const result = await startInterviewRequest({
      resumeId: selectedResumeId.value,
      positionId: selectedPositionId.value,
    })
    sessionId.value = result.sessionId
    conversation.value = []
    report.value = ''
    answer.value = ''
    setStatus(`面试已创建：${result.sessionId}`, 'success')
  } catch (error) {
    setStatus(getErrorMessage(error), 'error')
  } finally {
    loading.value = false
  }
}

async function finishInterview() {
  if (!sessionId.value) {
    setStatus('请先创建面试', 'warning')
    return
  }

  finishing.value = true
  setStatus('正在生成报告', 'info')

  try {
    const result = await finishInterviewRequest(sessionId.value)
    report.value = result.summaryReport
    setStatus('报告已生成', 'success')
  } catch (error) {
    setStatus(getErrorMessage(error), 'error')
  } finally {
    finishing.value = false
  }
}

onMounted(() => {
  void loadBaseData()
})
</script>

<template>
  <section class="page">
    <div class="page__header">
      <p class="eyebrow">面试</p>
      <h2 class="page__title">主工作台</h2>
      <p class="page__lead">上传简历、选择岗位、创建面试并进行流式对话。</p>
    </div>

    <ElAlert
      v-if="statusMessage"
      class="status-banner"
      :closable="false"
      :title="statusMessage"
      :type="statusType"
    />

    <div class="page__grid">
      <ElCard class="ui-card panel">
        <div class="panel__head">
          <div>
            <p class="panel__eyebrow">1. 准备</p>
            <h3 class="panel__title">简历与岗位</h3>
          </div>
          <ElTag class="ui-badge" effect="light">基础流程</ElTag>
        </div>

        <div class="field-stack">
          <label class="field">
            <span class="field__label">上传 PDF 简历</span>
            <input
              class="ui-file-input"
              :disabled="uploading"
              accept="application/pdf"
              type="file"
              @change="handleUpload"
            />
          </label>

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

        <div class="button-row">
          <ElButton
            class="ui-button ui-button--primary"
            :disabled="!canStart"
            :loading="loading"
            size="large"
            type="primary"
            @click="createInterview"
          >
            创建面试
          </ElButton>
        </div>
      </ElCard>

      <ElCard class="ui-card panel">
        <div class="panel__head">
          <div>
            <p class="panel__eyebrow">2. 对话</p>
            <h3 class="panel__title">流式面试会话</h3>
          </div>
          <ElTag class="ui-badge" effect="light">
            {{ sessionId ? `会话 #${sessionId}` : '未创建' }}
          </ElTag>
        </div>

        <div class="conversation">
          <div v-if="conversation.length === 0" class="empty-state">
            先创建面试，然后发送第一条回答。
          </div>

          <article
            v-for="(message, index) in conversation"
            :key="`${message.role}-${index}`"
            :class="['message-bubble', `message-bubble--${message.role}`]"
          >
            <div class="message-bubble__head">
              <ElTag class="ui-badge" effect="light">
                {{ message.role === 'user' ? '我' : '面试官' }}
              </ElTag>
            </div>
            <p class="message-bubble__content">{{ message.content || '...' }}</p>
          </article>
        </div>

        <ElDivider />

        <label class="field">
          <span class="field__label">你的回答</span>
          <ElInput
            v-model="answer"
            class="ui-input ui-textarea"
            :disabled="!sessionId"
            :rows="6"
            placeholder="输入回答后发送"
            resize="none"
            type="textarea"
          />
        </label>

        <div class="button-row">
          <ElButton
            class="ui-button ui-button--primary"
            :disabled="!canChat"
            :loading="sending"
            size="large"
            type="primary"
            @click="sendAnswer"
          >
            发送回答
          </ElButton>
          <ElButton
            class="ui-button ui-button--secondary"
            :disabled="!canFinish"
            :loading="finishing"
            size="large"
            @click="finishInterview"
          >
            生成报告
          </ElButton>
        </div>
      </ElCard>

      <ElCard class="ui-card panel panel--wide">
        <div class="panel__head">
          <div>
            <p class="panel__eyebrow">3. 结果</p>
            <h3 class="panel__title">面试报告</h3>
          </div>
          <ElTag class="ui-badge" effect="light">Markdown</ElTag>
        </div>

        <pre v-if="report" class="report-surface">{{ report }}</pre>
        <div v-else class="empty-state">生成完成后在这里查看报告内容。</div>
      </ElCard>
    </div>
  </section>
</template>
