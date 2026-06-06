<script setup lang="ts">
import { computed, ref, watch, onMounted, onBeforeUnmount, nextTick } from 'vue'
import type { ResumeItem, PositionTemplate } from '../../api/contracts'
import { Textarea } from '@/components/ui/textarea'
import { Button } from '@/components/ui/button'
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuTrigger,
  DropdownMenuSeparator,
} from '@/components/ui/dropdown-menu'
import {
  Tooltip,
  TooltipContent,
  TooltipProvider,
  TooltipTrigger,
} from '@/components/ui/tooltip'
import { FileText, Briefcase, FileSearch, Terminal } from '@lucide/vue'

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
  (e: 'start', jdText?: string): void
  (e: 'send'): void
  // 语音新增
  (e: 'update:isVoiceMode', value: boolean): void
  (e: 'voice-audio-chunk', chunk: ArrayBuffer): void
  (e: 'voice-start-recording'): void
  (e: 'voice-stop-recording'): void
  (e: 'voice-play-status', status: 'playing' | 'idle'): void
  (e: 'open-global-settings', tab: 'profile' | 'llm'): void
}>()

const fileInput = ref<HTMLInputElement | null>(null)
const showJdInput = ref(false)
const localJdText = ref('')

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
  emit('open-global-settings', 'llm')
}

// ==================== VOICE & CANVAS INTEGRATION ====================
import { useVoiceMedia } from '../../composables/useVoiceMedia'

const { isRecording, startRecording: mediaStart, stopRecording: mediaStop } = useVoiceMedia({
  onAudioChunk(chunk) {
    emit('voice-audio-chunk', chunk)
  },
  onWaveform(a) {
    drawWaveLoop(a)
  },
})

// Canvas for wave visualization
const canvasRef = ref<HTMLCanvasElement | null>(null)

// Audio playback queue
const playlist = ref<string[]>([])
let currentAudio: HTMLAudioElement | null = null
const isPlaying = ref(false)

// Watch incoming audio chunks to play them in queue
watch(() => props.incomingAudio, (newVal) => {
  if (newVal) {
    appendAudio(newVal)
  }
})

// Visual colors extracted dynamically from CSS variables
let brandColor = '#9e7b6a'
let borderWarmColor = '#e8e6dc'

function getThemeColors() {
  if (typeof window !== 'undefined') {
    const style = getComputedStyle(document.documentElement)
    brandColor = style.getPropertyValue('--color-brand').trim() || '#9e7b6a'
    borderWarmColor = style.getPropertyValue('--color-border-warm').trim() || '#e8e6dc'
  }
}

// Visual wave renderer
let analyser: AnalyserNode | null = null
let animFrameId: number | null = null

function drawWaveLoop(a: AnalyserNode) {
  if (!canvasRef.value) return
  analyser = a
  drawWave()
}

function drawWave() {
  if (!canvasRef.value || !analyser) return
  const canvas = canvasRef.value
  const ctx = canvas.getContext('2d')
  if (!ctx) return

  const bufferLength = analyser.frequencyBinCount
  const dataArray = new Uint8Array(bufferLength)

  const width = canvas.width
  const height = canvas.height

  const draw = () => {
    animFrameId = requestAnimationFrame(draw)
    analyser!.getByteFrequencyData(dataArray)

    ctx.clearRect(0, 0, width, height)

    // Draw multi-bar smooth jumping wave conforming to visual constraints
    ctx.fillStyle = brandColor
    const barWidth = 6
    const barGap = 4
    const barCount = Math.floor(width / (barWidth + barGap))

    for (let i = 0; i < barCount; i++) {
      // Create symmetrical heights
      const distFromCenter = Math.abs(i - barCount / 2) / (barCount / 2)
      const factor = Math.max(0, 1 - distFromCenter * distFromCenter)
      
      // Calculate dynamic bar height
      let barHeight = (dataArray[i % bufferLength] / 255) * (height * 0.7) * factor
      if (barHeight < 4) {
        barHeight = 4 // Minimum bar height
      }

      const x = i * (barWidth + barGap)
      const y = (height - barHeight) / 2

      // Draw rounded rectangle for bars
      ctx.beginPath()
      ctx.roundRect(x, y, barWidth, barHeight, 3)
      ctx.fill()
    }
  }

  draw()
}

// Draw flat line as visual placeholder
function drawFlatLine() {
  const canvas = canvasRef.value
  const ctx = canvas?.getContext('2d')
  if (canvas && ctx) {
    ctx.clearRect(0, 0, canvas.width, canvas.height)
    ctx.fillStyle = borderWarmColor
    ctx.beginPath()
    ctx.roundRect((canvas.width - 120) / 2, (canvas.height - 4) / 2, 120, 4, 2)
    ctx.fill()
  }
}

