import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { RefreshCw } from 'lucide-react'
import { useState } from 'react'
import { useNavigate, useSearchParams } from 'react-router'
import { deleteAttachment, uploadAttachment } from '@/features/assets'
import { fetchResumes } from '@/features/resume'
import { printInterviewReport, ReportPanel } from '@/features/report'
import {
  fetchLlmConfig,
  fetchProviders,
  saveLlmConfig,
  useSettings,
  type LlmConfigPayload,
  type LlmConfigResponse,
} from '@/features/settings'
import { fetchPositions } from '@/features/template'
import { Button } from '@/shared/ui'
import { RoseThree } from '@/shared/brand/RoseThree'
import { useFeedback } from '@/shared/ui/feedback'
import { fetchSessions, startInterview } from './api'
import { InterviewAnswerComposer, InterviewSetupComposer } from './components/InterviewComposer'
import { MessageThread } from './components/MessageThread'
import { WorkspaceHeader } from './components/WorkspaceHeader'
import { useInterviewSession } from './useInterviewSession'

export function InterviewPage() {
  const [params] = useSearchParams()
  const sessionId = Number(params.get('session')) || null
  return sessionId ? <InterviewSession key={sessionId} sessionId={sessionId} /> : <InterviewSetup />
}

function InterviewSetup() {
  const navigate = useNavigate()
  const client = useQueryClient()
  const feedback = useFeedback()
  const { openSettings } = useSettings()
  const positions = useQuery({
    queryKey: ['positions'],
    queryFn: fetchPositions,
  })
  const resumes = useQuery({
    queryKey: ['resumes'],
    queryFn: ({ signal }) => fetchResumes(signal),
  })
  const llmConfig = useQuery({
    queryKey: ['llm-config'],
    queryFn: fetchLlmConfig,
  })
  const providers = useQuery({
    queryKey: ['llm-providers'],
    queryFn: fetchProviders,
  })
  const upload = useMutation({
    mutationFn: (file: File) => uploadAttachment(file),
    onError: (error) => feedback.notify(error.message, 'error'),
  })
  const removeAttachment = useMutation({
    mutationFn: deleteAttachment,
    onError: (error) => feedback.notify(error.message, 'error'),
  })
  const saveModel = useMutation({
    mutationFn: saveLlmConfig,
    onMutate: async (payload) => {
      await client.cancelQueries({ queryKey: ['llm-config'] })
      const previous = client.getQueryData<LlmConfigResponse>(['llm-config'])
      client.setQueryData<LlmConfigResponse>(['llm-config'], (current) =>
        current
          ? {
              ...current,
              model: payload.model,
              reasoningLevel: payload.reasoningLevel ?? current.reasoningLevel,
            }
          : current,
      )
      return { previous }
    },
    onSuccess: (config) => {
      client.setQueryData(['llm-config'], config)
      feedback.notify('模型配置已更新', 'success')
    },
    onError: (error, _payload, context) => {
      if (context?.previous) client.setQueryData(['llm-config'], context.previous)
      feedback.notify(error.message, 'error')
    },
  })
  const create = useMutation({
    mutationFn: startInterview,
    onSuccess: async (data) => {
      await client.invalidateQueries({ queryKey: ['interview-sessions'] })
      await navigate(`/interview?session=${data.sessionId}`)
    },
    onError: (error) => feedback.notify(error.message, 'error'),
  })
  function updateModel(
    patch: Pick<LlmConfigPayload, 'model'> | Pick<LlmConfigPayload, 'reasoningLevel'>,
  ) {
    if (!llmConfig.data) return
    saveModel.mutate({
      provider: llmConfig.data.provider,
      customEndpointUrl: llmConfig.data.customEndpointUrl ?? undefined,
      model: 'model' in patch ? patch.model : llmConfig.data.model,
      reasoningLevel:
        'reasoningLevel' in patch ? patch.reasoningLevel : llmConfig.data.reasoningLevel,
      fallbackModels: llmConfig.data.fallbackModels,
    })
  }
  const error = positions.error || resumes.error || llmConfig.error || providers.error
  return (
    <div className="interview-workspace">
      <div className="workspace-empty">
        <div className="workspace-empty__content">
          <h1 className="workspace-empty__title">准备开始一场沉浸式模拟面试</h1>
          {positions.isPending ||
          resumes.isPending ||
          llmConfig.isPending ||
          providers.isPending ? (
            <div className="workspace-loading">正在准备面试资源…</div>
          ) : error ? (
            <div className="empty-state">
              <p>{error.message}</p>
              <Button
                variant="secondary"
                onClick={() => {
                  void positions.refetch()
                  void resumes.refetch()
                  void llmConfig.refetch()
                  void providers.refetch()
                }}
              >
                <RefreshCw size={15} />
                重新加载
              </Button>
            </div>
          ) : (
            <InterviewSetupComposer
              resumes={resumes.data ?? []}
              positions={positions.data ?? []}
              llmConfig={llmConfig.data}
              llmProviders={providers.data ?? []}
              uploadingAttachment={upload.isPending}
              savingModel={saveModel.isPending}
              creating={create.isPending}
              onUploadAttachment={(file) => upload.mutateAsync(file)}
              onDeleteAttachment={(id) => removeAttachment.mutateAsync(id)}
              onModelChange={(model) => updateModel({ model })}
              onThinkingDepthChange={(reasoningLevel) => updateModel({ reasoningLevel: reasoningLevel ?? undefined })}
              onManageModel={(provider) => openSettings({ section: 'llm', provider })}
              onNewResume={() =>
                openSettings({ section: 'resumes', intent: 'upload-resume' })
              }
              onNewPosition={() =>
                openSettings({ section: 'positions', intent: 'create-position' })
              }
              onStart={(payload) => create.mutate(payload)}
            />
          )}
        </div>
      </div>
    </div>
  )
}

