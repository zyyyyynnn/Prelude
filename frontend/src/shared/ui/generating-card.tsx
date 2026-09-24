import { RoseThree } from '@/shared/brand/RoseThree'
import type { ReactNode } from 'react'

/** The waiting surface for a background turn: brand mark, what is happening, and an
 *  indeterminate progress rail. */
export function GeneratingCard({ title, hint }: { title: ReactNode; hint: ReactNode }) {
  return (
    <div className="generating-card">
      <RoseThree className="size-(--layout-generating-rose-inline-size) text-brand" />
      <div className="grid gap-xs">
        <h2 className="generating-title">{title}</h2>
        <p className="type-body">{hint}</p>
      </div>
      <div className="generating-progress-track">
        <div className="generating-progress-indicator" />
      </div>
    </div>
  )
}

/** The generating card as the product presents it: alone, centred on a plain sheet that fills
 *  whatever region is waiting. Both axes are declared so the card centres the same way in a
 *  flex column and in a grid cell — the two places that need it. */
export function GeneratingSurface({ title, hint }: { title: ReactNode; hint: ReactNode }) {
  return (
    <div className="flex min-h-0 w-full flex-1 items-center justify-center bg-surface p-xl">
      <GeneratingCard title={title} hint={hint} />
    </div>
  )
}
