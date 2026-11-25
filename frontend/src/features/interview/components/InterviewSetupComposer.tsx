import { useRef, useState, type FormEvent } from 'react'
import type { AttachmentItem } from '@/features/assets'
import type { Position } from '@/features/position'
import type { ResumeItem } from '@/features/resume'
import type { ReasoningLevel } from '@/features/settings'
import type { InterviewModelConfig, InterviewModelProvider, InterviewStartPayload } from '../types'
import { InterviewModelMenu } from './MenuPrimitives'
import { InterviewContextMenu } from './PromptBarControls'
import { RiAttachmentLine, RiBriefcaseLine, RiFileTextLine, RiImageLine } from '@remixicon/react'
import {
  Button,
  ContextAttachment,
  HiddenFileInput,
  Icon,
  PromptBar,
  PromptBarActions,
  PromptBarToggle,
} from '@/shared/ui'

export function InterviewSetupComposer({
  resumes,
  positions,
  llmConfig,
  llmProviders,
  uploadingAttachment,
  creating,
  onUploadAttachment,
  onDeleteAttachment,
  onModelChange,
  onThinkingDepthChange,
  onManageModel,
  onNewResume,
  onNewPosition,
  onStart,
}: {
  resumes: ResumeItem[]
  positions: Position[]
  llmConfig: InterviewModelConfig
  llmProviders: InterviewModelProvider[]
  uploadingAttachment: boolean
  creating: boolean
  onUploadAttachment: (file: File) => Promise<AttachmentItem>
  onDeleteAttachment: (id: number) => Promise<void>
  onModelChange: (model: string) => void
  onThinkingDepthChange: (depth: ReasoningLevel | null) => void
  onManageModel: (providerKey?: string) => void
  onNewResume: () => void
  onNewPosition: () => void
  onStart: (value: InterviewStartPayload) => void
}) {
  const attachmentInput = useRef<HTMLInputElement>(null)
  const [resumeId, setResumeId] = useState<number | null>(null)
  const [positionId, setPositionId] = useState<number | null>(null)
  const [attachments, setAttachments] = useState<AttachmentItem[]>([])
  const [jdText, setJdText] = useState('')
  const [jdEnabled, setJdEnabled] = useState(false)
  const selectedResume = resumes.find((item) => item.id === resumeId)
  const selectedPosition = positions.find((item) => item.id === positionId)
  const provider = llmProviders.find((item) => item.providerKey === llmConfig.provider)
  const capability =
    provider?.models.find((item) => item.model === llmConfig.model) ??
    (llmConfig.capability.model === llmConfig.model ? llmConfig.capability : undefined)
  const supportedReasoningLevels = capability?.supportedReasoningLevels
  const modelError = supportedReasoningLevels?.length
    ? supportedReasoningLevels.includes(llmConfig.reasoningLevel)
      ? null
      : '该模型不支持当前思考深度，请选择兼容的思考深度'
    : llmConfig.reasoningLevel === 'AUTO'
      ? null
      : '所选模型能力尚未确认，请选择默认思考深度'
  const canStart = Boolean(selectedResume && selectedPosition) && !modelError && !creating

  async function uploadFiles(files: FileList | null) {
    if (!files) return
    for (const file of Array.from(files).slice(0, Math.max(0, 5 - attachments.length))) {
      try {
        const uploaded = await onUploadAttachment(file)
        setAttachments((current) => [...current, uploaded])
      } catch {
        break
      }
    }
  }

  async function removeAttachment(attachment: AttachmentItem) {
    try {
      await onDeleteAttachment(attachment.id)
      setAttachments((current) => current.filter((item) => item.id !== attachment.id))
    } catch {
      return
    }
  }

  function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (!selectedResume || !selectedPosition || !canStart) return
    const normalizedJd = jdText.trim()
    onStart({
      resumeId: selectedResume.id,
      positionId: selectedPosition.id,
      jdText: jdEnabled && normalizedJd ? normalizedJd : undefined,
      model: llmConfig.model,
      reasoningLevel: llmConfig.reasoningLevel,
      attachmentIds: attachments.length ? attachments.map((item) => item.id) : undefined,
    })
  }

  return (
    <>
      <PromptBar
        value={jdText}
        inputLabel="职位描述（可选）"
        onValueChange={(value) => {
          setJdText(value)
          if (value.trim()) setJdEnabled(true)
        }}
        placeholder="输入或粘贴职位描述以开启 JD 匹配（可选）"
        attachments={
          selectedResume || selectedPosition || attachments.length ? (
            <>
              {selectedResume && (
                <ContextAttachment
                  icon={<Icon as={RiFileTextLine} aria-hidden="true" />}
                  kindLabel="简历"
                  label={selectedResume.fileName}
                  onRemove={() => setResumeId(null)}
                />
              )}
              {selectedPosition && (
                <ContextAttachment
                  icon={<Icon as={RiBriefcaseLine} aria-hidden="true" />}
                  kindLabel="岗位"
                  label={selectedPosition.name}
                  onRemove={() => setPositionId(null)}
                />
              )}
              {attachments.map((attachment) => (
                <ContextAttachment
                  key={attachment.id}
                  icon={
                    attachment.image ? (
                      <Icon as={RiImageLine} aria-hidden="true" />
                    ) : (
                      <Icon as={RiAttachmentLine} aria-hidden="true" />
                    )
                  }
                  kindLabel={attachment.image ? '图片' : '附件'}
                  label={attachment.fileName}
                  onRemove={() => void removeAttachment(attachment)}
                />
              ))}
            </>
          ) : undefined
        }
        leftActions={
          <PromptBarActions>
            <InterviewContextMenu
              resumes={resumes}
              positions={positions}
              resumeId={resumeId}
              positionId={positionId}
              jdEnabled={jdEnabled}
              uploading={uploadingAttachment}
              onResumeChange={setResumeId}
              onPositionChange={setPositionId}
              onJdEnabledChange={setJdEnabled}
              onUpload={() => attachmentInput.current?.click()}
              onNewResume={onNewResume}
              onNewPosition={onNewPosition}
            />
            <InterviewModelMenu
              config={llmConfig}
              providers={llmProviders}
              onModelChange={onModelChange}
              onThinkingDepthChange={onThinkingDepthChange}
              onManage={() => onManageModel()}
            />
            {jdEnabled && <PromptBarToggle label="JD 匹配" onDisable={() => setJdEnabled(false)} />}
          </PromptBarActions>
        }
        rightActions={
          <Button type="submit" loading={creating} disabled={!canStart} shape="action">
            开始面试
          </Button>
        }
        onSubmit={submit}
      />
      {modelError && (
        <p role="alert" className="type-caption-error">
          {modelError}
        </p>
      )}
      <HiddenFileInput
        id="interview-attachment-upload"
        label="选择面试附件"
        multiple
        accept=".pdf,.docx,.txt,.md,.markdown,image/png,image/jpeg,image/webp"
        inputRef={attachmentInput}
        onFiles={(files) => void uploadFiles(files)}
      />
    </>
  )
}
