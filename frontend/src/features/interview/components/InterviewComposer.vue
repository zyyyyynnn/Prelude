<script setup lang="ts">
import { computed, ref, toRef } from 'vue'
import type { ResumeItem } from '@/features/resume'
import type { PositionTemplate } from '../model/types'
import { Button } from '@/shared/ui/button'
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuTrigger,
  DropdownMenuSeparator,
} from '@/shared/ui/dropdown-menu'
import {
  Tooltip,
  TooltipContent,
  TooltipProvider,
  TooltipTrigger,
  TooltipText,
} from '@/shared/ui/tooltip'
import { FileText, Briefcase, FileSearch, Terminal } from '@lucide/vue'
import { cn } from '@/shared/lib/utils'
import { dropdownTriggerVariants } from '@/shared/ui/shared-dropdown'
import { useComposerVoice } from '../composables/useComposerVoice'
import { useComposerModelOptions } from '../composables/useComposerModelOptions'
import ComposerText from './ComposerText.vue'
import ComposerVoice from './ComposerVoice.vue'

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
  // 语音及 JD 绑定
  isVoiceMode?: boolean
  voiceStatus?: string
  incomingAudio?: string
  jdText?: string
  disabled?: boolean
}>()

const emit = defineEmits<{
  (e: 'update:modelValue', value: string): void
  (e: 'update:selectedResumeId', value: number): void
  (e: 'update:selectedPositionId', value: number): void
  (e: 'upload', file: File): void
  (e: 'start', jdText?: string, llmModel?: string): void
  (e: 'send'): void
  // 语音新增
  (e: 'update:isVoiceMode', value: boolean): void
  (e: 'voice-audio-chunk', chunk: ArrayBuffer): void
  (e: 'voice-start-recording'): void
  (e: 'voice-stop-recording'): void
  (e: 'voice-play-status', status: 'playing' | 'idle'): void
  (e: 'open-global-settings', tab: 'profile' | 'theme' | 'llm'): void
}>()

const fileInput = ref<HTMLInputElement | null>(null)
const showJdInput = ref(false)
const localJdText = ref('')
const { providerModels, currentProviderName, selectedComposerModel, canSelectModel } =
  useComposerModelOptions({
    activeSessionId: toRef(props, 'activeSessionId'),
    llmProvider: toRef(props, 'llmProvider'),
    llmModel: toRef(props, 'llmModel'),
  })

const canStart = computed(
  () => !!props.selectedResumeId && !!props.selectedPositionId && !props.creating,
)
const canSend = computed(() => !!props.modelValue.trim() && !props.sending)

const selectedResumeName = computed(() => {
  if (!props.selectedResumeId) return '选择'
  return props.resumes.find((r) => r.id === props.selectedResumeId)?.fileName || '选择'
})

const selectedPositionName = computed(() => {
  if (!props.selectedPositionId) return '选择'
  return props.positions.find((p) => p.id === props.selectedPositionId)?.name || '选择'
})

const modelDisplay = computed(() => {
  const model = props.activeSessionId
    ? selectedComposerModel.value || props.llmModel
    : selectedComposerModel.value
  const provider = props.activeSessionId ? props.llmProvider : currentProviderName.value
  return `${provider || '未配置'} / ${model || '默认'}`
})

function toggleJdInput() {
  showJdInput.value = !showJdInput.value
}

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
  emit('open-global-settings', 'llm')
}

const { setCanvasRef, displayStatus, isRecording, startRecording, stopRecording } =
  useComposerVoice({
    incomingAudio: toRef(props, 'incomingAudio'),
    isVoiceMode: toRef(props, 'isVoiceMode'),
    voiceStatus: toRef(props, 'voiceStatus'),
    onAudioChunk: (chunk) => emit('voice-audio-chunk', chunk),
    onStartRecording: () => emit('voice-start-recording'),
    onStopRecording: () => emit('voice-stop-recording'),
    onPlayStatus: (status) => emit('voice-play-status', status),
  })
</script>

