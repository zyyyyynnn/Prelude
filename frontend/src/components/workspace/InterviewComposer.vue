<script setup lang="ts">
import { computed, ref, watch, onMounted, onBeforeUnmount, nextTick } from 'vue'
import { ElDropdown, ElDropdownMenu, ElDropdownItem, ElInput, ElButton } from 'element-plus'
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
  emit('open-global-settings', 'llm')
}

// ==================== VOICE & CANVAS INTEGRATION ====================
// Recorder state
const isRecording = ref(false)
let mediaRecorder: MediaRecorder | null = null
let audioCtx: AudioContext | null = null
let analyser: AnalyserNode | null = null
let micStream: MediaStream | null = null
let animFrameId: number | null = null

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
  isRecording.value = true
  emit('voice-start-recording')

  // Stop any active playbacks
  stopPlayback()

  try {
    micStream = await navigator.mediaDevices.getUserMedia({ audio: true })
    
    // Setup visualizer AudioContext
    audioCtx = new (window.AudioContext || (window as any).webkitAudioContext)()
    analyser = audioCtx.createAnalyser()
    analyser.fftSize = 64
    const source = audioCtx.createMediaStreamSource(micStream)
    source.connect(analyser)

    drawWave()

    // Setup recorder
    mediaRecorder = new MediaRecorder(micStream, { mimeType: 'audio/webm' })
    mediaRecorder.ondataavailable = (event) => {
      if (event.data && event.data.size > 0) {
        event.data.arrayBuffer().then((buffer) => {
          emit('voice-audio-chunk', buffer)
        })
      }
    }
    
    // Slice recorder output every 250ms to feed stream
    mediaRecorder.start(250)
  } catch (err) {
    console.error('Failed to start microphone recording:', err)
    isRecording.value = false
    emit('voice-stop-recording')
  }
}

