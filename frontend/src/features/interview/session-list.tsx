import type { ReactNode } from 'react'
import { SessionRow, type SessionRowState } from './components/session-row'

export interface SessionListRow {
  key: string | number
  name: string
  finished?: boolean
  pinned?: boolean
  state?: SessionRowState
  onOpen: () => void
  onTogglePin: () => void
  onRemove: () => void
}

export function SessionGroupLabel({ children }: { children: ReactNode }) {
  return <p className="mx-sm text-xs font-semibold tracking-label text-text-tertiary">{children}</p>
}

export function SessionGroup({
  label,
  rows,
  emptyLabel,
}: {
  label: string
  rows: SessionListRow[]
  emptyLabel: string
}) {
  return (
    <section className="session-group" aria-label={label}>
      <SessionGroupLabel>{label}</SessionGroupLabel>
      {rows.length ? (
        <ul className="list-plain flex flex-col gap-sm">
          {rows.map(({ key, ...row }) => (
            <SessionRow key={key} {...row} />
          ))}
        </ul>
      ) : (
        <SessionGroupLabel>{emptyLabel}</SessionGroupLabel>
      )}
    </section>
  )
}
