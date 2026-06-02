<script setup lang="ts">
import { computed, ref } from 'vue'
import { ElDropdown, ElDropdownMenu, ElDropdownItem, ElInput, ElButton } from 'element-plus'
import { useRouter } from 'vue-router'
import type { ResumeItem, PositionTemplate } from '../../api/contracts'
import { usePopperMatchTrigger } from '../../composables/usePopperMatchTrigger'

const props = defineProps<{
  isCentered: boolean
  activeSessionId?: number | null
  resumes: ResumeItem[]
  positions: PositionTemplate[]
  selectedResumeId: number | null
  selectedPositionId: number | null
  modelValue: string
  uploading: boolean
  uploadDisplayName: string
  sending: boolean
  creating: boolean
  llmProvider?: string
  llmModel?: string
}>()

const emit = defineEmits<{
  (e: 'update:modelValue', value: string): void
  (e: 'update:selectedResumeId', value: number): void
  (e: 'update:selectedPositionId', value: number): void
  (e: 'upload', file: File): void
  (e: 'start', jdText?: string): void
  (e: 'send'): void
  (e: 'toggle-voice'): void
}>()

const router = useRouter()
const fileInput = ref<HTMLInputElement | null>(null)
const showJdInput = ref(false)
const jdText = ref('')

const resumeDropdown = usePopperMatchTrigger()
const positionDropdown = usePopperMatchTrigger()

const canStart = computed(() => !!props.selectedResumeId && !!props.selectedPositionId && !props.creating)
const canSend = computed(() => !!props.modelValue.trim() && !props.sending)

const selectedResumeName = computed(() => {
  if (!props.selectedResumeId) return '选择'
  return props.resumes.find(r => r.id === props.selectedResumeId)?.fileName || '选择'
})

const selectedPositionName = computed(() => {
  if (!props.selectedPositionId) return '选择'
  return props.positions.find(p => p.id === props.selectedPositionId)?.name || '选择'
})

function handleFileChange(event: Event) {
  const target = event.target as HTMLInputElement
  const file = target.files?.[0]
  if (file) {
    emit('upload', file)
  }
  target.value = ''
}

function triggerUpload() {
  if (!props.uploading) {
    fileInput.value?.click()
  }
}

function navigateToLlm() {
  router.push('/settings/llm')
}
</script>