// Start capturing mic & record
async function startRecording() {
  if (isRecording.value) return
  emit('voice-start-recording')

  // Stop any active playbacks
  stopPlayback()

  await mediaStart()
}

// Stop recording and close mic
function stopRecording() {
  if (!isRecording.value) return
  emit('voice-stop-recording')

  mediaStop()

  if (animFrameId) {
    cancelAnimationFrame(animFrameId)
    animFrameId = null
  }

  drawFlatLine()
}

// Add base64 sound to playback queue
function appendAudio(base64: string) {
  playlist.value.push(base64)
  if (!isPlaying.value) {
    playNext()
  }
}

// Play next chunk in sequence
function playNext() {
  if (playlist.value.length === 0) {
    isPlaying.value = false
    emit('voice-play-status', 'idle')
    return
  }

  isPlaying.value = true
  emit('voice-play-status', 'playing')
  const base64 = playlist.value.shift()!

  try {
    const binary = atob(base64)
    const array = new Uint8Array(binary.length)
    for (let i = 0; i < binary.length; i++) {
      array[i] = binary.charCodeAt(i)
    }
    const blob = new Blob([array], { type: 'audio/mp3' })
    const url = URL.createObjectURL(blob)

    currentAudio = new Audio(url)
    currentAudio.onended = () => {
      URL.revokeObjectURL(url)
      playNext()
    }
    currentAudio.onerror = () => {
      URL.revokeObjectURL(url)
      playNext()
    }
    currentAudio.play().catch((err) => {
      console.warn('Audio playback blocked or interrupted:', err)
      playNext()
    })
  } catch (err) {
    console.error('Failed to parse and play audio chunk:', err)
    playNext()
  }
}

// Immediately abort any current playbacks
function stopPlayback() {
  playlist.value = []
  isPlaying.value = false
  emit('voice-play-status', 'idle')
  if (currentAudio) {
    currentAudio.pause()
    currentAudio = null
  }
}

// Display text mapping for voice interactions
const displayStatus = ref('等待中')
watch(() => props.voiceStatus, (newStatus) => {
  if (newStatus === 'stt_processing') {
    displayStatus.value = '正在识别您的发言...'
  } else if (newStatus === 'tts_processing') {
    displayStatus.value = 'AI 正在思考...'
  } else if (newStatus === 'speaking') {
    displayStatus.value = 'AI 正在发言...'
  } else if (newStatus === 'listening') {
    displayStatus.value = '您可以开始说话...'
  } else {
    displayStatus.value = '按住说话进行模拟'
  }
}, { immediate: true })

watch(() => props.isVoiceMode, (newVal) => {
  if (!newVal) {
    stopRecording()
    stopPlayback()
  } else {
    nextTick(() => {
      drawFlatLine()
    })
  }
})

onMounted(() => {
  getThemeColors()
  if (props.isVoiceMode) {
    drawFlatLine()
  }
})

onBeforeUnmount(() => {
  stopRecording()
  stopPlayback()
})
</script>

