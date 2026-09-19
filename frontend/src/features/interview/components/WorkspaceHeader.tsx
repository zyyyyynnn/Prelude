import { Printer } from 'lucide-react'
import { Button } from '@/shared/ui/button'
import { IconTooltip } from '@/shared/ui/overlay'
import { SegmentedControl } from '@/shared/ui/segmented-control'
import type { InterviewStageName } from '../types'

export function WorkspaceHeader({
  title,
  stage = 'warmup',
  status,
  hasReport,
  showingReport,
  sending,
  finishing,
  printing = false,
  onFinish,
  onPrintReport,
  onToggleReport,
}: {
  title?: string
  stage?: InterviewStageName
  status?: string
  hasReport: boolean
  showingReport: boolean
  sending: boolean
  finishing: boolean
  printing?: boolean
  onFinish: () => void
  onPrintReport: () => void
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
            <h1 className="workspace-header__title max-w-full" aria-label={headerTitle}>
              {headerTitle}
            </h1>
          </IconTooltip>
        </div>
        <div className="flex shrink-0 items-center gap-lg" data-slot="workspace-header-right">
          {showGenerateButton && (
            <div className="flex gap-sm">
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
            <div className="flex items-center gap-sm">
              <Button variant="secondary" loading={printing} onClick={onPrintReport}>
                <Printer size={15} />
                打印报告
              </Button>
            </div>
          )}
          {hasReport && (
            <SegmentedControl
              items={
                [
                  { value: 'interview', label: '面试' },
                  { value: 'report', label: '报告' },
                ] as const
              }
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
