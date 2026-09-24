import { useState, type FormEvent } from 'react'
import type { AttachmentItem } from '@/features/assets'
import type { InterviewMessageRecord } from '../types'
import { useVoiceInterview } from '../useVoiceInterview'
import { AnswerComposerSurface } from './AnswerComposerSurface'

export function InterviewAnswerComposer({
  sessionId,
  resumeName,
  positionName,
  modelName,
  attachments,
  jdMatched,
  disabled,
  sending,
  onSend,
  onMessage,
  onRefresh,
  onError,
}: {
  sessionId: number
  resumeName?: string
  positionName: string
  modelName: string
  attachments: AttachmentItem[]
  jdMatched: boolean
  disabled: boolean
  sending: boolean
  onSend: (value: string) => void
  onMessage: (message: InterviewMessageRecord, append?: boolean) => void
  onRefresh: () => void
  onError: (message: string) => void
}) {
  const [answer, setAnswer] = useState('')
  const [voiceOpen, setVoiceOpen] = useState(false)
  const voiceState = useVoiceInterview({
    enabled: voiceOpen,
    sessionId,
    onMessage,
    // What the microphone heard lands in the draft, never in the thread: the candidate
    // reads it back and sends it themselves.
    onTranscript: (text) => setAnswer((current) => (current.trim() ? `${current}\n${text}` : text)),
    onRefresh,
    onError,
    onTerminalError: () => setVoiceOpen(false),
  })

  function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const value = answer.trim()
    if (!value || sending || disabled) return
    onSend(value)
    setAnswer('')
  }

  return (
    <AnswerComposerSurface
      answer={answer}
      attachments={attachments}
      disabled={disabled}
      jdMatched={jdMatched}
      modelName={modelName}
      positionName={positionName}
      resumeName={resumeName}
      sending={sending}
      voice={
        voiceOpen
          ? {
              status: voiceState.status,
              recording: voiceState.recording,
              media: voiceState.media,
            }
          : undefined
      }
      onAnswerChange={setAnswer}
      onHoldEnd={voiceState.stopRecording}
      onHoldStart={() => void voiceState.startRecording()}
      onSubmit={submit}
      onToggleVoice={() => {
        if (!voiceOpen) {
          setVoiceOpen(true)
          return
        }
        voiceState.close()
        setVoiceOpen(false)
      }}
    />
  )
}
