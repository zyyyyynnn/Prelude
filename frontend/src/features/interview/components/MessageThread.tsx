import { useEffect, useRef } from 'react'
import type { InterviewMessageRecord } from '../types'

export function MessageThread({
  messages,
  connectionStatus,
}: {
  messages: InterviewMessageRecord[]
  connectionStatus?: string
}) {
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
    <div className="message-thread scrollable" ref={thread}>
      {visible.length ? (
        visible.map((message, index) => (
          <article
            className={`message-bubble message-bubble--${message.role}`}
            key={`${message.id}-${message.createdAt ?? index}`}
          >
            <div className="message-bubble__head">
              <span className="message-role">{message.role === 'assistant' ? '面试官' : '我'}</span>
              {message.score != null && (
                <span className="message-score">{message.score.toFixed(1)} / 10</span>
              )}
            </div>
            <div className="message-bubble__content">
              {message.role === 'assistant' && !message.content ? (
                <span className="thinking-dots">思考中</span>
              ) : (
                message.content
              )}
            </div>
            {message.hint && <p className="message-bubble__hint">{message.hint}</p>}
          </article>
        ))
      ) : (
        <div className="message-thread__empty">
          <p>会话已准备就绪，可以开始面试了。</p>
        </div>
      )}
      {connectionStatus && <div className="reconnecting-status">{connectionStatus}</div>}
    </div>
  )
}
