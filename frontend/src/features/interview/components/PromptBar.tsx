import {
  useLayoutEffect,
  useRef,
  type FormEventHandler,
  type KeyboardEventHandler,
  type ReactNode,
} from 'react'
import { classNames } from '@/shared/lib/class-names'

/*
 * Adapted from Beautiful UI's Prompt Bar.
 * Copyright (c) 2026 Shane Levine. Licensed under the MIT License.
 */
export function PromptBar({
  placement,
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
  placement: 'centered' | 'bottom'
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
      className={classNames(
        'prompt-bar',
        placement === 'centered' ? 'is-centered' : 'is-bottom',
        disabled && 'is-disabled',
      )}
      data-beautiful-ui="prompt-bar"
      onSubmit={onSubmit}
    >
      <div className="prompt-bar__surface">
        {attachments && <div className="prompt-bar__attachments">{attachments}</div>}
        <div className="prompt-bar__input-area">
          {inputContent ?? (
            <textarea
              ref={input}
              className="prompt-bar__input"
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
        <div className="prompt-bar__controls">
          <div className="prompt-bar__controls-start">{leftActions}</div>
          <div className="prompt-bar__controls-end">{rightActions}</div>
        </div>
      </div>
    </form>
  )
}
