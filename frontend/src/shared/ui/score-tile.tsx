import type { CSSProperties } from 'react'
import { InsetCard } from './inset-card'

/** A dimension score: label, value, and the fill rail that places it on a 0-10 scale.
 *  The rail width is the one documented runtime CSS variable in the design system. */
export function ScoreTile({ label, value }: { label: string; value: number }) {
  return (
    <InsetCard>
      <span className="type-label">{label}</span>
      <strong className="type-metric">{value.toFixed(1)}</strong>
      <div className="h-xs overflow-hidden rounded-full bg-border" aria-hidden="true">
        <span
          className="score-fill"
          style={{ '--score-fill': `${value * 10}%` } as CSSProperties}
        />
      </div>
    </InsetCard>
  )
}
