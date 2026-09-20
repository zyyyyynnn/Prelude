import { ErrorState, LoadingState } from '@/shared/ui/empty-state'
import { useNavigate } from 'react-router'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { deleteAttachment, uploadAttachment } from '@/features/assets'
import { fetchPositions } from '@/features/position'
import { fetchResumes } from '@/features/resume'
import {
  fetchLlmConfig,
  fetchProviders,
  saveLlmConfig,
  useSettings,
  type LlmConfigPayload,
  type LlmConfigResponse,
} from '@/features/settings'
import { useFeedback } from '@/shared/ui/feedback-context'
import { startInterview } from '../api'
import { InterviewSetupComposer } from './InterviewSetupComposer'

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
    if ('model' in patch) {
      const provider = providers.data?.find((item) => item.providerKey === llmConfig.data.provider)
      const capability = provider?.models.find((item) => item.model === patch.model)
      if (
        capability &&
        !capability.supportedReasoningLevels.includes(llmConfig.data.reasoningLevel)
      ) {
        feedback.notify('该模型不支持当前思考深度，请先显式选择兼容的思考深度', 'error')
        return
      }
    }
    saveModel.mutate({
      provider: llmConfig.data.provider,
      customEndpointUrl: llmConfig.data.customEndpointUrl ?? undefined,
      model: 'model' in patch ? patch.model : llmConfig.data.model,
      reasoningLevel:
        'reasoningLevel' in patch ? patch.reasoningLevel : llmConfig.data.reasoningLevel,
      maxOutputTokens: llmConfig.data.maxOutputTokens,
      fallbackModels: llmConfig.data.fallbackModels,
    })
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
          ) : error ? (
            <ErrorState
              message={error.message}
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
              llmConfig={llmConfig.data}
              llmProviders={providers.data ?? []}
              uploadingAttachment={upload.isPending}
              savingModel={saveModel.isPending}
              creating={create.isPending}
              onUploadAttachment={(file) => upload.mutateAsync(file)}
              onDeleteAttachment={(id) => removeAttachment.mutateAsync(id)}
              onModelChange={(model) => updateModel({ model })}
              onThinkingDepthChange={(reasoningLevel) =>
                updateModel({ reasoningLevel: reasoningLevel ?? undefined })
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
