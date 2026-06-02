<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref, watch } from 'vue'

const props = defineProps<{
  statusText: string // 'stt_processing' | 'tts_processing' | 'speaking' | 'listening' | 'idle'
  incomingAudio?: string // New audio chunk to append to playlist
}>()

const emit = defineEmits<{
  (e: 'audio-chunk', chunk: ArrayBuffer): void
  (e: 'start-recording'): void
  (e: 'stop-recording'): void
  (e: 'play-status', status: 'playing' | 'idle'): void
}>()

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

// Visual colors extracted dynamically from CSS variables to conform to DESIGN.md
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

// Start capturing mic & record
async function startRecording() {
  if (isRecording.value) return
  isRecording.value = true
  emit('start-recording')

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
          emit('audio-chunk', buffer)
        })
      }
    }
    
    // Slice recorder output every 250ms to feed stream
    mediaRecorder.start(250)
  } catch (err) {
    console.error('Failed to start microphone recording:', err)
    isRecording.value = false
    emit('stop-recording')
  }
}

// Stop recording and close mic
function stopRecording() {
  if (!isRecording.value) return
  isRecording.value = false
  emit('stop-recording')

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

  // Draw flat line as visual placeholder
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
    emit('play-status', 'idle')
    return
  }

  isPlaying.value = true
  emit('play-status', 'playing')
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
  emit('play-status', 'idle')
  if (currentAudio) {
    currentAudio.pause()
    currentAudio = null
  }
}

// Display text mapping for voice interactions
const displayStatus = ref('等待中')
watch(() => props.statusText, (newStatus) => {
  if (newStatus === 'stt_processing') {
    displayStatus.value = '正在识别您的发言...'
  } else if (newStatus === 'tts_processing') {
    displayStatus.value = 'AI 正在思考...'
  } else if (newStatus === 'speaking') {
    displayStatus.value = 'AI 正在发言...'
  } else if (newStatus === 'listening') {
    displayStatus.value = '您可以开始说话...'
  } else {
    displayStatus.value = '按住说话进行面试'
  }
}, { immediate: true })

onMounted(() => {
  getThemeColors()
  // Draw initial flat indicator
  const canvas = canvasRef.value
  const ctx = canvas?.getContext('2d')
  if (canvas && ctx) {
    ctx.clearRect(0, 0, canvas.width, canvas.height)
    ctx.fillStyle = borderWarmColor
    ctx.beginPath()
    ctx.roundRect((canvas.width - 120) / 2, (canvas.height - 4) / 2, 120, 4, 2)
    ctx.fill()
  }
})

onBeforeUnmount(() => {
  stopRecording()
  stopPlayback()
})
</script>

<template>
  <div class="voice-visualizer">
    <div class="voice-visualizer__status">
      <span class="status-indicator" :class="statusText" />
      <span class="status-text">{{ displayStatus }}</span>
    </div>

    <!-- Canvas Wave -->
    <div class="voice-visualizer__wave-container">
      <canvas ref="canvasRef" width="300" height="80" class="voice-canvas" />
    </div>

    <!-- Push Button -->
    <div class="voice-visualizer__action">
      <button 
        class="voice-btn" 
        :class="{ 'is-active': isRecording }"
        @mousedown="startRecording"
        @mouseup="stopRecording"
        @mouseleave="stopRecording"
        @touchstart.prevent="startRecording"
        @touchend.prevent="stopRecording"
        type="button"
      >
        <span class="voice-btn__icon">
          <svg viewBox="0 0 24 24" width="24" height="24" stroke="currentColor" stroke-width="2" fill="none" stroke-linecap="round" stroke-linejoin="round">
            <path d="M12 1a3 3 0 0 0-3 3v8a3 3 0 0 0 6 0V4a3 3 0 0 0-3-3z"/>
            <path d="M19 10v2a7 7 0 0 1-14 0v-2"/>
            <line x1="12" y1="19" x2="12" y2="23"/>
            <line x1="8" y1="23" x2="16" y2="23"/>
          </svg>
        </span>
      </button>
      <p class="voice-btn__tip">按住说话，松开发送</p>
    </div>
  </div>
</template>

<style scoped>
.voice-visualizer {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  background: var(--color-surface);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-xl);
  padding: var(--spacing-lg);
  box-shadow: 0 4px 20px color-mix(in srgb, var(--color-text-primary) 2%, transparent);
  width: 100%;
  max-width: 420px;
  margin: 0 auto;
}
.voice-visualizer__status {
  display: flex;
  align-items: center;
  gap: var(--spacing-sm);
  margin-bottom: var(--spacing-md);
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
.voice-visualizer__wave-container {
  width: 100%;
  height: 80px;
  display: flex;
  align-items: center;
  justify-content: center;
  margin-bottom: var(--spacing-lg);
}
.voice-canvas {
  width: 300px;
  height: 80px;
}
.voice-visualizer__action {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: var(--spacing-sm);
}
.voice-btn {
  width: 72px;
  height: 72px;
  border-radius: 50%;
  border: 1px solid var(--color-border);
  background: var(--color-surface);
  color: var(--color-text-secondary);
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: all 0.2s ease;
  box-shadow: 0 4px 10px color-mix(in srgb, var(--color-text-primary) 3%, transparent);
}
.voice-btn:hover {
  background-color: var(--color-surface-hover);
  color: var(--color-brand);
  transform: translateY(-1px);
}
.voice-btn:focus-visible {
  outline: 2px solid var(--color-focus);
  outline-offset: 2px;
}
.voice-btn.is-active {
  background-color: var(--color-brand);
  color: var(--color-surface);
  border-color: var(--color-brand);
  transform: scale(0.95);
  box-shadow: 0 0 16px var(--color-brand);
}
.voice-btn__icon {
  display: flex;
  align-items: center;
  justify-content: center;
}
.voice-btn__tip {
  font-size: 12px;
  color: var(--color-text-tertiary);
  margin: 0;
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
