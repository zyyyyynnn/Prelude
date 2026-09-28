import { useState } from 'react'
import { cn } from '@/shared/lib/cn'
import { InsetCard } from '@/shared/ui'

/**
 * One user instruction as stream content, on the same inset surface as the
 * assistant message. It sticks under the top bar while the turn it belongs to
 * is still on screen, and releases when that turn ends.
 */
export function StickyInstruction({
  instruction,
  className,
}: {
  instruction: string
  className?: string
}) {
  const [expanded, setExpanded] = useState(false)
  const singleLine = instruction.length <= 80
  return (
    <div className={cn('sticky-instruction', className)}>
      <InsetCard>
        <button
          type="button"
          aria-expanded={expanded}
          onClick={() => setExpanded((value) => !value)}
          className={cn('w-full text-start type-body', !expanded && !singleLine && 'truncate')}
        >
          {instruction}
        </button>
        {!singleLine && (
          <button
            type="button"
            className="type-meta"
            onClick={() => setExpanded((value) => !value)}
          >
            {expanded ? '收起' : '展开完整指令'}
          </button>
        )}
      </InsetCard>
    </div>
  )
}
