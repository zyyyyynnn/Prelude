import {
  Card,
  EmptyState,
  ErrorState,
  InsetCard,
  LoadingState,
  PageHeader,
  Panel,
  ScrollRegion,
} from '@/shared/ui'
import { useQuery } from '@tanstack/react-query'
import { fetchRadar, fetchTrend, fetchWeaknesses } from './api'
import { DIMENSIONS, radarValue } from './dimensions'
import { Radar } from './radar-chart'
import { Trend } from './trend-chart'

export function AnalyticsPage() {
  const radar = useQuery({ queryKey: ['analytics-radar'], queryFn: fetchRadar })
  const trend = useQuery({ queryKey: ['analytics-trend'], queryFn: fetchTrend })
  const weaknesses = useQuery({ queryKey: ['analytics-weaknesses'], queryFn: fetchWeaknesses })
  const pending = radar.isPending || trend.isPending || weaknesses.isPending
  const error = radar.error || trend.error || weaknesses.error
  const cards = radar.data
    ? DIMENSIONS.map((dimension) => ({
        key: dimension.key,
        label: dimension.label,
        value: radarValue(radar.data, dimension.key),
      }))
    : []
  function reload() {
    void Promise.all([radar.refetch(), trend.refetch(), weaknesses.refetch()])
  }
  return (
    <section className="workspace-page">
      <PageHeader title="数据看板" />
      <ScrollRegion shell="workspace-page__content">
        {pending ? (
          <LoadingState message="正在整理训练数据…" />
        ) : error ? (
          <ErrorState message={error.message} onRetry={reload} />
        ) : !radar.data?.sessionCount ? (
          <EmptyState message="完成至少一场面试后，这里会显示能力变化与训练重点。" />
        ) : (
          <>
            <div className="grid grid-cols-3 gap-md">
              {cards.map((card) => (
                <Card key={card.key} data-slot="score-card">
                  <p className="type-label" data-slot="score-label">
                    {card.label}
                  </p>
                  <strong className="type-metric" data-slot="score-value">
                    {card.value.toFixed(1)}
                  </strong>
                  <p className="type-meta" data-slot="score-meta">
                    最近 {radar.data.sessionCount} 场均分
                  </p>
                </Card>
              ))}
            </div>
            <div className="grid grid-cols-2 gap-lg" data-slot="chart-grid">
              <Panel
                layout="card"
                eyebrow="结构"
                title="能力雷达"
                actions={<span className="type-meta shrink-0">{radar.data.sessionCount} 场</span>}
              >
                <p className="type-lead">展示最近面试在三项核心维度上的平均水平。</p>
                <Radar data={radar.data} />
              </Panel>
              <Panel layout="card" eyebrow="走势" title="分数趋势">
                <p className="type-lead">按时间查看技术、表达与逻辑评分变化。</p>
                <Trend data={trend.data ?? []} />
              </Panel>
            </div>
            <Panel
              layout="card"
              eyebrow="聚合"
              title="薄弱点列表"
              actions={
                <span className="type-meta shrink-0">{weaknesses.data?.length ?? 0} 类问题</span>
              }
            >
              <p className="type-lead">按出现频率汇总薄弱点。</p>
              {weaknesses.data?.length ? (
                weaknesses.data.map((item) => (
                  <InsetCard data-slot="weakness-item" key={item.category}>
                    <div className="label-end-grid items-baseline gap-md">
                      <h3 className="type-subtitle" data-slot="weakness-title">
                        {item.category}
                      </h3>
                      <p className="type-meta" data-slot="weakness-summary">
                        出现 {item.count} 次
                      </p>
                    </div>
                    <ul
                      className="type-meta grid gap-xs list-plain"
                      data-slot="weakness-descriptions"
                    >
                      {item.descriptions.map((description) => (
                        <li key={description}>{description}</li>
                      ))}
                    </ul>
                  </InsetCard>
                ))
              ) : (
                <EmptyState message="暂无已归纳的薄弱点。" />
              )}
            </Panel>
          </>
        )}
      </ScrollRegion>
    </section>
  )
}
