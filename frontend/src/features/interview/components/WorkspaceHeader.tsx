import { Printer } from 'lucide-react'
import { Button, PageHeader, SegmentedControl } from '@/shared/ui'
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
    <PageHeader
      title={headerTitle}
      actions={
        <>
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
                <Printer />
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
        </>
      }
    />
  )
}
