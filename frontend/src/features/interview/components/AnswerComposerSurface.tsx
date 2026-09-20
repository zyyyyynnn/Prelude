import { type FormEvent } from 'react'
import { ArrowUp, Keyboard, Mic } from 'lucide-react'
import type { AttachmentItem } from '@/features/assets'
import { Button } from '@/shared/ui/button'
import { IconTooltip } from '@/shared/ui/overlay'
import {
  ContextAttachment,
  PromptBar,
  VoiceLevelMeter,
  type VoiceStatus,
} from '@/shared/ui/prompt-bar'
import { InterviewContextFacts } from './PromptBarControls'

/** The composer with its wiring left to the caller: what the draft says, whether the
 *  microphone lane is open and what the level meter should read. The live composer
 *  supplies them from the voice channel, the gallery supplies them frozen, so neither of
 *  them owns a second copy of this chrome. */
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
  /** Present while the microphone lane is open. The text box stays live in both modes: a
   *  transcript lands there and is only sent once the candidate sends it. */
  voice?: { status: VoiceStatus; recording: boolean; media: MediaStream | null }
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
        <>
          <IconTooltip label={voice ? '切换到文字输入' : '切换到语音输入'}>
            <Button
              type="button"
              size="icon"
              variant="secondary"
              aria-label={voice ? '切换到文字输入' : '切换到语音输入'}
              onClick={onToggleVoice}
              disabled={disabled}
            >
              {voice ? <Keyboard aria-hidden="true" /> : <Mic aria-hidden="true" />}
            </Button>
          </IconTooltip>
          {/* Held, the control keeps its box: the words fade and the meter takes their
              place, so the row it sits in never moves. */}
          {voice ? (
            <Button
              type="button"
              shape="hold"
              pressed={voice.recording}
              loading={voice.status === 'processing'}
              disabled={disabled || sending || voice.status === 'speaking'}
              aria-label={voice.recording ? '松开发送' : '按住说话'}
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
              <span className="prelude-button__label">按住说话</span>
              {voice.recording && <VoiceLevelMeter stream={voice.media} />}
            </Button>
          ) : null}
          <IconTooltip label="发送">
            <Button
              type="submit"
              size="icon"
              loading={sending}
              disabled={disabled || !answer.trim()}
              aria-label="发送"
            >
              <ArrowUp aria-hidden="true" />
            </Button>
          </IconTooltip>
        </>
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
