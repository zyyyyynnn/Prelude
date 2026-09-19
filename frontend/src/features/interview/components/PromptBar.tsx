import {
  useLayoutEffect,
  useRef,
  type FormEventHandler,
  type KeyboardEventHandler,
  type ReactNode,
} from 'react'

/*
 * Adapted from Beautiful UI's Prompt Bar.
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
    input.current.style.height = `${Math.min(input.current.scrollHeight, 100)}px`
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
