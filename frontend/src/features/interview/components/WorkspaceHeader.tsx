import { Download } from 'lucide-react'
import { Button, IconTooltip, SegmentedControl } from '@/shared/ui'
import type { InterviewStageName } from '../types'

export function WorkspaceHeader({
  title,
  stage = 'warmup',
  status,
  hasReport,
  showingReport,
  sending,
  finishing,
  exporting = false,
  onFinish,
  onExportReport,
  onToggleReport,
}: {
  title?: string
  stage?: InterviewStageName
  status?: string
  hasReport: boolean
  showingReport: boolean
  sending: boolean
  finishing: boolean
  exporting?: boolean
  onFinish: () => void
  onExportReport: () => void
  onToggleReport: (show: boolean) => void
}) {
  const headerTitle = title?.trim() || '新面试会话'
  const finished = status === 'finished' || status === 'generating'
  const showGenerateButton = !showingReport && !finished
  const generateDisabled = sending || stage !== 'closing'
  return (
    <header className="workspace-header">
      <div className="workspace-header__main">
        <div className="workspace-header__title-area">
          <IconTooltip label={headerTitle}>
            <h1 className="workspace-header__title workspace-header__title--truncated" aria-label={headerTitle}>
              {headerTitle}
            </h1>
          </IconTooltip>
        </div>
        <div className="workspace-header__right">
          {showGenerateButton && (
            <div className="stage-actions">
              <Button
                variant="secondary"
                loading={finishing}
                disabled={generateDisabled}
                onClick={onFinish}
              >
                生成报告
              </Button>
            </div>
          )}
          {hasReport && showingReport && (
            <div className="workspace-header__actions">
              <Button
                variant="secondary"
                loading={exporting}
                onClick={onExportReport}
              >
                <Download size={15} />
                导出 PDF
              </Button>
            </div>
          )}
          {hasReport && (
            <SegmentedControl
              items={[
                { value: 'interview', label: '面试' },
                { value: 'report', label: '报告' },
              ] as const}
              value={showingReport ? 'report' : 'interview'}
              onValueChange={(value) => onToggleReport(value === 'report')}
              ariaLabel="工作区视图"
            />
          )}
        </div>
      </div>
    </header>
  )
}
