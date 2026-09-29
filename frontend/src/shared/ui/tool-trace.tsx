import { useState } from 'react'
import {
  RiArrowDownSLine,
  RiChat1Line,
  RiFileTextLine,
  RiFileEditLine,
  RiFileSearchLine,
  RiGlobalLine,
  RiNodeTree,
  RiPencilLine,
  RiRefreshLine,
  RiSearchLine,
  RiTerminalBoxLine,
  RiWindowLine,
} from '@remixicon/react'
import { cn } from '@/shared/lib/cn'
import { Icon } from './Icon'
import { LoadingIndicator } from './loading-indicator'

/*
 * Tool-call trace rows adapted from Beautiful UI's Tool Chips.
 * Copyright (c) 2026 Shane Levine. Licensed under the MIT License.
 * Structure kept from the adopted component: a run header whose chevron trails its summary,
 * one compact content-width row per call whose own icon leads and whose chevron trails its
 * payload, and per-file diff counts on the row that wrote the file. Prelude owns the closing
 * status line and the left spine that hangs every step off the header icon's centre.
 *
 * Rows are content-width, not a table of fixed columns. Icons share a left edge and labels
 * share the next one because every row starts with the same mark slot; trailing payload and
 * the disclosure chevron follow their own content, which is what keeps a short verb from
 * stretching a hundred-odd pixels of empty air before its chevron.
 */

/** What a step in the trace can be. The vocabulary is owned here: a feature that
 *  stores a step kind persists this value, never a parallel copy of the list. */
export type ToolTraceIcon =
  | 'think'
  | 'write'
  | 'run'
  | 'read'
  | 'search'
  | 'edit'
  | 'find'
  | 'update'
  | 'browse'
  | 'tool'

/** A file a step touched, with the line counts measured from the real edit. */
export type ToolTraceFile = {
  name: string
  add: number
  del: number
}

export type ToolTraceStep = {
  id: string
  icon: ToolTraceIcon
  text: string
  /** A failed step keeps its own row red and the group still closes as done — the run
   *  completed, this one call inside it did not. */
  state?: 'default' | 'error'
  badge?: string
  badgeTone?: 'add' | 'error' | 'default'
  chips?: string[]
  files?: ToolTraceFile[]
  detail?: string[]
}

export type ToolTraceLabels = {
  summary: string
  status: string
}

const STEP_ICON = {
  think: RiChat1Line,
  write: RiPencilLine,
  run: RiTerminalBoxLine,
  read: RiFileTextLine,
  search: RiSearchLine,
  edit: RiFileEditLine,
  find: RiFileSearchLine,
  update: RiRefreshLine,
  browse: RiGlobalLine,
  tool: RiWindowLine,
} as const

