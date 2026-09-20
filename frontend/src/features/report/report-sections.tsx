import { useState } from 'react'
import { ChevronLeft, ChevronRight } from 'lucide-react'
import { cn } from '@/shared/lib/cn'
import { Button } from '@/shared/ui/button'
import { ScoreTile } from '@/shared/ui/score-tile'
import type {
  StructuredInterviewReport,
  StructuredQuestionReview,
  StructuredStagePerformance,
  StructuredTrainingPlan,
} from './types'

const stageLabels = {
  warmup: '破冰',
  technical: '技术问答',
  deep_dive: '深度追问',
  closing: '收尾复盘',
} as const

export function ScoreCard({ report }: { report: StructuredInterviewReport }) {
  const items = [
    ['技术能力', report.scores.technical],
    ['表达清晰度', report.scores.expression],
    ['逻辑思维', report.scores.logic],
  ] as const
  return (
    <section className="grid gap-lg border-t border-border py-lg">
      <header className="flex items-center justify-between gap-lg">
        <div className="grid gap-xs">
          <p className="type-eyebrow">能力画像</p>
          <h2 className="type-title text-balance">三维评分</h2>
        </div>
        <div className="flex items-baseline gap-xs font-serif text-text-secondary">
          <span>总体</span>
          <strong className="text-2xl font-semibold text-brand">
            {report.scores.overall.toFixed(1)}
          </strong>
          <small>/ 10</small>
        </div>
      </header>
      <div className="report-columns gap-md">
        {items.map(([label, value]) => (
          <ScoreTile key={label} label={label} value={value} />
        ))}
      </div>
    </section>
  )
}

