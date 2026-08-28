import { useState } from 'react'
import { ChevronLeft, ChevronRight, Download } from 'lucide-react'
import { Button } from '@/shared/ui'
import { useFeedback } from '@/shared/ui/feedback'
import { parseInterviewReport } from './parse-interview-report'
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

export function ReportPanel({ source }: { source: string }) {
  const parsed = parseInterviewReport(source)
  const [exporting, setExporting] = useState(false)
  const feedback = useFeedback()
  async function exportReport() {
    setExporting(true)
    try {
      const { printReport } = await import('./print-report')
      await printReport('面试训练报告')
      feedback.notify('已打开系统打印窗口', 'success')
    } catch (error) {
      feedback.notify(error instanceof Error ? error.message : '报告导出失败', 'error')
    } finally {
      setExporting(false)
    }
  }
  return (
    <div className="report-export-surface">
      <div className="report-export-actions">
        <Button variant="secondary" loading={exporting} onClick={() => void exportReport()}>
          <Download size={15} />
          导出 PDF
        </Button>
      </div>
      {parsed.kind === 'plain' ? (
        <article className="report-plain-surface">
          <pre className="report-plain-text">{parsed.text}</pre>
        </article>
      ) : (
        <StructuredReport report={parsed.report} />
      )}
    </div>
  )
}

function StructuredReport({ report }: { report: StructuredInterviewReport }) {
  return (
    <article className="structured-report">
      <header className="structured-report__hero">
        <p>Interview Review</p>
        <h1>求职训练报告</h1>
        <p className="structured-report__lede">{report.summary.fitAssessment}</p>
      </header>
      <div className="structured-report__summary">
        <section>
          <h2>行动建议</h2>
          <p>{report.summary.actionRecommendation}</p>
        </section>
        <section>
          <h2>总体风险</h2>
          <p>{report.summary.overallRisk}</p>
        </section>
      </div>
      <ScoreCard report={report} />
      <StagePerformanceList stages={report.stagePerformances} />
      <QuestionReviewList reviews={report.questionReviews} />
      <section className="report-section structured-report__traits">
        <header>
          <p>能力沉淀</p>
          <h2>优势与短板</h2>
        </header>
        <div>
          <Trait title="核心优势" items={report.strengths} empty="暂无可归纳的优势。" />
          <Trait title="主要短板" items={report.weaknesses} empty="暂无已沉淀的薄弱点。" />
        </div>
      </section>
      <TrainingPlan plan={report.trainingPlan} />
      <section className="report-section structured-report__advice">
        <p>总结建议</p>
        <h2>{report.finalAdvice}</h2>
      </section>
    </article>
  )
}

function ScoreCard({ report }: { report: StructuredInterviewReport }) {
  const items = [
    ['技术能力', report.scores.technical],
    ['表达清晰度', report.scores.expression],
    ['逻辑思维', report.scores.logic],
  ] as const
  return (
    <section className="report-section report-scores">
      <header className="report-section__header">
        <div>
          <h2>三维评分</h2>
        </div>
        <div className="report-scores__overall">
          <span>总体</span>
          <strong>{report.scores.overall.toFixed(1)}</strong>
          <small>/ 10</small>
        </div>
      </header>
      <div className="report-scores__grid">
        {items.map(([label, value]) => (
          <div className="report-score-item" key={label}>
            <span>{label}</span>
            <strong>{value.toFixed(1)}</strong>
            <div className="report-score-item__track" aria-hidden="true">
              <span style={{ inlineSize: `${value * 10}%` }} />
            </div>
          </div>
        ))}
      </div>
    </section>
  )
}

