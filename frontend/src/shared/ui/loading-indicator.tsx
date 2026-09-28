import { cn } from '@/shared/lib/cn'

/*
 * The running affordance for a status line: the pixel-grid mark at the left of an
 * optional label. Adapted from Beautiful UI's Loading State.
 * Copyright (c) 2026 Shane Levine. Licensed under the MIT License.
 * Only the Drive pattern is adopted — the product needs one quiet running indicator
 * beside its status label, not a gallery of variants. The elapsed readout lives with
 * the run that owns it (the trace's closing line), never as a private stopwatch here.
 */
export function LoadingIndicator({ label, className }: { label?: string; className?: string }) {
  return (
    <span
      role={label ? 'status' : undefined}
      aria-hidden={label ? undefined : true}
      className={cn('inline-flex w-fit items-center', label && 'gap-sm', className)}
    >
      <span aria-hidden className="loading-pixel-grid">
        {Array.from({ length: 9 }, (_, index) => (
          <span key={index} className="loading-pixel" />
        ))}
      </span>
      {label && <span className="type-meta">{label}</span>}
    </span>
  )
}
