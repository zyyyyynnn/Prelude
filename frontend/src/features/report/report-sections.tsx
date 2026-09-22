import { useState, type ReactNode } from 'react'
import { ChevronLeft, ChevronRight } from 'lucide-react'
import { cn } from '@/shared/lib/cn'
import { Button, ScoreTile } from '@/shared/ui'
import type { ReportCopy } from './copy'
import type {
  StructuredInterviewReport,
  StructuredQuestionReview,
  StructuredStagePerformance,
  StructuredTrainingPlan,
} from './types'

/** A report section: the hairline that separates it from the one above, the room that line
 *  needs on both sides, and the gap binding its heading to its content. `gap="sm"` is the
 *  closing advice block, which carries no heading of its own. */
export function ReportSection({
  children,
  gap = 'lg',
  slot,
}: {
  children: ReactNode
  gap?: 'sm' | 'lg'
  slot?: string
}) {
  return (
    <section
      className={cn('grid border-t border-border py-lg', gap === 'lg' ? 'gap-lg' : 'gap-sm')}
      data-slot={slot}
    >
      {children}
    </section>
  )
}

/** A report section opens the same way: the eyebrow naming the dimension, then the title
 *  stating it. With an action the pair sits on one line against it; without one the heading
 *  is its own block. Written out per section, the eyebrow weight and the balancing drifted. */
export function SectionHeading({
  eyebrow,
  title,
  action,
}: {
  eyebrow: string
  title: string
  action?: ReactNode
}) {
  const heading = (
    <>
      <p className="type-eyebrow">{eyebrow}</p>
      <h2 className="type-title text-balance">{title}</h2>
    </>
  )
  return action ? (
    <header className="flex items-center justify-between gap-lg">
      <div className="grid gap-xs">{heading}</div>
      {action}
    </header>
  ) : (
    <header className="grid gap-xs">{heading}</header>
  )
}

export function ScoreCard({
  report,
  copy,
}: {
  report: StructuredInterviewReport
  copy: ReportCopy
}) {
  const items = [
    [copy.scores.dimensions[0], report.scores.technical],
    [copy.scores.dimensions[1], report.scores.expression],
    [copy.scores.dimensions[2], report.scores.logic],
  ] as const
  return (
    <ReportSection>
      <SectionHeading
        eyebrow={copy.scores.eyebrow}
        title={copy.scores.title}
        action={
          <div className="flex items-baseline gap-xs font-serif text-text-secondary">
            <span>{copy.scores.overall}</span>
            <strong className="text-2xl font-semibold text-brand">
              {report.scores.overall.toFixed(1)}
            </strong>
            <small>/ 10</small>
          </div>
        }
      />
      <div className="report-columns gap-md">
        {items.map(([label, value]) => (
          <ScoreTile key={label} label={label} value={value} />
        ))}
      </div>
    </ReportSection>
  )
}

export function StagePerformanceList({
  stages,
  copy,
}: {
  stages: StructuredStagePerformance[]
  copy: ReportCopy
}) {
  const [index, setIndex] = useState(0)
  if (!stages.length)
    return (
      <ReportSection>
        <SectionHeading eyebrow={copy.stages.eyebrow} title={copy.stages.title} />
        <p className="type-copy">{copy.stages.empty}</p>
      </ReportSection>
    )
  return (
    <ReportSection>
      <SectionHeading
        eyebrow={copy.stages.eyebrow}
        title={copy.stages.title}
        action={
          <ReportCarouselNavigation
            ariaLabel="阶段复盘导航"
            index={index}
            count={stages.length}
            previousLabel="上一阶段"
            nextLabel="下一阶段"
            onPrevious={() => setIndex((value) => value - 1)}
            onNext={() => setIndex((value) => value + 1)}
          />
        }
      />
      <div className="min-w-0">
        {stages.map((stage, stageIndex) => (
          <article
            className={cn(
              'gap-lg break-inside-avoid min-w-0 inset-card-lg',
              stageIndex === index ? 'grid' : 'hidden',
              'print:block',
              stageIndex > 0 && 'print:mt-md',
            )}
            aria-hidden={stageIndex !== index}
            data-state={stageIndex === index ? 'active' : 'inactive'}
            data-slot="stage-performance"
            key={stage.stageName}
          >
            <div className="grid gap-md">
              <header className="flex items-start justify-between gap-md">
                <div className="grid gap-xs">
                  <span className="type-caption">
                    第 {String(stageIndex + 1).padStart(2, '0')} 阶段
                  </span>
                  <h3 className="type-subtitle">{copy.stages.stageLabels[stage.stageName]}</h3>
                </div>
                <span
                  className="type-caption shrink-0 whitespace-nowrap tabular-nums"
                  data-slot="stage-score"
                >
                  {stage.score == null ? copy.noScore : `${stage.score.toFixed(1)} / 10`}
                </span>
              </header>
              <p className="type-reading">{stage.summary}</p>
            </div>
            <div className="report-columns gap-lg" data-slot="stage-signals">
              <Signal title={copy.stages.signals.positive} items={stage.positiveSignals} />
              <Signal title={copy.stages.signals.risk} items={stage.negativeSignals} />
              <Signal
                title={copy.stages.signals.improvement}
                items={stage.improvementSuggestions}
              />
            </div>
          </article>
        ))}
      </div>
    </ReportSection>
  )
}

