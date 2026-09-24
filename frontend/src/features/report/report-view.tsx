import { reportCopy } from './copy'
import { parseInterviewReport } from './parse'
import {
  QuestionReviewList,
  ReportSection,
  ScoreCard,
  SectionHeading,
  StagePerformanceList,
  Trait,
  TrainingPlan,
} from './report-sections'
import type { ReportCopy } from './copy'
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

export function StructuredReport({
  report,
  copy = reportCopy,
}: {
  report: StructuredInterviewReport
  copy?: ReportCopy
}) {
  return (
    <article className="document-sheet w-full" data-slot="structured-report">
      <header className="grid min-w-0 gap-sm pb-lg" data-slot="report-hero">
        <div className="grid gap-xs">
          <p className="type-eyebrow">{copy.eyebrow}</p>
          <h1 className="type-document-title text-balance">{copy.title}</h1>
        </div>
        <p className="max-w-(--content-reading-max-inline-size) text-pretty font-serif text-md leading-copy text-text-secondary">
          {report.summary.fitAssessment}
        </p>
      </header>
      <div className="document-columns gap-lg inset-card-lg">
        <section className="grid min-w-0 gap-sm">
          <h2 className="type-title text-balance">{copy.actionTitle}</h2>
          <p className="type-copy">{report.summary.actionRecommendation}</p>
        </section>
        <section className="grid min-w-0 gap-sm">
          <h2 className="type-title text-balance">{copy.riskTitle}</h2>
          <p className="type-copy">{report.summary.overallRisk}</p>
        </section>
      </div>
      <ScoreCard report={report} copy={copy} />
      <StagePerformanceList stages={report.stagePerformances} copy={copy} />
      <QuestionReviewList reviews={report.questionReviews} copy={copy} />
      <ReportSection slot="report-traits">
        <SectionHeading eyebrow={copy.traits.eyebrow} title={copy.traits.title} />
        <div className="document-columns items-start gap-xl">
          <Trait
            title={copy.traits.strengths}
            items={report.strengths}
            empty={copy.traits.strengthsEmpty}
          />
          <Trait
            title={copy.traits.weaknesses}
            items={report.weaknesses}
            empty={copy.traits.weaknessesEmpty}
          />
        </div>
      </ReportSection>
      <TrainingPlan plan={report.trainingPlan} copy={copy} />
      <ReportSection slot="report-advice" gap="sm">
        <h2 className="type-title max-w-(--content-reading-max-inline-size) text-balance">
          {copy.adviceTitle}
        </h2>
        <p className="type-reading">{report.finalAdvice}</p>
      </ReportSection>
    </article>
  )
}
