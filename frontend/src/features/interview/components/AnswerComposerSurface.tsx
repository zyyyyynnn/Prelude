import { type FormEvent } from 'react'
import { Keyboard, Mic } from 'lucide-react'
import type { AttachmentItem } from '@/features/assets'
import { Button } from '@/shared/ui/button'
import { IconTooltip } from '@/shared/ui/overlay'
import {
  ContextAttachment,
  PromptBar,
  VoiceIndicator,
  type VoiceStatus,
} from '@/shared/ui/prompt-bar'
import { voiceStatusLabel } from '../voiceStatusLabel'
import { InterviewContextFacts } from './PromptBarControls'

/** The answer composer with its wiring left to the caller: which mode the microphone is
 *  in, what the draft says and when the session is finished all arrive as props. The
 *  live composer supplies them from the voice channel, the gallery supplies them frozen,
 *  so neither of them owns a second copy of this chrome. */
export function AnswerComposerSurface({
  answer,
  attachments,
  disabled,
  jdMatched,
  modelName,
  positionName,
  resumeName,
  sending,
  voice,
  onAnswerChange,
  onHoldEnd,
  onHoldStart,
  onSubmit,
  onToggleVoice,
}: {
  answer: string
  attachments: AttachmentItem[]
  disabled: boolean
  jdMatched: boolean
  modelName: string
  positionName: string
  resumeName?: string
  sending: boolean
  /** Present while the microphone channel is open; its state drives the indicator,
   *  the hold button and which pair of trailing controls the composer shows. */
  voice?: { status: VoiceStatus; recording: boolean }
  onAnswerChange: (value: string) => void
  onHoldEnd: () => void
  onHoldStart: () => void
  onSubmit: (event: FormEvent<HTMLFormElement>) => void
  onToggleVoice: () => void
}) {
  return (
    <PromptBar
      disabled={disabled}
      value={answer}
      inputLabel="面试回答"
      onValueChange={onAnswerChange}
      inputDisabled={disabled || sending}
      inputContent={
        voice && (
          <VoiceIndicator
            status={voice.status}
            recording={voice.recording}
            label={voiceStatusLabel(voice.status, voice.recording)}
          />
        )
      }
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
      leftActions={<InterviewContextFacts modelName={modelName} jdMatched={jdMatched} />}
      rightActions={
        voice ? (
          <>
            <IconTooltip label="切换到文字输入">
              <Button
                type="button"
                size="icon"
                variant="secondary"
                aria-label="切换到文字输入"
                onClick={onToggleVoice}
              >
                <Keyboard aria-hidden="true" />
              </Button>
            </IconTooltip>
            <Button
              type="button"
              shape="hold"
              pressed={voice.recording}
              disabled={disabled || sending}
              onPointerDown={onHoldStart}
              onPointerUp={onHoldEnd}
              onPointerLeave={onHoldEnd}
              onPointerCancel={onHoldEnd}
              onKeyDown={(event) => {
                if (!event.repeat && (event.key === 'Enter' || event.key === ' ')) {
                  event.preventDefault()
                  onHoldStart()
                }
              }}
              onKeyUp={(event) => {
                if (event.key === 'Enter' || event.key === ' ') onHoldEnd()
              }}
            >
              {voice.recording ? '松开发送' : '按住说话'}
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
                onClick={onToggleVoice}
                disabled={disabled}
              >
                <Mic aria-hidden="true" />
              </Button>
            </IconTooltip>
            <Button
              type="submit"
              loading={sending}
              disabled={disabled || !answer.trim()}
              shape="action"
            >
              发送
            </Button>
          </>
        )
      }
      onInputKeyDown={(event) => {
        if ((event.ctrlKey || event.metaKey) && event.key === 'Enter') {
          event.preventDefault()
          event.currentTarget.form?.requestSubmit()
        }
      }}
      onSubmit={onSubmit}
    />
  )
}
