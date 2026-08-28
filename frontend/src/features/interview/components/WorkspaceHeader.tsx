import { Button } from '@/shared/ui'
import type { InterviewStageName } from '../types'

const stageLabels: Record<InterviewStageName, string> = {
  warmup: '破冰',
  technical: '技术问答',
  deep_dive: '深挖追问',
  closing: '收尾',
}
const stages: InterviewStageName[] = ['warmup', 'technical', 'deep_dive', 'closing']

export function WorkspaceHeader({
  title,
  stage = 'warmup',
  status,
  hasReport,
  showingReport,
  sending,
  finishing,
  onFinish,
  onToggleReport,
}: {
  title?: string
  stage?: InterviewStageName
  status?: string
  hasReport: boolean
  showingReport: boolean
  sending: boolean
  finishing: boolean
  onFinish: () => void
  onToggleReport: (show: boolean) => void
}) {
  const finished = status === 'finished' || status === 'generating'
  return (
    <header className="workspace-header">
      <div className="workspace-header__main">
        <div className="workspace-header__title-area">
          <h1 className="workspace-header__title">{title || '新面试会话'}</h1>
          <span className="status-badge">
            {status === 'generating'
              ? '报告生成中'
              : status === 'finished'
                ? '已完成'
                : stageLabels[stage]}
          </span>
        </div>
        <div className="workspace-header__right">
          {!showingReport && (
            <div className="workspace-header__stage-wrap">
              <div className="stage-bar" aria-label={`当前阶段：${stageLabels[stage]}`}>
                {stages.map((item) => (
                  <span
                    key={item}
                    className={`${item === stage ? ' is-active' : ''}${stages.indexOf(item) < stages.indexOf(stage) ? ' is-complete' : ''}`}
                  >
                    {stageLabels[item]}
                  </span>
                ))}
              </div>
              {!finished && (
                <Button
                  variant="secondary"
                  loading={finishing}
                  disabled={sending}
                  onClick={onFinish}
                >
                  结束面试
                </Button>
              )}
            </div>
          )}
          {hasReport && (
            <div className="segmented-control" aria-label="工作区视图">
              <button
                className={!showingReport ? 'is-active' : ''}
                onClick={() => onToggleReport(false)}
              >
                面试
              </button>
              <button
                className={showingReport ? 'is-active' : ''}
                onClick={() => onToggleReport(true)}
              >
                报告
              </button>
            </div>
          )}
        </div>
      </div>
    </header>
  )
}
