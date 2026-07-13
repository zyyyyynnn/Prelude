<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref } from 'vue'

defineProps<{
  voiceStatus?: string
  displayStatus: string
}>()

const emit = defineEmits<{
  (event: 'canvas-ref', value: HTMLCanvasElement | null): void
}>()

const canvasRef = ref<HTMLCanvasElement | null>(null)

onMounted(() => emit('canvas-ref', canvasRef.value))
onBeforeUnmount(() => emit('canvas-ref', null))
</script>

<template>
  <div class="composer-mode-voice">
    <div class="composer-voice-area">
      <div class="voice-status-container">
        <span class="status-indicator" :class="voiceStatus" />
        <span class="status-text text-sm">{{ displayStatus }}</span>
      </div>
      <div class="voice-wave-container">
        <canvas ref="canvasRef" width="300" height="60" class="voice-canvas" />
      </div>
    </div>
  </div>
</template>

<style scoped>
.composer-mode-voice {
  inline-size: 100%;
}
.composer-voice-area {
  block-size: var(--composer-voice-area-block-size);
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
  inline-size: var(--composer-status-dot-size);
  block-size: var(--composer-status-dot-size);
  border-radius: 50%;
  background-color: var(--color-ring);
}
.status-indicator.listening {
  background-color: var(--color-brand);
  animation: pulse var(--motion-duration-slow) infinite var(--motion-ease-standard);
}
.status-indicator.stt_processing,
.status-indicator.tts_processing {
  background-color: var(--color-coral);
  animation: pulse var(--motion-duration-slow) infinite var(--motion-ease-standard);
}
.status-indicator.speaking {
  background-color: var(--color-brand);
  box-shadow: var(--shadow-ring-deep);
}
.status-text {
  color: var(--color-text-secondary);
  font-weight: 500;
}
.voice-wave-container {
  flex: 1;
  display: flex;
  justify-content: center;
  align-items: center;
  block-size: var(--composer-voice-wave-block-size);
  max-inline-size: var(--composer-voice-wave-max-inline-size);
}
.voice-canvas {
  inline-size: 100%;
  block-size: 100%;
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
