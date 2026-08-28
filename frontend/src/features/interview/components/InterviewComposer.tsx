import { useRef, useState, type FormEvent, type ReactNode } from 'react'
import {
  Briefcase,
  FileText,
  Image,
  Keyboard,
  Mic,
  Paperclip,
  ScanSearch,
  Terminal,
  X,
} from 'lucide-react'
import type { AttachmentItem } from '@/features/assets'
import type { ResumeItem } from '@/features/resume'
import type { LlmConfigResponse, LlmProviderResponse } from '@/features/settings'
import type { PositionTemplate } from '@/features/template'
import { Button, IconTooltip } from '@/shared/ui'
import type { InterviewMessageRecord } from '../types'
import { useVoiceInterview } from '../useVoiceInterview'
import { InterviewContextMenu, LockedInterviewContextButton } from './InterviewContextMenu'
import { InterviewModelMenu } from './InterviewModelMenu'
import { PromptBar } from './PromptBar'

const thinkingLabels: Record<string, string> = {
  low: '低',
  medium: '中',
  high: '高',
  xhigh: '极高',
}

function PromptBarFact({ label, icon }: { label: string; icon: ReactNode }) {
  return (
    <IconTooltip label={label}>
      <span className="prompt-bar__control prompt-bar__fact" tabIndex={0}>
        {icon}
        <span className="prompt-bar__control-label">{label}</span>
      </span>
    </IconTooltip>
  )
}