<template>
  <div
    :class="[
      'interview-composer',
      { 'is-centered': isCentered, 'is-bottom': !isCentered, 'is-disabled': disabled },
    ]"
  >
    <div class="interview-composer__inner">
      <!-- Input Area / Voice Wave Area -->
      <div class="composer-input-area">
        <transition name="mode-switch" mode="out-in">
          <ComposerText
            v-if="!isVoiceMode"
            :model-value="modelValue"
            :active-session-id="activeSessionId"
            :disabled="disabled"
            :can-send="canSend"
            :show-jd-input="showJdInput"
            :jd-text="localJdText"
            @update:model-value="(value) => emit('update:modelValue', value)"
            @update:jd-text="localJdText = $event"
            @send="emit('send')"
          />
          <ComposerVoice
            v-else
            :voice-status="voiceStatus"
            :display-status="displayStatus"
            @canvas-ref="setCanvasRef"
          />
        </transition>
      </div>

      <!-- Actions Toolbar -->
      <div class="composer-actions">
        <div class="composer-actions__left">
          <TooltipProvider>
            <div class="composer-toolbar">
              <template v-if="!activeSessionId">
                <!-- Resume Picker -->
                <DropdownMenu>
                  <DropdownMenuTrigger as-child>
                    <button
                      :class="
                        cn(
                          dropdownTriggerVariants({ size: 'compact' }),
                          'composer-toolbar-control composer-toolbar-select cursor-pointer overflow-hidden !border-transparent bg-transparent hover:bg-accent hover:text-accent-foreground',
                        )
                      "
                    >
                      <Tooltip>
                        <TooltipTrigger as-child>
                          <div
                            class="flex h-full w-full items-center gap-[var(--spacing-xs)] overflow-hidden"
                          >
                            <FileText class="w-3.5 h-3.5 shrink-0 opacity-70" />
                            <span class="min-w-0 flex-1 truncate font-medium text-foreground">{{
                              selectedResumeName
                            }}</span>
                          </div>
                        </TooltipTrigger>
                        <TooltipContent side="top" :side-offset="8" class="z-[110] text-xs">
                          {{ selectedResumeName }}
                        </TooltipContent>
                      </Tooltip>
                      <svg
                        xmlns="http://www.w3.org/2000/svg"
                        width="16"
                        height="16"
                        viewBox="0 0 24 24"
                        fill="none"
                        stroke="currentColor"
                        stroke-width="2"
                        stroke-linecap="round"
                        stroke-linejoin="round"
                        class="shrink-0 opacity-50 ml-1"
                      >
                        <path d="m6 9 6 6 6-6" />
                      </svg>
                    </button>
                  </DropdownMenuTrigger>
                  <DropdownMenuContent
                    class="min-w-[var(--reka-dropdown-menu-trigger-width)] w-[var(--composer-toolbar-select-inline-size)]"
                    align="start"
                  >
                    <DropdownMenuItem
                      v-for="r in resumes"
                      :key="r.id"
                      size="compact"
                      @click="emit('update:selectedResumeId', r.id)"
                      class="cursor-pointer overflow-hidden whitespace-nowrap"
                    >
                      <TooltipText class="min-w-0 flex-1 text-foreground" :text="r.fileName" />
                    </DropdownMenuItem>
                    <DropdownMenuSeparator />
                    <DropdownMenuItem
                      size="compact"
                      @click="triggerUpload"
                      class="justify-center cursor-pointer whitespace-nowrap text-primary font-medium"
                    >
                      {{ uploading ? '上传中...' : '+ 上传 PDF' }}
                    </DropdownMenuItem>
                  </DropdownMenuContent>
                </DropdownMenu>

                <!-- Hidden File Input for Resume -->
                <input
                  type="file"
                  ref="fileInput"
                  accept="application/pdf"
                  style="display: none"
                  @change="handleFileChange"
                />

                <!-- Position Picker -->
                <DropdownMenu>
                  <DropdownMenuTrigger as-child>
                    <button
                      :class="
                        cn(
                          dropdownTriggerVariants({ size: 'compact' }),
                          'composer-toolbar-control composer-toolbar-select cursor-pointer overflow-hidden !border-transparent bg-transparent hover:bg-accent hover:text-accent-foreground',
                        )
                      "
                    >
                      <Tooltip>
                        <TooltipTrigger as-child>
                          <div
                            class="flex h-full w-full items-center gap-[var(--spacing-xs)] overflow-hidden"
                          >
                            <Briefcase class="w-3.5 h-3.5 shrink-0 opacity-70" />
                            <span class="min-w-0 flex-1 truncate font-medium text-foreground">{{
                              selectedPositionName
                            }}</span>
                          </div>
                        </TooltipTrigger>
                        <TooltipContent side="top" :side-offset="8" class="z-[110] text-xs">
                          {{ selectedPositionName }}
                        </TooltipContent>
                      </Tooltip>
                      <svg
                        xmlns="http://www.w3.org/2000/svg"
                        width="16"
                        height="16"
                        viewBox="0 0 24 24"
                        fill="none"
                        stroke="currentColor"
                        stroke-width="2"
                        stroke-linecap="round"
                        stroke-linejoin="round"
                        class="shrink-0 opacity-50 ml-1"
                      >
                        <path d="m6 9 6 6 6-6" />
                      </svg>
                    </button>
                  </DropdownMenuTrigger>
                  <DropdownMenuContent
                    class="min-w-[var(--reka-dropdown-menu-trigger-width)] w-[var(--composer-toolbar-select-inline-size)]"
                    align="start"
                  >
                    <DropdownMenuItem
                      v-for="p in positions"
                      :key="p.id"
                      size="compact"
                      @click="emit('update:selectedPositionId', p.id)"
                      class="cursor-pointer overflow-hidden whitespace-nowrap"
                    >
                      <TooltipText class="min-w-0 flex-1 text-foreground" :text="p.name" />
                    </DropdownMenuItem>
                  </DropdownMenuContent>
                </DropdownMenu>

                <!-- JD Toggle -->
                <Tooltip>
                  <TooltipTrigger as-child>
                    <Button
                      variant="ghost"
                      size="compact"
                      class="composer-toolbar-control overflow-hidden"
                      type="button"
                      @click="toggleJdInput"
                      :class="{ 'bg-accent text-accent-foreground': showJdInput }"
                    >
                      <FileSearch class="w-3.5 h-3.5 shrink-0 opacity-70" />
                      <span class="font-medium truncate">{{
                        showJdInput ? '已开启' : '未开启'
                      }}</span>
                    </Button>
                  </TooltipTrigger>
                  <TooltipContent side="top" :side-offset="8" class="z-[110] text-xs">
                    {{ showJdInput ? '已开启' : '未开启' }}
                  </TooltipContent>
                </Tooltip>
              </template>
              <template v-else>
                <Tooltip>
                  <TooltipTrigger as-child>
                    <div
                      class="composer-toolbar-control inline-flex overflow-hidden items-center rounded-md text-sm opacity-65"
                    >
                      <FileText class="w-3.5 h-3.5 shrink-0 opacity-70" />
                      <span class="font-medium truncate text-foreground">{{
                        selectedResumeName
                      }}</span>
                    </div>
                  </TooltipTrigger>
                  <TooltipContent side="top" :side-offset="8" class="z-[110] text-xs">
                    {{ selectedResumeName }}
                  </TooltipContent>
                </Tooltip>

                <Tooltip>
                  <TooltipTrigger as-child>
                    <div
                      class="composer-toolbar-control inline-flex overflow-hidden items-center rounded-md text-sm opacity-65"
                    >
                      <Briefcase class="w-3.5 h-3.5 shrink-0 opacity-70" />
                      <span class="font-medium truncate text-foreground">{{
                        selectedPositionName
                      }}</span>
                    </div>
                  </TooltipTrigger>
                  <TooltipContent side="top" :side-offset="8" class="z-[110] text-xs">
                    {{ selectedPositionName }}
                  </TooltipContent>
                </Tooltip>

                <Tooltip v-if="jdText">
                  <TooltipTrigger as-child>
                    <div
                      class="composer-toolbar-control inline-flex overflow-hidden items-center rounded-md text-sm opacity-65"
                    >
                      <FileSearch class="w-3.5 h-3.5 shrink-0 opacity-70" />
                      <span class="font-medium truncate text-foreground">已开启</span>
                    </div>
                  </TooltipTrigger>
                  <TooltipContent side="top" :side-offset="8" class="z-[110] text-xs">
                    已开启
                  </TooltipContent>
                </Tooltip>
              </template>

              <!-- Model Info -->
              <DropdownMenu v-if="canSelectModel">
                <DropdownMenuTrigger as-child>
                  <button
                    :class="
                      cn(
                        dropdownTriggerVariants({ size: 'compact' }),
                        'composer-toolbar-control composer-toolbar-select cursor-pointer overflow-hidden !border-transparent bg-transparent hover:bg-accent hover:text-accent-foreground',
                      )
                    "
                  >
                    <Terminal class="w-3.5 h-3.5 shrink-0 opacity-70" />
                    <TooltipText class="min-w-0 flex-1 text-foreground" :text="modelDisplay" />
                    <svg
                      xmlns="http://www.w3.org/2000/svg"
                      width="16"
                      height="16"
                      viewBox="0 0 24 24"
                      fill="none"
                      stroke="currentColor"
                      stroke-width="2"
                      stroke-linecap="round"
                      stroke-linejoin="round"
                      class="shrink-0 opacity-50 ml-1"
                    >
                      <path d="m6 9 6 6 6-6" />
                    </svg>
                  </button>
                </DropdownMenuTrigger>
                <DropdownMenuContent class="w-[var(--reka-popper-anchor-width)]" align="start">
                  <DropdownMenuItem
                    v-for="model in providerModels"
                    :key="model"
                    size="compact"
                    @click="selectedComposerModel = model"
                    class="cursor-pointer overflow-hidden whitespace-nowrap"
                  >
                    <TooltipText class="min-w-0 flex-1 text-foreground" :text="model" />
                  </DropdownMenuItem>
                  <DropdownMenuSeparator />
                  <DropdownMenuItem
                    size="compact"
                    class="cursor-pointer justify-center whitespace-nowrap font-medium text-primary"
                    @click="navigateToLlm"
                  >
                    LLM 配置
                  </DropdownMenuItem>
                </DropdownMenuContent>
              </DropdownMenu>
              <Tooltip v-else>
                <TooltipTrigger as-child>
                  <Button
                    variant="ghost"
                    size="compact"
                    class="composer-toolbar-control overflow-hidden"
                    @click="navigateToLlm"
                    type="button"
                  >
                    <Terminal class="w-3.5 h-3.5 shrink-0 opacity-70" />
                    <span class="font-medium truncate">{{ modelDisplay }}</span>
                  </Button>
                </TooltipTrigger>
                <TooltipContent side="top" :side-offset="8" class="z-[110] text-xs">
                  {{ modelDisplay }}
                </TooltipContent>
              </Tooltip>
            </div>
          </TooltipProvider>
        </div>

        <div class="composer-actions__right">
          <Button
            v-if="!activeSessionId"
            class="rounded-md px-6 flex-shrink-0 !font-serif"
            :disabled="!canStart"
            :loading="creating"
            @click="emit('start', showJdInput ? localJdText : undefined, selectedComposerModel)"
          >
            开始面试
          </Button>
          <template v-else>
            <transition name="mode-switch" mode="out-in">
              <template v-if="isVoiceMode">
                <div key="voice" class="flex items-center gap-2">
                  <Button
                    variant="outline"
                    size="icon"
                    class="rounded-md"
                    @click="emit('update:isVoiceMode', false)"
                    type="button"
                    aria-label="切换到文字输入"
                  >
                    <svg
                      viewBox="0 0 24 24"
                      width="18"
                      height="18"
                      stroke="currentColor"
                      stroke-width="2"
                      fill="none"
                      stroke-linecap="round"
                      stroke-linejoin="round"
                    >
                      <rect x="2" y="4" width="20" height="16" rx="2" ry="2" />
                      <line x1="6" y1="8" x2="6.01" y2="8" />
                      <line x1="10" y1="8" x2="10.01" y2="8" />
                      <line x1="14" y1="8" x2="14.01" y2="8" />
                      <line x1="18" y1="8" x2="18.01" y2="8" />
                      <line x1="6" y1="12" x2="6.01" y2="12" />
                      <line x1="18" y1="12" x2="18.01" y2="12" />
                      <line x1="7" y1="16" x2="17" y2="16" />
                      <line x1="10" y1="12" x2="10.01" y2="12" />
                      <line x1="14" y1="12" x2="14.01" y2="12" />
                    </svg>
                  </Button>
                  <Button
                    class="voice-press-btn"
                    :class="{ 'is-pressed': isRecording }"
                    :disabled="disabled || sending"
                    @mousedown="startRecording"
                    @mouseup="stopRecording"
                    @mouseleave="stopRecording"
                    @touchstart.prevent="startRecording"
                    @touchend.prevent="stopRecording"
                    type="button"
                  >
                    {{ isRecording ? '松开发送' : '按住说话' }}
                  </Button>
                </div>
              </template>
              <template v-else>
                <div key="text" class="flex items-center gap-2">
                  <Button
                    variant="outline"
                    size="icon"
                    class="rounded-md"
                    @click="emit('update:isVoiceMode', true)"
                    type="button"
                    aria-label="切换到语音输入"
                  >
                    <svg
                      viewBox="0 0 24 24"
                      width="18"
                      height="18"
                      stroke="currentColor"
                      stroke-width="2"
                      fill="none"
                      stroke-linecap="round"
                      stroke-linejoin="round"
                    >
                      <path d="M12 1a3 3 0 0 0-3 3v8a3 3 0 0 0 6 0V4a3 3 0 0 0-3-3z" />
                      <path d="M19 10v2a7 7 0 0 1-14 0v-2" />
                      <line x1="12" y1="19" x2="12" y2="23" />
                      <line x1="8" y1="23" x2="16" y2="23" />
                    </svg>
                  </Button>
                  <Button
                    class="rounded-md px-6 flex-shrink-0 !font-serif"
                    :disabled="disabled || !canSend"
                    :loading="sending"
                    @click="emit('send')"
                  >
                    发送
                  </Button>
                </div>
              </template>
            </transition>
          </template>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.interview-composer {
  /* 组件级几何变量：集中声明 composer 各区块的尺寸语义，
     避免在属性侧出现裸 px 与 magic height ratio。 */
  --composer-max-inline-size: var(--layout-workspace-content-max-inline-size);
  --composer-input-min-block-size: calc(var(--ui-height-base) * 2 + var(--spacing-md));
  --composer-actions-min-block-size: calc(var(--ui-height-base) + var(--spacing-1-5));
  --composer-toolbar-control-min-inline-size: calc(var(--ui-height-compact) * 3);
  --composer-toolbar-control-max-inline-size: calc(var(--ui-height-compact) * 6);
  --composer-toolbar-select-inline-size: calc(var(--ui-height-compact) * 4 + var(--spacing-lg));
  --composer-voice-area-block-size: 88px;
  --composer-voice-wave-block-size: 60px;
  --composer-voice-wave-max-inline-size: 300px;
  --composer-status-dot-size: var(--spacing-sm);
  --composer-press-offset: var(--spacing-0-5);
  transition:
    transform var(--motion-duration-base) var(--motion-ease-standard),
    border-color var(--motion-duration-base) var(--motion-ease-standard),
    box-shadow var(--motion-duration-base) var(--motion-ease-standard);
  inline-size: 100%;
}
.interview-composer.is-centered {
  max-inline-size: var(--composer-max-inline-size);
  margin: 0 auto;
}
.interview-composer.is-bottom {
  max-inline-size: var(--composer-max-inline-size);
  margin: 0 auto;
  position: relative;
}
.interview-composer__inner {
  background: var(--color-surface);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-xl);
  box-shadow: var(--shadow-whisper), var(--shadow-ring);
  padding: var(--spacing-md);
  display: flex;
  flex-direction: column;
  gap: var(--spacing-sm);
  overflow: hidden;
}
.interview-composer.is-disabled .interview-composer__inner {
  opacity: 0.65;
  pointer-events: none;
}
.interview-composer.is-centered .interview-composer__inner {
  padding: var(--spacing-lg);
  gap: var(--spacing-md);
}
.composer-input-area {
  min-block-size: var(--composer-input-min-block-size);
  display: flex;
  align-items: flex-start;
  position: relative;
}
.composer-mode-text,
.composer-mode-voice {
  inline-size: 100%;
}

