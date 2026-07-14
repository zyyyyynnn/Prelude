import {
  nextTick,
  onBeforeUnmount,
  onMounted,
  ref,
  watch,
  type ComponentPublicInstance,
  type Ref,
} from 'vue'
import { useVoiceMedia } from './useVoiceMedia'

type UseComposerVoiceOptions = {
  incomingAudio: Ref<string | undefined>
  isVoiceMode: Ref<boolean | undefined>
  voiceStatus: Ref<string | undefined>
  onAudioChunk: (chunk: ArrayBuffer) => void
  onStartRecording: () => void
  onStopRecording: () => void
  onPlayStatus: (status: 'playing' | 'idle') => void
}

export function useComposerVoice(options: UseComposerVoiceOptions) {
  const canvasRef = ref<HTMLCanvasElement | null>(null)
  const playlist = ref<string[]>([])
  const isPlaying = ref(false)
  const displayStatus = ref('等待中')
  let currentAudio: HTMLAudioElement | null = null
  let currentAudioUrl: string | null = null
  let brandColor = ''
  let borderWarmColor = ''
  let analyser: AnalyserNode | null = null
  let animFrameId: number | null = null

  const {
    isRecording,
    startRecording: mediaStart,
    stopRecording: mediaStop,
  } = useVoiceMedia({
    onAudioChunk(chunk) {
      options.onAudioChunk(chunk)
    },
    onWaveform(activeAnalyser) {
      drawWaveLoop(activeAnalyser)
    },
  })

  function setCanvasRef(element: Element | ComponentPublicInstance | null) {
    canvasRef.value = element instanceof HTMLCanvasElement ? element : null
  }

  function getThemeColors() {
    if (typeof window !== 'undefined') {
      const style = getComputedStyle(document.documentElement)
      brandColor = style.getPropertyValue('--color-brand').trim()
      borderWarmColor = style.getPropertyValue('--color-border-warm').trim()
    }
  }

  function drawWaveLoop(activeAnalyser: AnalyserNode) {
    if (!canvasRef.value) return
    analyser = activeAnalyser
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
      ctx.fillStyle = brandColor
      const barWidth = 6
      const barGap = 4
      const barCount = Math.floor(width / (barWidth + barGap))

      for (let i = 0; i < barCount; i++) {
        const distFromCenter = Math.abs(i - barCount / 2) / (barCount / 2)
        const factor = Math.max(0, 1 - distFromCenter * distFromCenter)
        let barHeight = (dataArray[i % bufferLength] / 255) * (height * 0.7) * factor
        if (barHeight < 4) {
          barHeight = 4
        }
        const x = i * (barWidth + barGap)
        const y = (height - barHeight) / 2
        ctx.beginPath()
        ctx.roundRect(x, y, barWidth, barHeight, 3)
        ctx.fill()
      }
    }

    draw()
  }

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

  async function startRecording() {
    if (isRecording.value) return
    options.onStartRecording()
    stopPlayback()
    await mediaStart()
  }

  function stopRecording() {
    if (!isRecording.value) return
    options.onStopRecording()
    mediaStop()
    if (animFrameId) {
      cancelAnimationFrame(animFrameId)
      animFrameId = null
    }
    drawFlatLine()
  }

  function appendAudio(base64: string) {
    playlist.value.push(base64)
    if (!isPlaying.value) {
      playNext()
    }
  }

  function releaseCurrentAudioUrl() {
    if (currentAudioUrl) {
      URL.revokeObjectURL(currentAudioUrl)
      currentAudioUrl = null
    }
  }

  function playNext() {
    if (playlist.value.length === 0) {
      isPlaying.value = false
      options.onPlayStatus('idle')
      return
    }

    isPlaying.value = true
    options.onPlayStatus('playing')
    const base64 = playlist.value.shift()!

    try {
      const binary = atob(base64)
      const array = new Uint8Array(binary.length)
      for (let i = 0; i < binary.length; i++) {
        array[i] = binary.charCodeAt(i)
      }
      const blob = new Blob([array], { type: 'audio/mp3' })
      const url = URL.createObjectURL(blob)
      currentAudioUrl = url
      currentAudio = new Audio(url)
      currentAudio.onended = () => {
        releaseCurrentAudioUrl()
        playNext()
      }
      currentAudio.onerror = () => {
        releaseCurrentAudioUrl()
        playNext()
      }
      currentAudio.play().catch((error) => {
        console.warn('Audio playback blocked or interrupted:', error)
        releaseCurrentAudioUrl()
        playNext()
      })
    } catch (error) {
      console.error('Failed to parse and play audio chunk:', error)
      playNext()
    }
  }

  function stopPlayback() {
    playlist.value = []
    isPlaying.value = false
    options.onPlayStatus('idle')
    if (currentAudio) {
      currentAudio.pause()
      currentAudio = null
    }
    releaseCurrentAudioUrl()
  }

  watch(options.incomingAudio, (newVal) => {
    if (newVal) {
      appendAudio(newVal)
    }
  })

  watch(
    options.voiceStatus,
    (newStatus) => {
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
    },
    { immediate: true },
  )

  watch(options.isVoiceMode, (newVal) => {
    if (!newVal) {
      stopRecording()
      stopPlayback()
    } else {
      void nextTick(() => {
        drawFlatLine()
      })
    }
  })

  onMounted(() => {
    getThemeColors()
    if (options.isVoiceMode.value) {
      drawFlatLine()
    }
  })

  onBeforeUnmount(() => {
    stopRecording()
    stopPlayback()
  })

  return {
    setCanvasRef,
    displayStatus,
    isRecording,
    startRecording,
    stopRecording,
  }
}