export function ToolTrace({
  steps,
  labels,
  running = false,
  defaultOpenRows,
  className,
}: {
  steps: ToolTraceStep[]
  labels: ToolTraceLabels
  running?: boolean
  /** Rows whose detail starts revealed. The lab needs this so a still frame shows the
   *  expanded state; the product can adopt it when a step failed or the run is short. */
  defaultOpenRows?: string[]
  className?: string
}) {
  const [open, setOpen] = useState(true)
  const [openRows, setOpenRows] = useState<Set<string>>(() => new Set(defaultOpenRows ?? []))
  const failedSteps = steps.filter((step) => step.state === 'error').length

  const toggleRow = (id: string) =>
    setOpenRows((current) => {
      const next = new Set(current)
      if (next.has(id)) next.delete(id)
      else next.add(id)
      return next
    })

  return (
    <div className={cn('tool-trace', className)} data-open={open ? 'true' : 'false'}>
      <button
        type="button"
        aria-expanded={open}
        onClick={() => setOpen((value) => !value)}
        className="tool-trace__summary"
      >
        <span className="tool-trace__mark">
          <Icon as={RiNodeTree} size="sm" />
        </span>
        <span className="tool-trace__summary-text">{labels.summary}</span>
        <span className="tool-trace__chevron">
          <Icon as={RiArrowDownSLine} size="sm" />
        </span>
      </button>

      <div className="tool-trace__body">
        <div className="tool-trace__clip">
          <ol className="tool-trace__list">
            {steps.map((step) => {
              const rowOpen = openRows.has(step.id)
              const StepIcon = STEP_ICON[step.icon]
              const expandable = Boolean(step.detail?.length)
              const failed = step.state === 'error'
              return (
                <li
                  key={step.id}
                  className="tool-trace__item"
                  data-open={rowOpen ? 'true' : 'false'}
                  data-expandable={expandable ? 'true' : 'false'}
                  data-state={failed ? 'error' : 'default'}
                >
                  <button
                    type="button"
                    aria-expanded={expandable ? rowOpen : undefined}
                    disabled={!expandable}
                    onClick={() => toggleRow(step.id)}
                    className="tool-trace__row"
                  >
                    <span className="tool-trace__mark">
                      <Icon as={StepIcon} size="sm" />
                    </span>
                    <span className="tool-trace__text">{step.text}</span>
                    {/* One payload slot per row: chips, the file diff and the badge ride
                        together so the row's grid keeps exactly four items — mark, label,
                        payload, chevron — however many chips a step carries. Emitted as
                        loose spans they overrun the four columns and the chevron wraps to
                        a second line under the row. */}
                    {(step.chips?.length || step.files?.length || step.badge) && (
                      <span className="tool-trace__payload">
                        {step.chips?.map((chip) => (
                          <span key={chip} className="tool-trace__chip">
                            {chip}
                          </span>
                        ))}
                        {step.files?.map((file) => (
                          <span key={file.name} className="tool-trace__file">
                            <span className="tool-trace__mark">
                              <Icon as={RiFileTextLine} size="sm" />
                            </span>
                            <span className="tool-trace__file-name">{file.name}</span>
                            <span className="tool-trace__file-add">+{file.add}</span>
                            {file.del > 0 && (
                              <span className="tool-trace__file-del">−{file.del}</span>
                            )}
                          </span>
                        ))}
                        {step.badge && (
                          <span
                            className="tool-trace__badge"
                            data-tone={step.badgeTone ?? 'default'}
                          >
                            {step.badge}
                          </span>
                        )}
                      </span>
                    )}
                    {expandable && (
                      <span
                        className={cn(
                          'tool-trace__chevron',
                          !rowOpen && 'tool-trace__chevron--closed',
                        )}
                      >
                        <Icon as={RiArrowDownSLine} size="sm" />
                      </span>
                    )}
                  </button>
                  {expandable && (
                    <div className="tool-trace__detail">
                      <div className="tool-trace__clip">
                        <div className="tool-trace__detail-body">
                          {step.detail?.map((line) => (
                            <span key={line}>{line}</span>
                          ))}
                        </div>
                      </div>
                    </div>
                  )}
                </li>
              )
            })}
          </ol>
        </div>
      </div>

      {/* The closing line sits on the spine axis — the same x the summary icon's centre and
          the list rail use — because it belongs to the run, not to the indented step rows.
          A running run carries the loading mark to the left of its words: the same quiet
          affordance the standalone indicator uses, so "running" reads one way everywhere.
          A run that finished with failed steps discloses the count here — the aggregate
          copy never reports a bare completion label over a step that blew up. */}
      <div
        className="tool-trace__status"
        data-running={running ? 'true' : 'false'}
        role="status"
        aria-atomic="true"
      >
        {running && (
          <span className="tool-trace__mark">
            <LoadingIndicator />
          </span>
        )}
        <span>{labels.status}</span>
        {failedSteps > 0 && (
          <span className="tool-trace__status-failure">{` · ${failedSteps} 项失败`}</span>
        )}
      </div>
    </div>
  )
}