<template>
  <div :class="['interview-composer', { 'is-centered': isCentered, 'is-bottom': !isCentered }]">
    <div class="interview-composer__inner">
      <!-- Input Area / Voice Wave Area -->
      <div class="composer-input-area">
        <transition name="mode-switch" mode="out-in">
          <div class="composer-mode-text" v-if="!isVoiceMode">
            <Textarea
              :model-value="modelValue"
              @update:model-value="(v: string | number) => emit('update:modelValue', String(v))"
              :rows="3"
              class="composer-textarea min-h-[80px] max-h-[160px] resize-none border-0 bg-transparent shadow-none p-2 text-[15px] focus-visible:ring-0 focus-visible:ring-offset-0 disabled:opacity-50 disabled:cursor-default"
              :placeholder="activeSessionId ? '输入回答...' : '请先选择简历与岗位，然后点击「开始面试」'"
              :disabled="disabled || !activeSessionId"
              @keydown.ctrl.enter="canSend && emit('send')"
              @keydown.meta.enter="canSend && emit('send')"
            />
            <transition name="jd-fade-float">
              <div v-if="!activeSessionId && showJdInput" class="absolute inset-0 z-10 bg-surface">
                <Textarea
                  v-model="localJdText"
                  :rows="3"
                  class="composer-textarea h-full w-full min-h-[80px] max-h-[160px] resize-none border-0 bg-transparent shadow-none p-2 text-[15px] focus-visible:ring-0 focus-visible:ring-offset-0"
                  placeholder="粘贴目标岗位职责或 JD 文本，系统将通过 RAG 算法进行智能分块和背景匹配发问..."
                />
              </div>
            </transition>
          </div>
          <div class="composer-mode-voice" v-else>
            <div class="composer-voice-area">
              <div class="voice-status-container">
                <span class="status-indicator" :class="voiceStatus" />
                <span class="status-text">{{ displayStatus }}</span>
              </div>
              <div class="voice-wave-container">
                <canvas ref="canvasRef" width="300" height="60" class="voice-canvas" />
              </div>
            </div>
          </div>
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
                  <button class="flex h-[30px] w-36 items-center justify-between overflow-hidden rounded-md border border-transparent bg-transparent px-2 py-1 text-[13px] hover:bg-accent hover:text-accent-foreground outline-none focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-ring disabled:cursor-not-allowed disabled:opacity-50 cursor-pointer !font-serif">
                    <Tooltip>
                      <TooltipTrigger as-child>
                        <div class="flex h-full w-full items-center gap-1.5 overflow-hidden">
                          <FileText class="w-3.5 h-3.5 shrink-0 opacity-70" />
                          <span class="font-medium truncate text-foreground">{{ selectedResumeName }}</span>
                        </div>
                      </TooltipTrigger>
                      <TooltipContent side="top" :side-offset="8" class="z-[110] text-xs">
                        {{ selectedResumeName }}
                      </TooltipContent>
                    </Tooltip>
                    <svg xmlns="http://www.w3.org/2000/svg" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" class="shrink-0 opacity-50 ml-1"><path d="m6 9 6 6 6-6"/></svg>
                  </button>
                </DropdownMenuTrigger>
                <DropdownMenuContent class="z-[105] w-36 border border-black/5 shadow-lg rounded-xl p-0" align="start">
                  <DropdownMenuItem 
                    v-for="r in resumes" 
                    :key="r.id" 
                    @click="emit('update:selectedResumeId', r.id)"
                    class="flex h-[30px] items-center justify-between px-3 text-[13px] !font-serif rounded-md cursor-pointer"
                  >
                    {{ r.fileName }}
                  </DropdownMenuItem>
                  <DropdownMenuSeparator />
                  <DropdownMenuItem @click="triggerUpload" class="flex h-[30px] items-center justify-between px-3 text-[13px] !font-serif rounded-md cursor-pointer text-primary font-medium justify-center">
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
                  <button class="flex h-[30px] w-36 items-center justify-between overflow-hidden rounded-md border border-transparent bg-transparent px-2 py-1 text-[13px] hover:bg-accent hover:text-accent-foreground outline-none focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-ring disabled:cursor-not-allowed disabled:opacity-50 cursor-pointer !font-serif">
                    <Tooltip>
                      <TooltipTrigger as-child>
                        <div class="flex h-full w-full items-center gap-1.5 overflow-hidden">
                          <Briefcase class="w-3.5 h-3.5 shrink-0 opacity-70" />
                          <span class="font-medium truncate text-foreground">{{ selectedPositionName }}</span>
                        </div>
                      </TooltipTrigger>
                      <TooltipContent side="top" :side-offset="8" class="z-[110] text-xs">
                        {{ selectedPositionName }}
                      </TooltipContent>
                    </Tooltip>
                    <svg xmlns="http://www.w3.org/2000/svg" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" class="shrink-0 opacity-50 ml-1"><path d="m6 9 6 6 6-6"/></svg>
                  </button>
                </DropdownMenuTrigger>
                <DropdownMenuContent class="z-[105] w-36 border border-black/5 shadow-lg rounded-xl p-0" align="start">
                  <DropdownMenuItem 
                    v-for="p in positions" 
                    :key="p.id" 
                    @click="emit('update:selectedPositionId', p.id)"
                    class="flex h-[30px] items-center justify-between px-3 text-[13px] !font-serif rounded-md cursor-pointer"
                  >
                    {{ p.name }}
                  </DropdownMenuItem>
                </DropdownMenuContent>
              </DropdownMenu>

              <!-- JD Toggle -->
              <Tooltip>
                <TooltipTrigger as-child>
                  <Button variant="ghost" size="sm" class="h-[30px] px-2 text-[13px] gap-1.5 max-w-[180px] min-w-[100px] overflow-hidden !font-serif" type="button" @click="showJdInput = !showJdInput" :class="{ 'bg-accent text-accent-foreground': showJdInput }">
                    <FileSearch class="w-3.5 h-3.5 shrink-0 opacity-70" />
                    <span class="font-medium truncate">{{ showJdInput ? '已开启' : '未开启' }}</span>
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
                  <div class="inline-flex h-[30px] max-w-[180px] min-w-[100px] overflow-hidden items-center gap-1.5 rounded-md px-2 text-[13px] !font-serif pointer-events-none opacity-65">
                    <FileText class="w-3.5 h-3.5 shrink-0 opacity-70" />
                    <span class="font-medium truncate text-foreground">{{ selectedResumeName }}</span>
                  </div>
                </TooltipTrigger>
                <TooltipContent side="top" :side-offset="8" class="z-[110] text-xs">
                  {{ selectedResumeName }}
                </TooltipContent>
              </Tooltip>

              <Tooltip>
                <TooltipTrigger as-child>
                  <div class="inline-flex h-[30px] max-w-[180px] min-w-[100px] overflow-hidden items-center gap-1.5 rounded-md px-2 text-[13px] !font-serif pointer-events-none opacity-65">
                    <Briefcase class="w-3.5 h-3.5 shrink-0 opacity-70" />
                    <span class="font-medium truncate text-foreground">{{ selectedPositionName }}</span>
                  </div>
                </TooltipTrigger>
                <TooltipContent side="top" :side-offset="8" class="z-[110] text-xs">
                  {{ selectedPositionName }}
                </TooltipContent>
              </Tooltip>

              <Tooltip v-if="jdText">
                <TooltipTrigger as-child>
                  <div class="inline-flex h-[30px] max-w-[180px] min-w-[100px] overflow-hidden items-center gap-1.5 rounded-md px-2 text-[13px] !font-serif pointer-events-none opacity-65">
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
            <Tooltip>
              <TooltipTrigger as-child>
                <Button variant="ghost" size="sm" class="h-[30px] px-2 text-[13px] gap-1.5 max-w-[180px] min-w-[100px] overflow-hidden !font-serif" @click="navigateToLlm" type="button">
                  <Terminal class="w-3.5 h-3.5 shrink-0 opacity-70" />
                  <span class="font-medium truncate">{{ llmProvider || '未配置' }} / {{ llmModel || 'default' }}</span>
                </Button>
              </TooltipTrigger>
              <TooltipContent side="top" :side-offset="8" class="z-[110] text-xs">
                {{ llmProvider || '未配置' }} / {{ llmModel || 'default' }}
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
            @click="emit('start', showJdInput ? localJdText : undefined)"
          >
            开始面试
          </Button>
          <template v-else>
            <transition name="mode-switch" mode="out-in">
              <template v-if="isVoiceMode">
                <div key="voice" class="flex items-center gap-2">
                  <Button 
                    variant="outline" size="icon-sm" class="rounded-md"
                    @click="emit('update:isVoiceMode', false)"
                    type="button"
                  >
                    <svg viewBox="0 0 24 24" width="18" height="18" stroke="currentColor" stroke-width="2" fill="none" stroke-linecap="round" stroke-linejoin="round">
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
                  <button
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
                  </button>
                </div>
              </template>
              <template v-else>
                <div key="text" class="flex items-center gap-2">
                  <Button 
                    variant="outline" size="icon-sm" class="rounded-md"
                    @click="emit('update:isVoiceMode', true)"
                    type="button"
                  >
                    <svg viewBox="0 0 24 24" width="18" height="18" stroke="currentColor" stroke-width="2" fill="none" stroke-linecap="round" stroke-linejoin="round">
                      <path d="M12 1a3 3 0 0 0-3 3v8a3 3 0 0 0 6 0V4a3 3 0 0 0-3-3z"/>
                      <path d="M19 10v2a7 7 0 0 1-14 0v-2"/>
                      <line x1="12" y1="19" x2="12" y2="23"/>
                      <line x1="8" y1="23" x2="16" y2="23"/>
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
  transition: max-width 0.3s ease-in-out, margin 0.3s ease-in-out, transform 0.3s ease-in-out, border-color 0.3s ease-in-out, box-shadow 0.3s ease-in-out;
  width: 100%;
}
.interview-composer.is-centered {
  max-width: 800px;
  margin: 0 auto;
}
.interview-composer.is-bottom {
  max-width: 800px;
  margin: 0 auto;
  position: relative;
}
.interview-composer__inner {
  background: var(--color-surface);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-xl);
  box-shadow: 0 4px 20px color-mix(in srgb, var(--color-text-primary) 4%, transparent), 0 0 0 1px var(--color-ring);
  padding: var(--spacing-md);
  display: flex;
  flex-direction: column;
  gap: var(--spacing-sm);
  overflow: hidden;
}
.interview-composer.is-centered .interview-composer__inner {
  padding: var(--spacing-lg);
  gap: var(--spacing-md);
}
.composer-input-area {
  min-height: calc(3 * 1.5 * 16px + var(--spacing-sm) * 2);
  display: flex;
  align-items: flex-start;
  position: relative;
}
.composer-mode-text,
.composer-mode-voice {
  width: 100%;
}