function ContextAttachment({
  label,
  kind,
  onRemove,
}: {
  label: string
  kind: 'resume' | 'position' | 'document' | 'image'
  onRemove?: () => void
}) {
  const Icon =
    kind === 'resume' ? FileText : kind === 'position' ? Briefcase : kind === 'image' ? Image : Paperclip
  const kindLabel =
    kind === 'resume' ? '简历' : kind === 'position' ? '岗位' : kind === 'image' ? '图片' : '附件'
  return (
    <div className="prompt-bar__attachment">
      <Icon aria-hidden="true" />
      <IconTooltip label={label}>
        <span className="prompt-bar__attachment-label" tabIndex={0}>
          {label}
        </span>
      </IconTooltip>
      {onRemove && (
        <button
          type="button"
          className="prompt-bar__attachment-remove ui-action"
          aria-label={`移除${kindLabel}：${label}`}
          onClick={onRemove}
        >
          <X aria-hidden="true" />
        </button>
      )}
    </div>
  )
}

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
  positions: PositionTemplate[]
  llmConfig: LlmConfigResponse
  llmProviders: LlmProviderResponse[]
  uploadingAttachment: boolean
  savingModel: boolean
  creating: boolean
  onUploadAttachment: (file: File) => Promise<AttachmentItem>
  onDeleteAttachment: (id: number) => Promise<void>
  onModelChange: (model: string) => void
  onThinkingDepthChange: (depth: string | null) => void
  onManageModel: (providerKey?: string) => void
  onNewResume: () => void
  onNewPosition: () => void
  onStart: (value: {
    resumeId: number
    positionId: number
    jdText?: string
    llmModel?: string
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
  const canStart = Boolean(selectedResume && selectedPosition) && !creating

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
      llmModel: llmConfig.model,
      attachmentIds: attachments.length ? attachments.map((item) => item.id) : undefined,
    })
  }

  return (
    <>
      <PromptBar
        placement="centered"
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
          <div className="prompt-bar__rail">
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
                className="prompt-bar__control prompt-bar__jd ui-action"
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
          <Button
            type="submit"
            loading={creating}
            disabled={!canStart}
            className="prompt-bar__primary-action"
          >
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

export function InterviewAnswerComposer({
  sessionId,
  resumeName,
  positionName,
  attachments,
  model,
  thinkingDepth,
  jdMatched,
  disabled,
  sending,
  onSend,
  onMessage,
  onRefresh,
  onError,
}: {
  sessionId: number
  resumeName?: string
  positionName: string
  attachments: AttachmentItem[]
  model: string
  thinkingDepth?: string
  jdMatched: boolean
  disabled: boolean
  sending: boolean
  onSend: (value: string) => void
  onMessage: (message: InterviewMessageRecord, append?: boolean) => void
  onRefresh: () => void
  onError: (message: string) => void
}) {
  const [answer, setAnswer] = useState('')
  const [voice, setVoice] = useState(false)
  const voiceState = useVoiceInterview({
    enabled: voice,
    sessionId,
    onMessage,
    onRefresh,
    onError,
  })
  const thinkingLabel = thinkingDepth ? (thinkingLabels[thinkingDepth] ?? thinkingDepth) : '默认'

  function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const value = answer.trim()
    if (!value || sending || disabled) return
    onSend(value)
    setAnswer('')
  }

  const voiceContent = (
    <div className="prompt-bar__voice-mode">
      <div className="prompt-bar__voice-area">
        <div className="prompt-bar__voice-status">
          <span className={`prompt-bar__status-dot is-${voiceState.status}`} />
          <span>
            {voiceState.recording
              ? '正在聆听'
              : voiceState.status === 'processing'
                ? '正在处理'
                : voiceState.status === 'speaking'
                  ? '面试官正在回答'
                  : '语音模式已连接'}
          </span>
        </div>
        <div
          className={voiceState.recording ? 'prompt-bar__wave is-active' : 'prompt-bar__wave'}
          aria-hidden="true"
        >
          {Array.from({ length: 9 }, (_, index) => (
            <span key={index} />
          ))}
        </div>
      </div>
    </div>
  )

  const actions = voice ? (
    <>
      <IconTooltip label="切换到文字输入">
        <Button
          type="button"
          size="icon"
          variant="secondary"
          aria-label="切换到文字输入"
          onClick={() => {
            voiceState.close()
            setVoice(false)
          }}
        >
          <Keyboard aria-hidden="true" />
        </Button>
      </IconTooltip>
      <Button
        type="button"
        className={
          voiceState.recording ? 'prompt-bar__voice-button is-pressed' : 'prompt-bar__voice-button'
        }
        disabled={disabled || sending}
        onPointerDown={() => void voiceState.startRecording()}
        onPointerUp={voiceState.stopRecording}
        onPointerLeave={voiceState.stopRecording}
        onPointerCancel={voiceState.stopRecording}
        onKeyDown={(event) => {
          if (!event.repeat && (event.key === 'Enter' || event.key === ' ')) {
            event.preventDefault()
            void voiceState.startRecording()
          }
        }}
        onKeyUp={(event) => {
          if (event.key === 'Enter' || event.key === ' ') voiceState.stopRecording()
        }}
      >
        {voiceState.recording ? '松开发送' : '按住说话'}
      </Button>
    </>
  ) : (
    <>
      <IconTooltip label="切换到语音输入">
        <Button
          type="button"
          size="icon"
          variant="secondary"
          aria-label="切换到语音输入"
          onClick={() => setVoice(true)}
          disabled={disabled}
        >
          <Mic aria-hidden="true" />
        </Button>
      </IconTooltip>
      <Button
        type="submit"
        loading={sending}
        disabled={disabled || !answer.trim()}
        className="prompt-bar__primary-action"
      >
        发送
      </Button>
    </>
  )

  return (
    <PromptBar
      placement="bottom"
      disabled={disabled}
      value={answer}
      inputLabel="面试回答"
      onValueChange={setAnswer}
      inputDisabled={disabled || sending}
      inputContent={voice ? voiceContent : undefined}
      placeholder={disabled ? '本场面试已结束' : '输入回答…'}
      attachments={
        <>
          {resumeName && <ContextAttachment kind="resume" label={resumeName} />}
          <ContextAttachment kind="position" label={positionName} />
          {attachments.map((attachment) => (
            <ContextAttachment
              key={attachment.id}
              kind={attachment.image ? 'image' : 'document'}
              label={attachment.fileName}
            />
          ))}
        </>
      }
      leftActions={
        <div className="prompt-bar__rail">
          <LockedInterviewContextButton />
          <PromptBarFact
            label={`${model} · ${thinkingLabel}`}
            icon={<Terminal aria-hidden="true" />}
          />
          {jdMatched && (
            <PromptBarFact label="JD 匹配" icon={<ScanSearch aria-hidden="true" />} />
          )}
        </div>
      }
      rightActions={actions}
      onInputKeyDown={(event) => {
        if ((event.ctrlKey || event.metaKey) && event.key === 'Enter') {
          event.preventDefault()
          event.currentTarget.form?.requestSubmit()
        }
      }}
      onSubmit={submit}
    />
  )
}