// Stop recording and close mic
function stopRecording() {
  if (!isRecording.value) return
  isRecording.value = false
  emit('voice-stop-recording')

  if (mediaRecorder && mediaRecorder.state !== 'inactive') {
    mediaRecorder.stop()
  }

  if (micStream) {
    micStream.getTracks().forEach((track) => track.stop())
    micStream = null
  }

  if (audioCtx) {
    audioCtx.close()
    audioCtx = null
  }

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
            <ElInput
              :model-value="modelValue"
              @update:model-value="(v) => emit('update:modelValue', v)"
              type="textarea"
              :rows="3"
              resize="none"
              :placeholder="activeSessionId ? '输入回答...' : '请先选择简历与岗位，然后点击「开始面试」'"
              :disabled="disabled || !activeSessionId"
              class="composer-textarea"
              @keydown.ctrl.enter="canSend && emit('send')"
              @keydown.meta.enter="canSend && emit('send')"
            />
            <transition name="jd-expand">
              <div v-if="!activeSessionId && showJdInput" class="composer-jd-grid">
                <div class="composer-jd-inner">
                  <div class="composer-jd-area">
                    <ElInput
                      v-model="localJdText"
                      type="textarea"
                      :rows="4"
                      resize="none"
                      placeholder="粘贴目标岗位职责或 JD 文本，系统将通过 RAG 算法进行智能分块和背景匹配发问..."
                      class="jd-textarea"
                    />
                  </div>
                </div>
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
              <div class="toolbar-item is-readonly">
                <span class="toolbar-item__label">简历:</span>
                <span class="toolbar-item__value">{{ selectedResumeName }}</span>
              </div>
              <div class="toolbar-item is-readonly">
                <span class="toolbar-item__label">岗位:</span>
                <span class="toolbar-item__value">{{ selectedPositionName }}</span>
              </div>
              <div v-if="jdText" class="toolbar-item is-readonly is-active">
                <span class="toolbar-item__label">JD 匹配:</span>
                <span class="toolbar-item__value">已开启</span>
              </div>
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
            @click="emit('start', showJdInput ? localJdText : undefined)"
          >
            开始面试
          </ElButton>
          <template v-else>
            <template v-if="isVoiceMode">
              <button 
                class="icon-btn voice-toggle-action-btn"
                @click="emit('update:isVoiceMode', false)"
                title="切换到文字"
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
              </button>
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
            </template>
            <template v-else>
              <button 
                class="icon-btn voice-toggle-action-btn"
                @click="emit('update:isVoiceMode', true)"
                title="切换到语音"
                type="button"
              >
                <svg viewBox="0 0 24 24" width="18" height="18" stroke="currentColor" stroke-width="2" fill="none" stroke-linecap="round" stroke-linejoin="round">
                  <path d="M12 1a3 3 0 0 0-3 3v8a3 3 0 0 0 6 0V4a3 3 0 0 0-3-3z"/>
                  <path d="M19 10v2a7 7 0 0 1-14 0v-2"/>
                  <line x1="12" y1="19" x2="12" y2="23"/>
                  <line x1="8" y1="23" x2="16" y2="23"/>
                </svg>
              </button>
              <ElButton
                type="primary"
                class="ui-button ui-button--primary ui-button--compact composer-btn"
                :disabled="disabled || !canSend"
                :loading="sending"
                @click="emit('send')"
              >
                发送
              </ElButton>
            </template>
          </template>
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
  height: var(--ui-height-md, 36px);
  padding: 0 var(--spacing-lg);
  border-radius: var(--radius-lg);
  font-size: 14px;
  font-weight: 500;
  cursor: pointer;
  border: 1px solid var(--color-border);
  background-color: var(--color-surface);
  color: var(--color-text-primary);
  transition: all 0.2s ease;
  user-select: none;
  outline: none;
}
.voice-press-btn:hover:not(:disabled) {
  background-color: var(--color-surface-hover);
}
.voice-press-btn:disabled {
  cursor: not-allowed;
  opacity: 0.5;
  background-color: transparent;
  color: var(--color-text-tertiary);
  border-color: var(--color-border);
}
.voice-press-btn:focus-visible {
  outline: 2px solid var(--color-focus);
  outline-offset: 2px;
}
.voice-press-btn.is-pressed {
  background-color: var(--color-brand);
  color: var(--color-surface);
  border-color: var(--color-brand);
  box-shadow: 0 0 12px var(--color-brand);
  transform: scale(0.98);
}
.icon-btn.voice-toggle-action-btn {
  width: var(--ui-height-md, 36px);
  height: var(--ui-height-md, 36px);
  border-radius: var(--radius-lg);
  border: 1px solid var(--color-border);
  background: var(--color-surface);
  color: var(--color-text-secondary);
  display: inline-flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  transition: all 0.2s ease;
  outline: none;
}
.icon-btn.voice-toggle-action-btn:hover {
  background-color: var(--color-surface-hover);
  color: var(--color-brand);
}
.icon-btn.voice-toggle-action-btn:focus-visible {
  outline: 2px solid var(--color-focus);
  outline-offset: 2px;
}

/* Readonly Toolbar Items */
.toolbar-item.is-readonly {
  pointer-events: none;
  background-color: transparent;
  border: none;
  cursor: default;
}
.toolbar-item.is-readonly .toolbar-item__value {
  color: color-mix(in srgb, var(--color-text-primary) 65%, transparent);
}
.toolbar-item.is-readonly.is-active {
  background-color: var(--color-surface-hover);
}
.toolbar-item.is-readonly.is-active .toolbar-item__value {
  color: var(--color-brand);
}

/* JD Grid expand/collapse transition */
.composer-jd-grid {
  display: grid;
  grid-template-rows: 1fr;
}
.jd-expand-enter-active,
.jd-expand-leave-active {
  transition: grid-template-rows 0.2s ease, opacity 0.2s ease, transform 0.2s ease;
}
.jd-expand-enter-from,
.jd-expand-leave-to {
  grid-template-rows: 0fr;
  opacity: 0;
  transform: translateY(-8px);
}
.composer-jd-inner {
  min-height: 0;
  overflow: hidden;
}

/* Mode switch transition (Text <-> Voice) */
.mode-switch-enter-active,
.mode-switch-leave-active {
  transition: opacity 0.15s ease;
}
.mode-switch-enter-from,
.mode-switch-leave-to {
  opacity: 0;
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