.composer-actions {
  display: flex;
  align-items: center;
  justify-content: space-between;
  min-height: 40px;
  margin-top: auto;
  min-width: 0;
}
.composer-actions__left {
  display: flex;
  align-items: center;
  min-width: 0;
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
  min-width: 0;
}

/* Voice Integration Styles */
.composer-voice-area {
  height: 88px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 var(--spacing-sm);
  background-color: var(--color-surface-hover);
  border-radius: var(--radius-lg);
  box-sizing: border-box;
}
.voice-status-container {
  display: flex;
  align-items: center;
  gap: var(--spacing-sm);
}
.status-indicator {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background-color: var(--color-ring, #d1cfc5);
}
.status-indicator.listening {
  background-color: var(--color-brand, #9e7b6a);
  animation: pulse 1.5s infinite ease-in-out;
}
.status-indicator.stt_processing,
.status-indicator.tts_processing {
  background-color: var(--color-coral, #b08878);
  animation: pulse 1s infinite ease-in-out;
}
.status-indicator.speaking {
  background-color: var(--color-brand, #9e7b6a);
  box-shadow: 0 0 8px var(--color-brand);
}
.status-text {
  font-size: 14px;
  color: var(--color-text-secondary);
  font-weight: 500;
}
.voice-wave-container {
  flex: 1;
  display: flex;
  justify-content: center;
  align-items: center;
  height: 60px;
  max-width: 300px;
}
.voice-canvas {
  width: 100%;
  height: 100%;
}
.voice-press-btn {
  height: var(--ui-height-base);
  padding: 0 var(--spacing-lg);
  border-radius: var(--radius-md);
  font-size: 14px;
  font-weight: 500;
  font-family: var(--font-serif) !important;
  cursor: pointer;
  border: 1px solid var(--color-brand);
  background-color: var(--color-brand);
  color: var(--color-surface);
  transition: transform 0.3s ease-in-out, background-color 0.3s ease-in-out, box-shadow 0.3s ease-in-out; /* 快速响应按下 */
}
.voice-press-btn:not(:active) {
  transition: transform 0.3s ease-in-out, background-color 0.3s ease-in-out, box-shadow 0.3s ease-in-out; /* 从容释放 */
}
.voice-press-btn {
  user-select: none;
  outline: none;
}
.voice-press-btn:hover:not(:disabled) {
  background-color: color-mix(in srgb, var(--color-brand) 85%, var(--color-surface));
}
.voice-press-btn:disabled {
  cursor: not-allowed;
  opacity: 0.5;
  pointer-events: none;
}
.voice-press-btn:focus-visible {
  outline: 2px solid var(--color-brand);
  outline-offset: 2px;
}
.voice-press-btn.is-pressed {
  background-color: var(--color-brand);
  color: var(--color-surface);
  border-color: var(--color-brand);
  box-shadow: 0 0 12px var(--color-brand);
  transform: scale(0.98);
}


.jd-fade-float-enter-active,
.jd-fade-float-leave-active {
  transition: opacity 0.3s ease-in-out, transform 0.3s ease-in-out;
}
.jd-fade-float-enter-from {
  opacity: 0;
  transform: translateY(4px); /* 自下而上柔和 4px 浮入 */
}
.jd-fade-float-leave-to {
  opacity: 0;
  transform: translateY(-4px); /* 向上方 4px 飘散淡出 */
}
.jd-fade-float-leave-active {
  pointer-events: none; /* 绝对保留：离场防遮挡 */
}


/* Mode switch transition (Text <-> Voice) */
.mode-switch-enter-active,
.mode-switch-leave-active {
  transition: opacity 0.3s ease-in-out, transform 0.3s ease-in-out;
}
.mode-switch-enter-from {
  opacity: 0;
  transform: translateY(4px);
}
.mode-switch-leave-to {
  opacity: 0;
  transform: translateY(-4px);
}

@keyframes pulse {
  0% {
    transform: scale(0.9);
    opacity: 0.6;
  }
  50% {
    transform: scale(1.2);
    opacity: 1;
  }
  100% {
    transform: scale(0.9);
    opacity: 0.6;
  }
}
</style>