export function ReportCarouselNavigation({
  ariaLabel,
  index,
  count,
  previousLabel,
  nextLabel,
  onPrevious,
  onNext,
}: {
  ariaLabel: string
  index: number
  count: number
  previousLabel: string
  nextLabel: string
  onPrevious: () => void
  onNext: () => void
}) {
  return (
    <div className="flex items-center gap-xs print:hidden" role="group" aria-label={ariaLabel}>
      <span
        className="min-w-(--layout-report-counter-min-inline-size) text-center font-sans text-sm text-text-secondary"
        aria-live="polite"
      >
        {index + 1} / {count}
      </span>
      <Button
        type="button"
        size="icon"
        variant="ghost"
        aria-label={previousLabel}
        disabled={index === 0}
        onClick={onPrevious}
      >
        <ChevronLeft />
      </Button>
      <Button
        type="button"
        size="icon"
        variant="ghost"
        aria-label={nextLabel}
        disabled={index === count - 1}
        onClick={onNext}
      >
        <ChevronRight />
      </Button>
    </div>
  )
}

export function Signal({ title, items }: { title: string; items: string[] }) {
  return items.length ? (
    <section className="grid min-w-0 gap-sm">
      <h4 className="font-serif text-sm leading-base text-text-primary">{title}</h4>
      <ul className="type-copy list-plain grid gap-xs">
        {items.map((item) => (
          <li key={item}>{item}</li>
        ))}
      </ul>
    </section>
  ) : null
}

export function QuestionReviewList({
  reviews,
  copy,
}: {
  reviews: StructuredQuestionReview[]
  copy: ReportCopy
}) {
  const [index, setIndex] = useState(0)
  if (!reviews.length)
    return (
      <ReportSection>
        <SectionHeading eyebrow={copy.reviews.eyebrow} title={copy.reviews.title} />
        <p className="type-copy">{copy.reviews.empty}</p>
      </ReportSection>
    )
  const active = reviews[Math.min(index, reviews.length - 1)]
  return (
    <ReportSection>
      <SectionHeading
        eyebrow={copy.reviews.eyebrow}
        title={copy.reviews.title}
        action={
          <ReportCarouselNavigation
            ariaLabel="逐题复盘导航"
            index={index}
            count={reviews.length}
            previousLabel="上一题"
            nextLabel="下一题"
            onPrevious={() => setIndex((value) => value - 1)}
            onNext={() => setIndex((value) => value + 1)}
          />
        }
      />
      <article
        className="grid gap-lg break-inside-avoid min-w-0 inset-card-lg print:mt-md"
        data-slot="question-review"
      >
        <div className="grid gap-md">
          <header className="flex items-center justify-between gap-lg">
            <span className="type-caption">
              第 {index + 1} 题 · {copy.stages.stageLabels[active.stageName]}
            </span>
            <span
              className="type-caption shrink-0 whitespace-nowrap tabular-nums"
              data-slot="review-score"
            >
              {active.score == null ? copy.noScore : `${active.score.toFixed(1)} / 10`}
            </span>
          </header>
          <h3 className="type-subtitle">{active.question}</h3>
        </div>
        <dl className="grid gap-md">
          <ReviewDetail label={copy.reviews.fields.summary} value={active.answerSummary} />
          <ReviewDetail label={copy.reviews.fields.reason} value={active.scoringReason} />
          <ReviewDetail
            label={copy.reviews.fields.improvement}
            value={active.improvementSuggestion}
          />
        </dl>
      </article>
    </ReportSection>
  )
}

export function ReviewDetail({ label, value }: { label: string; value: string }) {
  return (
    <div className="review-detail-grid">
      <dt className="font-serif text-sm text-text-secondary">{label}</dt>
      <dd className="type-copy m-0">{value}</dd>
    </div>
  )
}

export function TrainingPlan({ plan, copy }: { plan: StructuredTrainingPlan; copy: ReportCopy }) {
  const groups = [
    [copy.plan.groups[0], plan.threeDay],
    [copy.plan.groups[1], plan.sevenDay],
    [copy.plan.groups[2], plan.nextInterviewFocus],
  ] as const
  return (
    <ReportSection>
      <SectionHeading eyebrow={copy.plan.eyebrow} title={copy.plan.title} />
      <div className="report-columns gap-xl" data-slot="training-plan-grid">
        {groups.map(([title, items], index) => (
          <section className="grid min-w-0 gap-sm break-inside-avoid" key={title}>
            <span className="font-sans text-xs tabular-nums text-text-tertiary" aria-hidden="true">
              {String(index + 1).padStart(2, '0')}
            </span>
            <h3 className="type-subtitle">{title}</h3>
            <ol className="type-copy list-plain grid gap-xs">
              {(items.length ? items : [copy.plan.fallback]).map((item) => (
                <li key={item}>{item}</li>
              ))}
            </ol>
          </section>
        ))}
      </div>
    </ReportSection>
  )
}

export function Trait({ title, items, empty }: { title: string; items: string[]; empty: string }) {
  return (
    <section className="grid min-w-0 gap-sm">
      <h3 className="type-subtitle">{title}</h3>
      {items.length ? (
        <ul className="type-copy list-plain grid gap-sm">
          {items.map((item) => (
            <li key={item}>{item}</li>
          ))}
        </ul>
      ) : (
        <p className="type-copy">{empty}</p>
      )}
    </section>
  )
}
