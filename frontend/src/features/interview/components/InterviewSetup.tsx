import { ErrorState, LoadingState, useFeedback } from '@/shared/ui'
import { useState } from 'react'
import { useNavigate } from 'react-router'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { deleteAttachment, uploadAttachment } from '@/features/assets'
import { fetchPositions } from '@/features/position'
import { fetchResumes } from '@/features/resume'
import { fetchLlmConfig, fetchProviders, useSettings } from '@/features/settings'
import type { InterviewModelConfig } from '../types'
import { startInterview } from '../api'
import { InterviewSetupComposer } from './InterviewSetupComposer'
import {
  interviewModelSource,
  resolveInterviewModelConfig,
  type InterviewModelDraft,
} from './model-draft'

export function InterviewSetup() {
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
  const globalConfigKey = llmConfig.data ? interviewModelSource(llmConfig.data) : ''
  const [modelDraft, setModelDraft] = useState<InterviewModelDraft | null>(null)
  const composerConfig = resolveInterviewModelConfig(modelDraft, globalConfigKey, llmConfig.data)
  const upload = useMutation({
    mutationFn: (file: File) => uploadAttachment(file),
    onError: (error) => feedback.notify(error.message, 'error'),
  })
  const removeAttachment = useMutation({
    mutationFn: deleteAttachment,
    onError: (error) => feedback.notify(error.message, 'error'),
  })
  const create = useMutation({
    mutationFn: startInterview,
    onSuccess: async (data) => {
      await client.invalidateQueries({ queryKey: ['interview-sessions'] })
      await navigate(`/interview?session=${data.sessionId}`)
    },
    onError: (error) => feedback.notify(error.message, 'error'),
  })
  function updateModel(patch: Partial<Pick<InterviewModelConfig, 'model' | 'reasoningLevel'>>) {
    if (!llmConfig.data) return
    setModelDraft((current) => ({
      source: globalConfigKey,
      config: {
        ...(current?.source === globalConfigKey ? current.config : llmConfig.data),
        ...patch,
      },
    }))
  }
  const error = positions.error || resumes.error || llmConfig.error || providers.error
  return (
    <div className="flex min-h-0 flex-1 flex-col" data-slot="interview-workspace">
      <div className="workspace-empty">
        <div className="flex w-full max-w-(--layout-workspace-content-max-inline-size) flex-col items-center gap-lg">
          <h1 className="type-hero text-center">准备开始一场沉浸式模拟面试</h1>
          {positions.isPending ||
          resumes.isPending ||
          llmConfig.isPending ||
          providers.isPending ? (
            <LoadingState message="正在准备面试资源…" />
          ) : error || !composerConfig ? (
            <ErrorState
              message={error?.message ?? '模型配置不可用'}
              onRetry={() => {
                void positions.refetch()
                void resumes.refetch()
                void llmConfig.refetch()
                void providers.refetch()
              }}
            />
          ) : (
            <InterviewSetupComposer
              resumes={resumes.data ?? []}
              positions={positions.data ?? []}
              llmConfig={composerConfig}
              llmProviders={providers.data ?? []}
              uploadingAttachment={upload.isPending}
              creating={create.isPending}
              onUploadAttachment={(file) => upload.mutateAsync(file)}
              onDeleteAttachment={(id) => removeAttachment.mutateAsync(id)}
              onModelChange={(model) => updateModel({ model })}
              onThinkingDepthChange={(reasoningLevel) =>
                updateModel({ reasoningLevel: reasoningLevel ?? 'AUTO' })
              }
              onManageModel={(provider) => openSettings({ section: 'llm', provider })}
              onNewResume={() => openSettings({ section: 'resumes', intent: 'upload-resume' })}
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
