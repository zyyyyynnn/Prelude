import type { CSSProperties } from 'react'

/** A dimension score: label, value, and the fill rail that places it on a 0-10 scale.
 *  The rail width is the one documented runtime CSS variable in the design system. */
export function ScoreTile({ label, value }: { label: string; value: number }) {
  return (
    <div className="break-inside-avoid rounded-lg bg-surface-muted p-md">
      <span className="font-serif text-sm text-text-secondary">{label}</span>
      <strong className="my-sm block font-serif text-xl text-text-primary">
        {value.toFixed(1)}
      </strong>
      <div className="h-xs overflow-hidden rounded-full bg-border" aria-hidden="true">
        <span
          className="score-fill"
          style={{ '--report-score-fill': `${value * 10}%` } as CSSProperties}
        />
      </div>
    </div>
  )
}
