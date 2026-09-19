import { useRef, useState, type FormEvent } from 'react'
import { ScanSearch } from 'lucide-react'
import type { AttachmentItem } from '@/features/assets'
import type { Position } from '@/features/position'
import type { ResumeItem } from '@/features/resume'
import { Button } from '@/shared/ui/button'
import type { InterviewModelConfig, InterviewModelProvider, ReasoningLevel } from '../types'
import { InterviewModelMenu } from './MenuPrimitives'
import { InterviewContextMenu } from './PromptBarControls'
import { ContextAttachment, PromptBar } from '@/shared/ui/prompt-bar'

export function InterviewSetupComposer({
  resumes,
  positions,
  llmConfig,
  llmProviders,
  uploadingAttachment,
  savingModel,
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
  savingModel: boolean
  creating: boolean
  onUploadAttachment: (file: File) => Promise<AttachmentItem>
  onDeleteAttachment: (id: number) => Promise<void>
  onModelChange: (model: string) => void
  onThinkingDepthChange: (depth: ReasoningLevel | null) => void
  onManageModel: (providerKey?: string) => void
  onNewResume: () => void
  onNewPosition: () => void
  onStart: (value: {
    resumeId: number
    positionId: number
    jdText?: string
    requestedModel?: string
    attachmentIds?: number[]
  }) => void
}) {
  const attachmentInput = useRef<HTMLInputElement>(null)
  const [resumeId, setResumeId] = useState<number | null>(null)
  const [positionId, setPositionId] = useState<number | null>(null)
  const [attachments, setAttachments] = useState<AttachmentItem[]>([])
  const [jdText, setJdText] = useState('')
  const [jdEnabled, setJdEnabled] = useState(false)
  const selectedResume = resumes.find((item) => item.id === resumeId)
  const selectedPosition = positions.find((item) => item.id === positionId)
  const canStart = Boolean(selectedResume && selectedPosition) && !savingModel && !creating

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
      requestedModel: llmConfig.model,
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
                  kind="resume"
                  label={selectedResume.fileName}
                  onRemove={() => setResumeId(null)}
                />
              )}
              {selectedPosition && (
                <ContextAttachment
                  kind="position"
                  label={selectedPosition.name}
                  onRemove={() => setPositionId(null)}
                />
              )}
              {attachments.map((attachment) => (
                <ContextAttachment
                  key={attachment.id}
                  kind={attachment.image ? 'image' : 'document'}
                  label={attachment.fileName}
                  onRemove={() => void removeAttachment(attachment)}
                />
              ))}
            </>
          ) : undefined
        }
        leftActions={
          <div className="flex min-w-0 items-center gap-xs">
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
              saving={savingModel}
              onModelChange={onModelChange}
              onThinkingDepthChange={onThinkingDepthChange}
              onManage={() => onManageModel()}
            />
            {jdEnabled && (
              <button
                type="button"
                className="prompt-bar-control prompt-bar-control-jd ui-action"
                aria-pressed="true"
                onClick={() => setJdEnabled(false)}
              >
                <ScanSearch aria-hidden="true" />
                <span>JD 匹配</span>
              </button>
            )}
          </div>
        }
        rightActions={
          <Button type="submit" loading={creating} disabled={!canStart} shape="action">
            开始面试
          </Button>
        }
        onSubmit={submit}
      />
      <label className="sr-only" htmlFor="interview-attachment-upload">
        选择面试附件
      </label>
      <input
        id="interview-attachment-upload"
        ref={attachmentInput}
        className="sr-only"
        type="file"
        multiple
        accept=".pdf,.docx,.txt,.md,.markdown,image/png,image/jpeg,image/webp"
        onChange={(event) => {
          void uploadFiles(event.target.files)
          event.currentTarget.value = ''
        }}
      />
    </>
  )
}