function StagePerformanceList({ stages }: { stages: StructuredStagePerformance[] }) {
  return (
    <section className="report-section">
      <header className="report-section__header">
        <div>
          <p>阶段复盘</p>
          <h2>分阶段表现</h2>
        </div>
      </header>
      <div className="stage-performance-list">
        {stages.map((stage) => (
          <article className="stage-performance" key={stage.stageName}>
            <header>
              <h3>{stageLabels[stage.stageName]}</h3>
              <span className="status-badge">
                {stage.score == null ? '暂无评分' : `${stage.score.toFixed(1)} / 10`}
              </span>
            </header>
            <p>{stage.summary}</p>
            <div className="stage-performance__signals">
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

function Signal({ title, items }: { title: string; items: string[] }) {
  return items.length ? (
    <section className="stage-performance__signal">
      <h4>{title}</h4>
      <ul>
        {items.map((item) => (
          <li key={item}>{item}</li>
        ))}
      </ul>
    </section>
  ) : null
}

function QuestionReviewList({ reviews }: { reviews: StructuredQuestionReview[] }) {
  const [index, setIndex] = useState(0)
  if (!reviews.length)
    return (
      <section className="report-section">
        <header>
          <p>回答证据</p>
          <h2>逐题复盘</h2>
        </header>
        <p>当前报告没有可复盘的有效回答。</p>
      </section>
    )
  const active = reviews[Math.min(index, reviews.length - 1)]
  return (
    <section className="report-section question-review-carousel">
      <header className="report-section__header">
        <div>
          <p>回答证据</p>
          <h2>逐题复盘</h2>
        </div>
        <div className="question-review-carousel__nav" aria-label="逐题复盘导航">
          <span className="question-review-carousel__counter" aria-live="polite">
            {index + 1} / {reviews.length}
          </span>
          <Button
            size="icon"
            variant="ghost"
            aria-label="上一题"
            disabled={index === 0}
            onClick={() => setIndex((value) => value - 1)}
          >
            <ChevronLeft size={16} />
          </Button>
          <Button
            size="icon"
            variant="ghost"
            aria-label="下一题"
            disabled={index === reviews.length - 1}
            onClick={() => setIndex((value) => value + 1)}
          >
            <ChevronRight size={16} />
          </Button>
        </div>
      </header>
      <article className="question-review">
        <header>
          <span>
            第 {index + 1} 题 · {stageLabels[active.stageName]}
          </span>
          <span className="status-badge">
            {active.score == null ? '暂无评分' : `${active.score.toFixed(1)} / 10`}
          </span>
        </header>
        <div className="question-review__body">
          <h3>{active.question}</h3>
          <dl>
            <ReviewDetail label="回答摘要" value={active.answerSummary} />
            <ReviewDetail label="评分依据" value={active.scoringReason} />
            <ReviewDetail label="改进建议" value={active.improvementSuggestion} />
          </dl>
        </div>
      </article>
    </section>
  )
}

function ReviewDetail({ label, value }: { label: string; value: string }) {
  return (
    <div>
      <dt>{label}</dt>
      <dd>{value}</dd>
    </div>
  )
}

function TrainingPlan({ plan }: { plan: StructuredTrainingPlan }) {
  const groups = [
    ['3 天补强', plan.threeDay],
    ['7 天专项', plan.sevenDay],
    ['下次模拟重点', plan.nextInterviewFocus],
  ] as const
  return (
    <section className="report-section training-plan">
      <header>
        <p>下一步行动</p>
        <h2>训练计划</h2>
      </header>
      <div className="training-plan__grid">
        {groups.map(([title, items], index) => (
          <section className="training-plan__group" key={title}>
            <span className="training-plan__step" aria-hidden="true">
              {String(index + 1).padStart(2, '0')}
            </span>
            <div>
              <h3>{title}</h3>
              <ol>
                {(items.length ? items : ['按逐题复盘中的建议完成一次定向练习。']).map((item) => (
                  <li key={item}>{item}</li>
                ))}
              </ol>
            </div>
          </section>
        ))}
      </div>
    </section>
  )
}

function Trait({ title, items, empty }: { title: string; items: string[]; empty: string }) {
  return (
    <section className="structured-report__trait">
      <h3>{title}</h3>
      {items.length ? (
        <ul>
          {items.map((item) => (
            <li key={item}>{item}</li>
          ))}
        </ul>
      ) : (
        <p>{empty}</p>
      )}
    </section>
  )
}