.composer-actions {
  display: flex;
  align-items: center;
  justify-content: space-between;
  min-block-size: var(--composer-actions-min-block-size);
  margin-top: auto;
  min-inline-size: 0;
}
.composer-actions__left {
  display: flex;
  align-items: center;
  min-inline-size: 0;
}
.composer-actions__right {
  display: flex;
  align-items: center;
  gap: var(--spacing-md);
}
.composer-actions__hint {
  color: var(--color-text-tertiary);
  padding-inline-start: var(--spacing-xs);
}
.composer-toolbar {
  display: flex;
  align-items: center;
  gap: var(--spacing-sm);
  min-inline-size: 0;
}
.composer-toolbar-control {
  block-size: var(--ui-height-compact);
  min-inline-size: var(--composer-toolbar-control-min-inline-size);
  max-inline-size: var(--composer-toolbar-control-max-inline-size);
  padding: 0 var(--spacing-sm);
  gap: var(--spacing-xs);
  font-family: var(--font-serif);
}
.composer-toolbar-select {
  inline-size: var(--composer-toolbar-select-inline-size);
}
.voice-press-btn {
  padding: 0 var(--spacing-lg);
  font-family: var(--font-serif);
  user-select: none;
}
.voice-press-btn.is-pressed {
  transform: translateY(var(--composer-press-offset));
  box-shadow: var(--shadow-ring-deep);
}

/* Mode switch transition (Text <-> Voice) */
.mode-switch-enter-active,
.mode-switch-leave-active {
  transition:
    opacity var(--motion-duration-base) var(--motion-ease-standard),
    transform var(--motion-duration-base) var(--motion-ease-standard);
}
.mode-switch-enter-from {
  opacity: 0;
  transform: translateY(var(--spacing-xs));
}
.mode-switch-leave-to {
  opacity: 0;
  transform: translateY(var(--spacing-neg-xs));
}
</style>
