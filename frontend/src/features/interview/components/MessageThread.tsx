import { useEffect, useRef } from 'react'
import { cn } from '@/shared/lib/cn'
import type { InterviewMessageRecord } from '../types'

export function MessageThread({ messages }: { messages: InterviewMessageRecord[] }) {
  const thread = useRef<HTMLDivElement>(null)
  const visible = messages
    .filter((message) => message.role !== 'system')
    .map((message) => ({
      ...message,
      content:
        message.content
          ?.replace(/\[STAGE[_\s]?COMPLETE\]?/g, '')
          .replace(/\[STAGE(?:_(?:COM(?:P(?:L(?:E(?:TE?)?)?)?)?)?)?$/, '') ?? '',
    }))
  useEffect(() => {
    const frame = requestAnimationFrame(() => {
      if (thread.current) thread.current.scrollTop = thread.current.scrollHeight
    })
    return () => cancelAnimationFrame(frame)
  }, [messages])
  return (
    <div
      className="scrollable gutter-stable flex min-h-0 flex-1 flex-col gap-lg overflow-y-auto px-2xl pt-lg pb-(--composer-height)"
      ref={thread}
      data-slot="message-thread"
    >
      {visible.length ? (
        visible.map((message, index) => (
          <article
            className={cn(
              'message-bubble',
              message.role === 'user' ? 'items-end self-end' : 'items-start self-start',
            )}
            key={`${message.id}-${message.createdAt ?? index}`}
          >
            {/* Per-answer score and coaching belong to the finished report; the live
                thread only says who is speaking. */}
            <div className="flex w-full items-center gap-sm">
              <span
                className={cn(
                  'font-serif text-xs text-text-tertiary',
                  message.role === 'user' && 'ms-auto',
                )}
              >
                {message.role === 'assistant' ? '面试官' : '我'}
              </span>
            </div>
            <div
              className={cn(
                'message-bubble-body',
                message.role === 'user'
                  ? 'rounded-lg rounded-se-sm bg-surface-muted'
                  : 'rounded-lg rounded-ss-sm bg-surface elevated-whisper',
              )}
            >
              {message.role === 'assistant' && !message.content ? (
                <span className="ellipsis-progress text-text-tertiary">思考中</span>
              ) : (
                message.content
              )}
            </div>
          </article>
        ))
      ) : (
        <div className="flex h-full items-center justify-center text-text-tertiary">
          <p>会话已准备就绪，可以开始面试了。</p>
        </div>
      )}
    </div>
  )
}
