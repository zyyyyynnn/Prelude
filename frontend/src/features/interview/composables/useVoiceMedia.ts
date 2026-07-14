import { ref, onBeforeUnmount } from 'vue'

export interface UseVoiceMediaOptions {
  onAudioChunk?: (chunk: ArrayBuffer) => void
  onWaveform?: (analyser: AnalyserNode) => void
}

export function useVoiceMedia(options: UseVoiceMediaOptions = {}) {
  const isRecording = ref(false)
  let mediaRecorder: MediaRecorder | null = null
  let audioCtx: AudioContext | null = null
  let analyser: AnalyserNode | null = null
  let micStream: MediaStream | null = null
  let animFrameId: number | null = null

  async function startRecording() {
    if (!navigator.mediaDevices?.getUserMedia) {
      console.warn('MediaDevices API not available')
      return
    }

    try {
      micStream = await navigator.mediaDevices.getUserMedia({ audio: true })
      audioCtx = new AudioContext()
      const source = audioCtx.createMediaStreamSource(micStream)
      analyser = audioCtx.createAnalyser()
      analyser.fftSize = 256
      source.connect(analyser)

      mediaRecorder = new MediaRecorder(micStream, { mimeType: 'audio/webm' })
      mediaRecorder.ondataavailable = (e) => {
        if (e.data.size > 0) {
          void e.data.arrayBuffer().then((buf) => options.onAudioChunk?.(buf))
        }
      }
      mediaRecorder.start(250)
      isRecording.value = true
      options.onWaveform?.(analyser)
    } catch (err) {
      console.error('Microphone access denied:', err)
      cleanup()
    }
  }

  function stopRecording() {
    if (mediaRecorder?.state === 'recording') {
      mediaRecorder.stop()
    }
    cleanup()
    isRecording.value = false
  }

  function cleanup() {
    micStream?.getTracks().forEach((t) => t.stop())
    micStream = null
    if (audioCtx?.state !== 'closed') {
      audioCtx?.close().catch(() => {})
    }
    audioCtx = null
    analyser = null
    mediaRecorder = null
    if (animFrameId !== null) {
      cancelAnimationFrame(animFrameId)
      animFrameId = null
    }
  }

  onBeforeUnmount(cleanup)

  return { isRecording, startRecording, stopRecording, getAnalyser: () => analyser }
}
