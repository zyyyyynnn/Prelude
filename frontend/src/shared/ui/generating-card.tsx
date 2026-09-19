import { RoseThree } from '@/shared/brand/RoseThree'
import type { ReactNode } from 'react'

/** The waiting surface for a background turn: brand mark, what is happening, and an
 *  indeterminate progress rail. */
export function GeneratingCard({ title, hint }: { title: ReactNode; hint: ReactNode }) {
  return (
    <div className="generating-card">
      <RoseThree className="mb-lg size-(--layout-generating-rose-inline-size) text-brand" />
      <h2 className="generating-title">{title}</h2>
      <p className="type-body mb-lg">{hint}</p>
      <div className="generating-progress-track">
        <div className="generating-progress-indicator" />
      </div>
    </div>
  )
}