<template>
  <div :class="['interview-composer', { 'is-centered': isCentered, 'is-bottom': !isCentered }]">
    <div class="interview-composer__inner">
      <!-- Text Input Area -->
      <div class="composer-input-area">
        <ElInput
          :model-value="modelValue"
          @update:model-value="(v) => emit('update:modelValue', v)"
          type="textarea"
          :rows="3"
          resize="none"
          :placeholder="activeSessionId ? '输入回答...' : '请先选择简历与岗位，然后点击「开始面试」'"
          :disabled="!activeSessionId"
          class="composer-textarea"
          @keydown.ctrl.enter="canSend && emit('send')"
          @keydown.meta.enter="canSend && emit('send')"
        />
        <transition name="fade">
          <div v-if="!activeSessionId && showJdInput" class="composer-jd-area">
            <ElInput
              v-model="jdText"
              type="textarea"
              :rows="4"
              resize="none"
              placeholder="粘贴目标岗位职责或 JD 文本，系统将通过 RAG 算法进行智能分块和背景匹配发问..."
              class="jd-textarea"
            />
          </div>
        </transition>
      </div>

      <div class="composer-actions">
        <div class="composer-actions__left">
          <div class="composer-toolbar">
            <template v-if="!activeSessionId">
              <!-- Resume Picker -->
              <ElDropdown popper-class="custom-dropdown-popper" :popper-style="resumeDropdown.popperStyle.value" :ref="(el: any) => resumeDropdown.bind(el?.$el?.querySelector?.('.toolbar-item') ?? el?.$el ?? null)" @command="(v: number | string) => { if (v === 'upload') { triggerUpload() } else { emit('update:selectedResumeId', v as number) } }" trigger="click">
                <button class="toolbar-item" type="button">
                  <span class="toolbar-item__label">简历:</span>
                  <span class="toolbar-item__value">{{ selectedResumeName }}</span>
                </button>
                <template #dropdown>
                  <ElDropdownMenu class="custom-dropdown-menu">
                    <ElDropdownItem v-for="r in resumes" :key="r.id" :command="r.id">{{ r.fileName }}</ElDropdownItem>
                    <ElDropdownItem divided command="upload" class="upload-action">{{ uploading ? '上传中...' : '+ 上传 PDF' }}</ElDropdownItem>
                  </ElDropdownMenu>
                </template>
              </ElDropdown>
              
              <!-- Hidden File Input for Resume -->
              <input
                type="file"
                ref="fileInput"
                accept="application/pdf"
                style="display: none"
                @change="handleFileChange"
              />

              <!-- Position Picker -->
              <ElDropdown popper-class="custom-dropdown-popper" :popper-style="positionDropdown.popperStyle.value" :ref="(el: any) => positionDropdown.bind(el?.$el?.querySelector?.('.toolbar-item') ?? el?.$el ?? null)" @command="(v: number) => emit('update:selectedPositionId', v)" trigger="click">
                <button class="toolbar-item" type="button">
                  <span class="toolbar-item__label">岗位:</span>
                  <span class="toolbar-item__value">{{ selectedPositionName }}</span>
                </button>
                <template #dropdown>
                  <ElDropdownMenu class="custom-dropdown-menu">
                    <ElDropdownItem v-for="p in positions" :key="p.id" :command="p.id">{{ p.name }}</ElDropdownItem>
                  </ElDropdownMenu>
                </template>
              </ElDropdown>

              <!-- JD Toggle -->
              <button class="toolbar-item" type="button" @click="showJdInput = !showJdInput" :class="{ 'is-active': showJdInput }">
                <span class="toolbar-item__label">JD 匹配:</span>
                <span class="toolbar-item__value">{{ showJdInput ? '已开启' : '未开启' }}</span>
              </button>
            </template>
            <template v-else>
              <!-- Voice Toggle -->
              <button class="toolbar-item" type="button" @click="emit('toggle-voice')">
                <span class="toolbar-item__label">互动:</span>
                <span class="toolbar-item__value" style="color: var(--color-brand);">切换到语音</span>
              </button>
            </template>

            <!-- Model Info -->
            <button class="toolbar-item" @click="navigateToLlm" title="前往 LLM 配置" type="button">
              <span class="toolbar-item__label">模型:</span>
              <span class="toolbar-item__value">{{ llmProvider || '未配置' }} / {{ llmModel || 'default' }}</span>
            </button>
          </div>
        </div>
        
        <div class="composer-actions__right">

          <ElButton
            v-if="!activeSessionId"
            type="primary"
            class="ui-button ui-button--primary ui-button--compact composer-btn"
            :disabled="!canStart"
            :loading="creating"
            @click="emit('start', showJdInput ? jdText : undefined)"
          >
            开始面试
          </ElButton>
          <ElButton
            v-else
            type="primary"
            class="ui-button ui-button--primary ui-button--compact composer-btn"
            :disabled="!canSend"
            :loading="sending"
            @click="emit('send')"
          >
            发送
          </ElButton>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.interview-composer {
  transition: all 0.3s ease;
  width: 100%;
}
.interview-composer.is-centered {
  max-width: 800px;
  margin: 0 auto;
}
.interview-composer.is-bottom {
  max-width: 720px;
  margin: 0 auto;
  position: relative;
}
.interview-composer__inner {
  background: var(--color-surface);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-xl);
  box-shadow: 0 4px 20px rgba(0, 0, 0, 0.04), 0 0 0 1px var(--color-ring);
  padding: var(--spacing-md);
  display: flex;
  flex-direction: column;
  gap: var(--spacing-sm);
}
.interview-composer.is-centered .interview-composer__inner {
  padding: var(--spacing-lg);
  gap: var(--spacing-md);
}
.composer-textarea :deep(.el-textarea__inner) {
  border: none;
  background: transparent;
  padding: var(--spacing-sm) var(--spacing-xs);
  box-shadow: none;
  font-size: 15px;
  color: var(--color-text-primary);
}
.composer-textarea :deep(.el-textarea__inner:focus) {
  box-shadow: none;
}
.composer-textarea :deep(.el-textarea__inner:disabled) {
  background: transparent;
  cursor: default;
  color: var(--color-text-tertiary);
  -webkit-text-fill-color: var(--color-text-tertiary);
}
.composer-actions {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.composer-actions__left {
  display: flex;
  align-items: center;
}
.composer-actions__right {
  display: flex;
  align-items: center;
  gap: var(--spacing-md);
}
.composer-actions__hint {
  font-size: 12px;
  color: var(--color-text-tertiary);
  padding-left: var(--spacing-xs);
}
.composer-toolbar {
  display: flex;
  align-items: center;
  gap: var(--spacing-sm);
}
.toolbar-item {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  background: transparent;
  border: none;
  font-size: 13px;
  padding: var(--spacing-xs) var(--spacing-sm);
  border-radius: var(--radius-md);
  cursor: pointer;
  transition: background-color 0.2s;
  text-decoration: none;
  outline: none;
}
.toolbar-item:hover, .toolbar-item:focus-within {
  background-color: var(--color-surface-hover);
}
.toolbar-item:focus-visible {
  outline: 2px solid var(--color-focus);
  outline-offset: -2px;
}
.toolbar-item__label {
  color: var(--color-text-tertiary);
  white-space: nowrap;
  pointer-events: none;
}
.toolbar-item__value {
  color: var(--color-text-primary);
  font-weight: 500;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  max-width: 140px;
}
.composer-btn {
  border-radius: var(--radius-lg);
  padding: 0 var(--spacing-lg);
  flex-shrink: 0;
}
.composer-jd-area {
  border-top: 1px dashed var(--color-border);
  padding-top: var(--spacing-sm);
  margin-top: var(--spacing-xs);
}
.jd-textarea :deep(.el-textarea__inner) {
  border: none;
  background: transparent;
  padding: var(--spacing-sm) var(--spacing-xs);
  box-shadow: none;
  font-size: 14px;
  color: var(--color-text-primary);
}
.jd-textarea :deep(.el-textarea__inner:focus) {
  box-shadow: none;
}
.toolbar-item.is-active {
  background-color: var(--color-surface-hover);
}
.toolbar-item.is-active .toolbar-item__value {
  color: var(--color-brand);
}

/* Fade transition */
.fade-enter-active,
.fade-leave-active {
  transition: opacity 0.2s ease, transform 0.2s ease;
}
.fade-enter-from,
.fade-leave-to {
  opacity: 0;
  transform: translateY(-8px);
}
</style>
<style>
.custom-dropdown-menu .upload-action {
  color: var(--color-brand);
  text-align: center;
  justify-content: center;
  font-weight: 500;
}
.custom-dropdown-menu .upload-action:hover {
  background-color: var(--color-surface-hover) !important;
  color: var(--color-brand) !important;
}
</style>
