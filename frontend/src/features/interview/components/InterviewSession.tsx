import { ErrorState, LoadingState, useFeedback, GeneratingSurface } from '@/shared/ui'
import { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { fetchResumes } from '@/features/resume'
import { printInterviewReport, ReportPanel } from '@/features/report'
import { REASONING_LABELS } from '@/features/settings'
import { InterviewAnswerComposer } from './InterviewAnswerComposer'
import { MessageThread } from './MessageThread'
import { useInterviewSession } from './useInterviewSession'
import { WorkspaceHeader } from './WorkspaceHeader'

function frozenModelLabel(model?: string, reasoningLevel?: string) {
  const knownLevel =
    reasoningLevel && reasoningLevel in REASONING_LABELS
      ? REASONING_LABELS[reasoningLevel as keyof typeof REASONING_LABELS]
      : reasoningLevel
  return knownLevel ? `${model ?? '模型信息不可用'} · ${knownLevel}` : (model ?? '模型信息不可用')
}

export function InterviewSession({ sessionId }: { sessionId: number }) {
  const feedback = useFeedback()
  const [printing, setPrinting] = useState(false)
  const controller = useInterviewSession(sessionId, (message) => feedback.notify(message, 'error'))
  const resumes = useQuery({
    queryKey: ['resumes'],
    queryFn: ({ signal }) => fetchResumes(signal),
  })
  if (controller.session.isPending)
    return <LoadingState message="正在加载会话…" className="flex-1" />
  if (controller.session.isError || !controller.current)
    return (
      <ErrorState
        message={controller.session.error?.message ?? '会话不存在'}
        onRetry={() => void controller.session.refetch()}
        className="flex-1"
      />
    )
  const current = controller.current
  const resumeName = resumes.data?.find((item) => item.id === current.resumeId)?.fileName
  const hasReport = Boolean(current.summaryReport)
  async function printReport() {
    setPrinting(true)
    try {
      await printInterviewReport()
      feedback.notify('已打开系统打印窗口', 'success')
    } catch (error) {
      feedback.notify(error instanceof Error ? error.message : '报告打印失败', 'error')
    } finally {
      setPrinting(false)
    }
  }
  return (
    <div className="flex min-h-0 flex-1 flex-col" data-slot="interview-workspace">
      <div
        className="flex h-full min-h-0 flex-1 flex-col overflow-hidden bg-bg"
        data-slot="workspace-active"
      >
        <WorkspaceHeader
          title={current.targetPosition}
          stage={current.currentStage}
          status={current.status}
          hasReport={hasReport}
          showingReport={controller.showReport}
          sending={controller.sending}
          finishing={controller.finishing}
          printing={printing}
          onFinish={controller.finish}
          onPrintReport={() => void printReport()}
          onToggleReport={controller.setShowReport}
        />
        <div
          className="relative flex min-h-0 flex-1 flex-col overflow-hidden"
          data-slot="workspace-active-main"
        >
          {current.status === 'generating' && !hasReport ? (
            <GeneratingSurface title="AI 评估报告生成中…" hint="正在整理答题表现并生成训练建议。" />
          ) : controller.showReport && hasReport ? (
            <div
              className="scrollable gutter-stable flex min-h-0 flex-1 items-start justify-center overflow-y-auto px-2xl py-(--layout-workspace-report-block-padding)"
              data-slot="workspace-report"
            >
              <div
                className="max-w-(--layout-workspace-content-max-inline-size) flex-1"
                data-slot="report-content"
              >
                <ReportPanel source={current.summaryReport!} />
              </div>
            </div>
          ) : (
            <>
              <MessageThread messages={controller.messages} />
              <div className="composer-overlay" data-slot="workspace-composer">
                <InterviewAnswerComposer
                  sessionId={sessionId}
                  resumeName={resumeName}
                  positionName={current.targetPosition ?? '当前岗位'}
                  modelName={frozenModelLabel(current.model, current.reasoningLevel)}
                  attachments={current.attachments ?? []}
                  jdMatched={Boolean(current.jdText?.trim())}
                  disabled={current.status === 'finished'}
                  sending={controller.sending}
                  onSend={controller.send}
                  onMessage={controller.updateMessage}
                  onRefresh={controller.refresh}
                  onError={(message) => feedback.notify(message, 'error')}
                />
              </div>
            </>
          )}
        </div>
      </div>
    </div>
  )
}