function InterviewSession({ sessionId }: { sessionId: number }) {
  const feedback = useFeedback()
  const [exporting, setExporting] = useState(false)
  const controller = useInterviewSession(sessionId, (message) => feedback.notify(message, 'error'))
  const sessions = useQuery({
    queryKey: ['interview-sessions'],
    queryFn: ({ signal }) => fetchSessions(signal),
  })
  const resumes = useQuery({
    queryKey: ['resumes'],
    queryFn: ({ signal }) => fetchResumes(signal),
  })
  if (controller.session.isPending) return <div className="workspace-loading">正在加载会话…</div>
  if (controller.session.isError || !controller.current)
    return (
      <div className="workspace-loading">
        <div className="empty-state">
          <p>{controller.session.error?.message ?? '会话不存在'}</p>
          <Button variant="secondary" onClick={() => void controller.session.refetch()}>
            <RefreshCw size={15} />
            重新加载
          </Button>
        </div>
      </div>
    )
  const current = controller.current
  const summary = sessions.data?.find((item) => item.sessionId === sessionId)
  const resumeName = resumes.data?.find((item) => item.id === current.resumeId)?.fileName
  const hasReport = Boolean(current.summaryReport)
  async function exportReport() {
    setExporting(true)
    try {
      await printInterviewReport()
      feedback.notify('已打开系统打印窗口', 'success')
    } catch (error) {
      feedback.notify(error instanceof Error ? error.message : '报告导出失败', 'error')
    } finally {
      setExporting(false)
    }
  }
  return (
    <div className="interview-workspace">
      <div className="workspace-active">
        <WorkspaceHeader
          title={current.targetPosition}
          stage={current.currentStage}
          status={current.status}
          hasReport={hasReport}
          showingReport={controller.showReport}
           sending={controller.sending}
           finishing={controller.finishing}
           exporting={exporting}
           onFinish={controller.finish}
           onExportReport={() => void exportReport()}
           onToggleReport={controller.setShowReport}
        />
        <div className="workspace-active__main">
          {current.status === 'generating' && !hasReport ? (
            <div className="workspace-generating">
              <div className="generating-card">
                <RoseThree className="generating-rose" />
                <h2 className="generating-title">AI 评估报告生成中…</h2>
                <p className="generating-subtitle">正在整理答题表现并生成训练建议。</p>
                <div className="generating-progress">
                  <div className="progress-bar-ind" />
                </div>
              </div>
            </div>
          ) : controller.showReport && hasReport ? (
            <div className="workspace-report scrollable">
              <div className="report-content">
                <ReportPanel source={current.summaryReport!} />
              </div>
            </div>
          ) : (
            <>
              <MessageThread
                messages={controller.messages}
                connectionStatus={controller.connectionStatus}
              />
              <div className="workspace-composer-fixed">
                <InterviewAnswerComposer
                  sessionId={sessionId}
                  resumeName={resumeName}
                  positionName={current.targetPosition ?? '当前岗位'}
                  attachments={current.attachments ?? []}
                  model={summary?.llmModel ?? '默认模型'}
                  thinkingDepth={current.llmThinkingDepth ?? summary?.llmThinkingDepth}
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
