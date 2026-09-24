import { EmptyState, MessageBubble } from '@/shared/ui'
import { useEffect, useRef } from 'react'
import type { InterviewMessageRecord } from '../types'
import { TranscriptScroll } from './transcript-scroll'

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
    <TranscriptScroll ref={thread} data-slot="message-thread">
      {visible.length ? (
        visible.map((message, index) => (
          <MessageBubble
            key={`${message.id}-${message.createdAt ?? index}`}
            side={message.role === 'assistant' ? 'assistant' : 'user'}
            speaker={message.role === 'assistant' ? '面试官' : '我'}
            pending={message.role === 'assistant' && !message.content}
          >
            {message.content}
          </MessageBubble>
        ))
      ) : (
        <EmptyState message="会话已准备就绪，可以开始面试了。" className="flex-1" />
      )}
    </TranscriptScroll>
  )
}
