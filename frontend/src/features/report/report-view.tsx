import { parseInterviewReport } from './parse'
import {
  QuestionReviewList,
  ScoreCard,
  StagePerformanceList,
  Trait,
  TrainingPlan,
} from './report-sections'
import type { StructuredInterviewReport } from './types'

export function ReportPanel({ source }: { source: string }) {
  const parsed = parseInterviewReport(source)
  return (
    <div className="w-full bg-surface" data-slot="report-export">
      {parsed.kind === 'plain' ? (
        <article className="document-sheet">
          <pre
            className="m-0 break-inside-avoid font-sans text-md leading-copy wrap-anywhere whitespace-pre-wrap"
            data-slot="report-plain-text"
          >
            {parsed.text}
          </pre>
        </article>
      ) : (
        <StructuredReport report={parsed.report} />
      )}
    </div>
  )
}

export function StructuredReport({ report }: { report: StructuredInterviewReport }) {
  return (
    <article className="document-sheet w-full" data-slot="structured-report">
      <header className="grid min-w-0 gap-xs pb-xl" data-slot="report-hero">
        <p className="type-eyebrow">Interview Review</p>
        <h1 className="text-balance font-serif text-xl leading-display font-semibold">
          求职训练报告
        </h1>
        <p className="mt-sm max-w-(--content-report-reading-max-inline-size) text-pretty font-serif text-md leading-copy text-text-secondary">
          {report.summary.fitAssessment}
        </p>
      </header>
      <div className="report-columns gap-lg rounded-lg bg-surface-muted p-lg">
        <section className="min-w-0">
          <h2 className="type-title text-balance">行动建议</h2>
          <p className="mt-sm font-sans text-sm leading-copy text-text-secondary">
            {report.summary.actionRecommendation}
          </p>
        </section>
        <section className="min-w-0">
          <h2 className="type-title text-balance">总体风险</h2>
          <p className="mt-sm font-sans text-sm leading-copy text-text-secondary">
            {report.summary.overallRisk}
          </p>
        </section>
      </div>
      <ScoreCard report={report} />
      <StagePerformanceList stages={report.stagePerformances} />
      <QuestionReviewList reviews={report.questionReviews} />
      <section className="border-t border-border py-lg" data-slot="report-traits">
        <header className="mb-lg">
          <p className="type-eyebrow">能力沉淀</p>
          <h2 className="type-title text-balance">优势与短板</h2>
        </header>
        <div className="report-columns items-start gap-xl">
          <Trait title="核心优势" items={report.strengths} empty="暂无可归纳的优势。" />
          <Trait title="主要短板" items={report.weaknesses} empty="暂无已沉淀的薄弱点。" />
        </div>
      </section>
      <TrainingPlan plan={report.trainingPlan} />
      <section className="border-t border-border py-lg" data-slot="report-advice">
        <h2 className="type-title max-w-(--content-report-reading-max-inline-size) text-balance">
          总结建议
        </h2>
        <p className="mt-sm max-w-(--content-report-reading-max-inline-size) text-pretty font-sans text-sm leading-copy text-text-secondary">
          {report.finalAdvice}
        </p>
      </section>
    </article>
  )
}