export function StagePerformanceList({ stages }: { stages: StructuredStagePerformance[] }) {
  const [index, setIndex] = useState(0)
  if (!stages.length)
    return (
      <section className="grid gap-lg border-t border-border py-lg">
        <header className="grid gap-xs">
          <p className="type-eyebrow">阶段复盘</p>
          <h2 className="type-title text-balance">分阶段表现</h2>
        </header>
        <p className="font-sans text-sm leading-copy text-text-secondary">
          当前报告没有可复盘的阶段表现。
        </p>
      </section>
    )
  return (
    <section className="grid gap-lg border-t border-border py-lg">
      <header className="flex items-center justify-between gap-lg">
        <div className="grid gap-xs">
          <p className="type-eyebrow">阶段复盘</p>
          <h2 className="type-title text-balance">分阶段表现</h2>
        </div>
        <ReportCarouselNavigation
          ariaLabel="阶段复盘导航"
          index={index}
          count={stages.length}
          previousLabel="上一阶段"
          nextLabel="下一阶段"
          onPrevious={() => setIndex((value) => value - 1)}
          onNext={() => setIndex((value) => value + 1)}
        />
      </header>
      <div className="min-w-0">
        {stages.map((stage, stageIndex) => (
          <article
            className={cn(
              'gap-lg break-inside-avoid min-w-0 rounded-lg bg-surface-muted p-lg',
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
                  <span className="font-serif text-xs text-text-tertiary">
                    第 {String(stageIndex + 1).padStart(2, '0')} 阶段
                  </span>
                  <h3 className="type-subtitle">{stageLabels[stage.stageName]}</h3>
                </div>
                <span
                  className="shrink-0 whitespace-nowrap tabular-nums font-serif text-xs text-text-tertiary"
                  data-slot="stage-score"
                >
                  {stage.score == null ? '暂无评分' : `${stage.score.toFixed(1)} / 10`}
                </span>
              </header>
              <p className="max-w-(--content-report-reading-max-inline-size) text-pretty font-sans text-sm leading-copy text-text-secondary">
                {stage.summary}
              </p>
            </div>
            <div className="report-columns gap-lg" data-slot="stage-signals">
              <Signal title="正向信号" items={stage.positiveSignals} />
              <Signal title="风险信号" items={stage.negativeSignals} />
              <Signal title="改进建议" items={stage.improvementSuggestions} />
            </div>
          </article>
        ))}
      </div>
    </section>
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
      <ul className="list-plain grid gap-xs font-sans text-sm leading-copy text-text-secondary">
        {items.map((item) => (
          <li key={item}>{item}</li>
        ))}
      </ul>
    </section>
  ) : null
}

export function QuestionReviewList({ reviews }: { reviews: StructuredQuestionReview[] }) {
  const [index, setIndex] = useState(0)
  if (!reviews.length)
    return (
      <section className="grid gap-lg border-t border-border py-lg">
        <header className="grid gap-xs">
          <p className="type-eyebrow">回答证据</p>
          <h2 className="type-title text-balance">逐题复盘</h2>
        </header>
        <p className="font-sans text-sm leading-copy text-text-secondary">
          当前报告没有可复盘的有效回答。
        </p>
      </section>
    )
  const active = reviews[Math.min(index, reviews.length - 1)]
  return (
    <section className="grid gap-lg border-t border-border py-lg">
      <header className="flex items-center justify-between gap-lg">
        <div className="grid gap-xs">
          <p className="type-eyebrow">回答证据</p>
          <h2 className="type-title text-balance">逐题复盘</h2>
        </div>
        <ReportCarouselNavigation
          ariaLabel="逐题复盘导航"
          index={index}
          count={reviews.length}
          previousLabel="上一题"
          nextLabel="下一题"
          onPrevious={() => setIndex((value) => value - 1)}
          onNext={() => setIndex((value) => value + 1)}
        />
      </header>
      <article
        className="grid gap-lg break-inside-avoid min-w-0 rounded-lg bg-surface-muted p-lg print:mt-md"
        data-slot="question-review"
      >
        <div className="grid gap-md">
          <header className="flex items-center justify-between gap-lg">
            <span className="font-serif text-xs text-text-tertiary">
              第 {index + 1} 题 · {stageLabels[active.stageName]}
            </span>
            <span
              className="shrink-0 whitespace-nowrap tabular-nums font-serif text-xs text-text-tertiary"
              data-slot="review-score"
            >
              {active.score == null ? '暂无评分' : `${active.score.toFixed(1)} / 10`}
            </span>
          </header>
          <h3 className="type-subtitle">{active.question}</h3>
        </div>
        <dl className="grid gap-md">
          <ReviewDetail label="回答摘要" value={active.answerSummary} />
          <ReviewDetail label="评分依据" value={active.scoringReason} />
          <ReviewDetail label="改进建议" value={active.improvementSuggestion} />
        </dl>
      </article>
    </section>
  )
}

export function ReviewDetail({ label, value }: { label: string; value: string }) {
  return (
    <div className="review-detail-grid">
      <dt className="font-serif text-sm text-text-secondary">{label}</dt>
      <dd className="m-0 font-sans text-sm leading-copy text-text-secondary">{value}</dd>
    </div>
  )
}

export function TrainingPlan({ plan }: { plan: StructuredTrainingPlan }) {
  const groups = [
    ['3 天补强', plan.threeDay],
    ['7 天专项', plan.sevenDay],
    ['下次模拟重点', plan.nextInterviewFocus],
  ] as const
  return (
    <section className="grid gap-lg border-t border-border py-lg">
      <header className="grid gap-xs">
        <p className="type-eyebrow">下一步行动</p>
        <h2 className="type-title text-balance">训练计划</h2>
      </header>
      <div className="report-columns gap-xl" data-slot="training-plan-grid">
        {groups.map(([title, items], index) => (
          <section className="grid min-w-0 gap-sm break-inside-avoid" key={title}>
            <span className="font-sans text-xs tabular-nums text-text-tertiary" aria-hidden="true">
              {String(index + 1).padStart(2, '0')}
            </span>
            <h3 className="type-subtitle">{title}</h3>
            <ol className="list-plain grid gap-xs font-sans text-sm leading-copy text-text-secondary">
              {(items.length ? items : ['按逐题复盘中的建议完成一次定向练习。']).map((item) => (
                <li key={item}>{item}</li>
              ))}
            </ol>
          </section>
        ))}
      </div>
    </section>
  )
}

export function Trait({ title, items, empty }: { title: string; items: string[]; empty: string }) {
  return (
    <section className="grid min-w-0 gap-sm">
      <h3 className="type-subtitle">{title}</h3>
      {items.length ? (
        <ul className="list-plain grid gap-sm font-sans text-sm leading-copy text-text-secondary">
          {items.map((item) => (
            <li key={item}>{item}</li>
          ))}
        </ul>
      ) : (
        <p className="font-sans text-sm leading-copy text-text-secondary">{empty}</p>
      )}
    </section>
  )
}
