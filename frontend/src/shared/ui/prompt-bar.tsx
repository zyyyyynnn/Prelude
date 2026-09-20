import {
  useEffect,
  useLayoutEffect,
  useRef,
  type FormEventHandler,
  type KeyboardEventHandler,
  type ReactNode,
} from 'react'
import { Briefcase, FileText, Image, Paperclip, ScanSearch, X } from 'lucide-react'
import { IconTooltip } from '@/shared/ui/overlay'

/*
 * Prompt Bar shell adapted from Beautiful UI's Prompt Bar.
 * Copyright (c) 2026 Shane Levine. Licensed under the MIT License.
 */
export function PromptBar({
  disabled,
  value,
  placeholder,
  inputDisabled,
  inputLabel,
  attachments,
  leftActions,
  rightActions,
  onValueChange,
  onInputKeyDown,
  onSubmit,
}: {
  disabled?: boolean
  value?: string
  placeholder?: string
  inputDisabled?: boolean
  inputLabel: string
  attachments?: ReactNode
  leftActions: ReactNode
  rightActions: ReactNode
  onValueChange?: (value: string) => void
  onInputKeyDown?: KeyboardEventHandler<HTMLTextAreaElement>
  onSubmit: FormEventHandler<HTMLFormElement>
}) {
  const input = useRef<HTMLTextAreaElement>(null)

  useLayoutEffect(() => {
    if (!input.current) return
    input.current.style.height = '0px'
    // `prompt-bar-input` caps the grown height with `max-block-size`, so the autosize
    // only reports the content height; the cap is not repeated here.
    input.current.style.height = `${input.current.scrollHeight}px`
  }, [value])

  return (
    <form
      className="mx-auto w-full max-w-(--layout-workspace-content-max-inline-size) group/prompt"
      data-disabled={disabled ? 'true' : undefined}
      data-beautiful-ui="prompt-bar"
      onSubmit={onSubmit}
    >
      <div
        className="prompt-bar-surface group-data-[disabled=true]/prompt:pointer-events-none group-data-[disabled=true]/prompt:opacity-65"
        data-slot="prompt-bar-surface"
      >
        {attachments && (
          <div
            className="flex flex-wrap items-center gap-xs pt-xs px-sm"
            data-slot="prompt-bar-attachments"
          >
            {attachments}
          </div>
        )}
        <div
          className="flex min-h-(--layout-prompt-input-min-block-size) items-start"
          data-slot="prompt-bar-input-area"
        >
          <textarea
            ref={input}
            className="prompt-bar-input"
            data-slot="prompt-bar-input"
            rows={1}
            value={value}
            disabled={inputDisabled}
            placeholder={placeholder}
            aria-label={inputLabel}
            onChange={(event) => onValueChange?.(event.target.value)}
            onKeyDown={onInputKeyDown}
          />
        </div>
        <div
          className="flex min-h-(--ui-height-control) min-w-0 items-center justify-between gap-sm"
          data-slot="prompt-bar-controls"
        >
          <div className="flex min-w-0 flex-1 items-center">{leftActions}</div>
          <div className="flex items-center gap-sm">{rightActions}</div>
        </div>
      </div>
    </form>
  )
}

/** A read-only fact pinned into the prompt bar's control row, e.g. the frozen model. */
export function PromptBarFact({ label, icon }: { label: string; icon: ReactNode }) {
  return (
    <IconTooltip label={label}>
      <span className="prompt-bar-control prompt-bar-control-text opacity-72" tabIndex={0}>
        {icon}
        <span className="min-w-0 flex-1 truncate text-start">{label}</span>
      </span>
    </IconTooltip>
  )
}

/** The JD-matching chip. Matching is either on or absent — there is no off state — so
 *  the control only renders while it is on, and pressing it turns matching off. */
export function PromptBarJdToggle({ onDisable }: { onDisable: () => void }) {
  return (
    <button
      type="button"
      className="prompt-bar-control prompt-bar-control-jd ui-action"
      aria-pressed="true"
      onClick={onDisable}
    >
      <ScanSearch aria-hidden="true" />
      <span>JD 匹配</span>
    </button>
  )
}

/** A context file the interview is grounded in. The remove box only appears for
 *  editable context, so a locked turn renders the same chip without it. */
export function ContextAttachment({
  label,
  kind,
  onRemove,
}: {
  label: string
  kind: 'resume' | 'position' | 'document' | 'image'
  onRemove?: () => void
}) {
  const Icon =
    kind === 'resume'
      ? FileText
      : kind === 'position'
        ? Briefcase
        : kind === 'image'
          ? Image
          : Paperclip
  const kindLabel =
    kind === 'resume' ? '简历' : kind === 'position' ? '岗位' : kind === 'image' ? '图片' : '附件'
  return (
    <div className="prompt-bar-attachment">
      <Icon aria-hidden="true" />
      <IconTooltip label={label}>
        <span className="min-w-0 truncate" tabIndex={0}>
          {label}
        </span>
      </IconTooltip>
      {onRemove && (
        <button
          type="button"
          className="prompt-bar-attachment-remove ui-action ui-action-icon"
          aria-label={`移除${kindLabel}：${label}`}
          onClick={onRemove}
        >
          <X aria-hidden="true" />
        </button>
      )}
    </div>
  )
}

/** The voice lane's states, mirrored by `.prelude-button--hold` in the sheet. */
export type VoiceStatus = 'idle' | 'listening' | 'processing' | 'speaking'

/** The microphone's own level, drawn as bars inside the hold control. The analyser runs
 *  its own frame loop and writes one custom property, so a live meter never costs a React
 *  render; with no stream, or a browser that refuses the graph, the bars hold their floor
 *  and the control still reads as recording. */
export function VoiceLevelMeter({ stream }: { stream: MediaStream | null }) {
  const root = useRef<HTMLSpanElement>(null)

  useEffect(() => {
    if (!stream) return
    /* Reduced motion still gets a reading: the meter samples one frame and stops instead
       of pumping, so the control shows what the microphone sounds like without animating. */
    const continuous = !window.matchMedia('(prefers-reduced-motion: reduce)').matches
    let context: AudioContext | undefined
    let frame = 0
    try {
      context = new AudioContext()
      const analyser = context.createAnalyser()
      analyser.fftSize = 256
      context.createMediaStreamSource(stream).connect(analyser)
      const samples = new Uint8Array(analyser.fftSize)
      const paint = () => {
        analyser.getByteTimeDomainData(samples)
        let sum = 0
        for (const sample of samples) {
          const deviation = (sample - 128) / 128
          sum += deviation * deviation
        }
        const level = Math.min(1, Math.sqrt(sum / samples.length) * 4)
        root.current?.style.setProperty('--voice-level', level.toFixed(3))
        if (continuous) frame = requestAnimationFrame(paint)
      }
      frame = requestAnimationFrame(paint)
    } catch {
      return
    }
    return () => {
      cancelAnimationFrame(frame)
      void context?.close()
    }
  }, [stream])

  return (
    <span ref={root} className="voice-meter" aria-hidden="true">
      {Array.from({ length: 5 }, (_, index) => (
        <span key={index} />
      ))}
    </span>
  )
}
