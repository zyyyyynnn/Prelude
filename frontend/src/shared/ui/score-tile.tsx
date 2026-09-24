import { InsetCard } from './inset-card'

/** A dimension score: label, value, and the fill rail that places it on a 0-10 scale.
 *  The rail width is a named step from the score (`data-score`), so no runtime CSS
 *  variable and no composed class name are needed. */
export function ScoreTile({ label, value }: { label: string; value: number }) {
  const step = Math.max(0, Math.min(10, Math.round(value)))
  return (
    <InsetCard>
      <span className="type-label">{label}</span>
      <strong className="type-metric">{value.toFixed(1)}</strong>
      <div className="h-xs overflow-hidden rounded-full bg-border" aria-hidden="true">
        <span className="score-fill" data-score={step} />
      </div>
    </InsetCard>
  )
}
