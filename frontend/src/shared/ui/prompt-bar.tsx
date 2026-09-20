import {
  useLayoutEffect,
  useRef,
  type FormEventHandler,
  type KeyboardEventHandler,
  type ReactNode,
} from 'react'
import { Briefcase, FileText, Image, Paperclip, ScanSearch, X } from 'lucide-react'
import { cn } from '@/shared/lib/cn'
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
  inputContent,
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
  inputContent?: ReactNode
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
          {inputContent ?? (
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
          )}
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

/** The voice lane's states, mirrored by `.prompt-bar-status-dot.is-*` in the sheet. */
export type VoiceStatus = 'idle' | 'listening' | 'processing' | 'speaking'

/** The voice lane's in-place replacement for the text input: status dot, whatever
 *  the caller says the lane is doing, and the recording waveform. */
export function VoiceIndicator({
  status,
  recording,
  label,
}: {
  status: VoiceStatus
  recording: boolean
  label: string
}) {
  return (
    <div className="w-full">
      <div className="flex min-h-(--layout-prompt-input-min-block-size) items-center justify-between rounded-md bg-surface-hover px-sm">
        <div className="flex items-center gap-sm font-serif text-sm font-medium text-text-secondary">
          <span className={cn('prompt-bar-status-dot', `is-${status}`)} />
          <span>{label}</span>
        </div>
        <div className={cn('prompt-bar-wave', recording && 'is-active')} aria-hidden="true">
          {Array.from({ length: 9 }, (_, index) => (
            <span key={index} />
          ))}
        </div>
      </div>
    </div>
  )
}
