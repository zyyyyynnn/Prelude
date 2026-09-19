import { useState, type FormEvent } from 'react'
import { Keyboard, Mic, ScanSearch, Terminal } from 'lucide-react'
import type { AttachmentItem } from '@/features/assets'
import { Button } from '@/shared/ui/button'
import { IconTooltip } from '@/shared/ui/overlay'
import {
  ContextAttachment,
  PromptBar,
  PromptBarFact,
  VoiceIndicator,
  type VoiceStatus,
} from '@/shared/ui/prompt-bar'
import type { InterviewMessageRecord } from '../types'
import { useVoiceInterview } from '../useVoiceInterview'
import { LockedInterviewContextButton } from './PromptBarControls'

function voiceStatusLabel(status: VoiceStatus, recording: boolean): string {
  if (recording) return '正在聆听'
  if (status === 'processing') return '正在处理'
  if (status === 'speaking') return '面试官正在回答'
  return '语音模式已连接'
}

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
  const [voice, setVoice] = useState(false)
  const voiceState = useVoiceInterview({
    enabled: voice,
    sessionId,
    onMessage,
    onRefresh,
    onError,
    onTerminalError: () => setVoice(false),
  })
  function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const value = answer.trim()
    if (!value || sending || disabled) return
    onSend(value)
    setAnswer('')
  }

  const voiceContent = (
    <VoiceIndicator
      status={voiceState.status}
      recording={voiceState.recording}
      label={voiceStatusLabel(voiceState.status, voiceState.recording)}
    />
  )

  const actions = voice ? (
    <>
      <IconTooltip label="切换到文字输入">
        <Button
          type="button"
          size="icon"
          variant="secondary"
          aria-label="切换到文字输入"
          onClick={() => {
            voiceState.close()
            setVoice(false)
          }}
        >
          <Keyboard aria-hidden="true" />
        </Button>
      </IconTooltip>
      <Button
        type="button"
        shape="hold"
        pressed={voiceState.recording}
        disabled={disabled || sending}
        onPointerDown={() => void voiceState.startRecording()}
        onPointerUp={voiceState.stopRecording}
        onPointerLeave={voiceState.stopRecording}
        onPointerCancel={voiceState.stopRecording}
        onKeyDown={(event) => {
          if (!event.repeat && (event.key === 'Enter' || event.key === ' ')) {
            event.preventDefault()
            void voiceState.startRecording()
          }
        }}
        onKeyUp={(event) => {
          if (event.key === 'Enter' || event.key === ' ') voiceState.stopRecording()
        }}
      >
        {voiceState.recording ? '松开发送' : '按住说话'}
      </Button>
    </>
  ) : (
    <>
      <IconTooltip label="切换到语音输入">
        <Button
          type="button"
          size="icon"
          variant="secondary"
          aria-label="切换到语音输入"
          onClick={() => setVoice(true)}
          disabled={disabled}
        >
          <Mic aria-hidden="true" />
        </Button>
      </IconTooltip>
      <Button type="submit" loading={sending} disabled={disabled || !answer.trim()} shape="action">
        发送
      </Button>
    </>
  )

  return (
    <PromptBar
      disabled={disabled}
      value={answer}
      inputLabel="面试回答"
      onValueChange={setAnswer}
      inputDisabled={disabled || sending}
      inputContent={voice ? voiceContent : undefined}
      placeholder={disabled ? '本场面试已结束' : '输入回答…'}
      attachments={
        <>
          {resumeName && <ContextAttachment kind="resume" label={resumeName} />}
          <ContextAttachment kind="position" label={positionName} />
          {attachments.map((attachment) => (
            <ContextAttachment
              key={attachment.id}
              kind={attachment.image ? 'image' : 'document'}
              label={attachment.fileName}
            />
          ))}
        </>
      }
      leftActions={
        <div className="flex min-w-0 items-center gap-xs">
          <LockedInterviewContextButton />
          <PromptBarFact label={modelName} icon={<Terminal aria-hidden="true" />} />
          {jdMatched && <PromptBarFact label="JD 匹配" icon={<ScanSearch aria-hidden="true" />} />}
        </div>
      }
      rightActions={actions}
      onInputKeyDown={(event) => {
        if ((event.ctrlKey || event.metaKey) && event.key === 'Enter') {
          event.preventDefault()
          event.currentTarget.form?.requestSubmit()
        }
      }}
      onSubmit={submit}
    />
  )
}
