import { InsetCard, LoadingIndicator, ToolTrace } from '@/shared/ui'
import type { ToolTraceLabels } from '@/shared/ui'
import type { ResumeAssistantMessage, ResumeToolGroup, ResumeTurn } from './types'
import { StickyInstruction } from './sticky-instruction'

/** The run's own duration, read from the turn that owns it. Finished runs carry it as a
 *  plain measured value; a running turn keeps ticking from its start. */
function formatElapsed(startedAt?: string | null, completedAt?: string | null): string | null {
  if (!startedAt) return null
  const started = Date.parse(startedAt)
  if (Number.isNaN(started)) return null
  const finished = completedAt ? Date.parse(completedAt) : Date.now()
  if (Number.isNaN(finished)) return null
  const total = Math.max(0, (finished - started) / 1000)
  if (total < 60) return `${Math.round(total)}s`
  return `${Math.floor(total / 60)}m ${Math.round(total % 60)}s`
}

/**
 * One tool-call set owned by one assistant message. Renders the agent activity
 * trace: summary head, indented step list, status line. No card shell.
 */
function ToolGroupView({
  group,
  startedAt,
  completedAt,
}: {
  group: ResumeToolGroup
  startedAt?: string | null
  completedAt?: string | null
}) {
  const elapsed = formatElapsed(startedAt, completedAt)
  const labels: ToolTraceLabels = {
    summary: group.summary,
    status:
      group.status === 'running'
        ? elapsed
          ? `正在处理 · ${elapsed}`
          : '正在处理'
        : elapsed
          ? `已完成 · ${elapsed}`
          : '已完成',
  }
  return (
    <ToolTrace
      running={group.status === 'running'}
      labels={labels}
      steps={group.steps.map((step) => ({
        id: String(step.id),
        icon: step.icon,
        text: step.text,
        state: step.state,
        badge: step.badge,
        badgeTone: step.badgeTone,
        chips: step.chips,
        files: step.files,
        detail: step.detail,
      }))}
    />
  )
}

function AssistantMessageView({
  message,
  startedAt,
  completedAt,
}: {
  message: ResumeAssistantMessage
  startedAt?: string | null
  completedAt?: string | null
}) {
  return (
    <div className="mx-auto w-full max-w-(--layout-workspace-content-max-inline-size) grid gap-sm">
      <InsetCard>
        <span className="type-label">简历制作助手</span>
        <p className="type-body whitespace-pre-wrap">{message.content}</p>
      </InsetCard>
      {message.toolCalls && (
        <ToolGroupView group={message.toolCalls} startedAt={startedAt} completedAt={completedAt} />
      )}
    </div>
  )
}

export function ResumeStream({ turns }: { turns: ResumeTurn[] }) {
  return (
    <div className="grid gap-lg">
      {turns.map((turn) => (
        <section key={turn.id} className="grid gap-md" data-turn-id={turn.id}>
          <StickyInstruction instruction={turn.instruction} />
          {turn.messages.map((message) => (
            <AssistantMessageView
              key={message.id}
              message={message}
              startedAt={turn.startedAt}
              completedAt={turn.completedAt}
            />
          ))}
          {turn.status === 'running' && turn.messages.length === 0 && (
            <div className="mx-auto w-full max-w-(--layout-workspace-content-max-inline-size)">
              <LoadingIndicator label="正在处理" />
            </div>
          )}
        </section>
      ))}
    </div>
  )
}
