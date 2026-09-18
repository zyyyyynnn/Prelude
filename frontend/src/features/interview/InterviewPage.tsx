import { useEffect, useRef, useState, type FormEvent, type ReactNode } from 'react'
import { useNavigate, useSearchParams } from 'react-router'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import {
  Briefcase,
  ChevronDown,
  ChevronRight,
  Printer,
  FileText,
  Image,
  Keyboard,
  Mic,
  Paperclip,
  Plus,
  RefreshCw,
  ScanSearch,
  Settings,
  Terminal,
  Upload,
  X,
} from 'lucide-react'
import { deleteAttachment, uploadAttachment, type AttachmentItem } from '@/features/assets'
import { fetchResumes, type ResumeItem } from '@/features/resume'
import { printInterviewReport, ReportPanel } from '@/features/report'
import './interview.css'
import {
  fetchLlmConfig,
  fetchProviders,
  saveLlmConfig,
  useSettings,
  REASONING_LABELS,
  type LlmConfigPayload,
  type LlmConfigResponse,
} from '@/features/settings'
import { fetchPositions, type Position } from '@/features/position'
import { RoseThree } from '@/shared/brand/RoseThree'
import { cn } from '@/shared/lib/cn'
import { Button } from '@/shared/ui/button'
import { useFeedback } from '@/shared/ui/feedback-context'
import {
  DropdownMenu,
  DropdownMenuCheckboxItem,
  DropdownMenuGroup,
  DropdownMenuItem,
  DropdownMenuRadioGroup,
  DropdownMenuRadioItem,
  DropdownMenuSeparator,
  DropdownMenuSubmenu,
} from '@/shared/ui/menu'
import { IconTooltip } from '@/shared/ui/overlay'
import { SegmentedControl } from '@/shared/ui/segmented-control'
import { PromptBar } from './components/PromptBar'
import { MessageThread } from './components/MessageThread'
import { useVoiceInterview } from './useVoiceInterview'
import { fetchSession, finishInterview, startInterview, streamInterview } from './index'
import type {
  InterviewMessageRecord,
  InterviewModelConfig,
  InterviewModelProvider,
  InterviewSessionDetailResponse,
  InterviewStageName,
  ReasoningLevel,
} from './types'

const MAX_CONTEXT_MESSAGES = 20

function applyMessageUpdate(
  existing: InterviewMessageRecord[] | null,
  fallback: InterviewMessageRecord[] | undefined,
  message: InterviewMessageRecord,
  append: boolean,
): InterviewMessageRecord[] {
  const list = [...(existing ?? fallback ?? [])]
  const index = list.findIndex((item) => item.id === message.id)
  if (index < 0) {
    list.push(message)
  } else {
    list[index] = {
      ...list[index],
      ...message,
      content: append ? list[index].content + message.content : message.content,
    }
  }
  return list
}

function handleInterviewStreamEvent(
  event: { name: string; data: string },
  assistantId: number,
  sessionId: number,
  client: ReturnType<typeof useQueryClient>,
  callbacks: {
    updateMessage: (msg: InterviewMessageRecord, append?: boolean) => void
    setConnectionStatus: (status: string) => void
    setMessages: (
      updater: (prev: InterviewMessageRecord[] | null) => InterviewMessageRecord[] | null,
    ) => void
    setShowReport: (show: boolean) => void
    onError: (msg: string) => void
  },
) {
  const { name, data } = event
  if (name === 'message') {
    callbacks.updateMessage({ id: assistantId, role: 'assistant', content: data }, true)
    return
  }
  if (name === 'status') {
    callbacks.setConnectionStatus(
      data.startsWith('reconnecting_')
        ? `连接已断开，正在尝试第 ${data.split('_')[1]} 次重连`
        : data === 'checking'
          ? '正在核对会话状态'
          : '',
    )
    return
  }
  if (name === 'sync') {
    try {
      const syncMessages = JSON.parse(data) as InterviewMessageRecord[]
      callbacks.setMessages(() => syncMessages)
    } catch {
      callbacks.onError('会话同步数据无法解析')
    }
    return
  }
  if (name === 'report_ready') {
    client.setQueryData<InterviewSessionDetailResponse>(['interview-session', sessionId], (old) =>
      old ? { ...old, summaryReport: data, status: 'finished' } : old,
    )
    callbacks.setShowReport(true)
    return
  }
  if (name === 'judge') {
    try {
      const result = JSON.parse(data) as { score?: number; hint?: string }
      callbacks.setMessages((list) => {
        const next = [...(list ?? [])]
        const index = next.findLastIndex((item) => item.role === 'user')
        if (index >= 0) next[index] = { ...next[index], score: result.score, hint: result.hint }
        return next
      })
    } catch {
      callbacks.onError('评分数据无法解析')
    }
    return
  }
  if (name === 'error') throw new Error(data)
}

function useInterviewSession(sessionId: number, onError: (message: string) => void) {
  const client = useQueryClient()
  const [messages, setMessages] = useState<InterviewMessageRecord[] | null>(null)
  const [showReport, setShowReport] = useState(false)
  const [connectionStatus, setConnectionStatus] = useState('')
  const abort = useRef<AbortController | null>(null)
  const autoStartedSessionId = useRef<number | null>(null)
  const session = useQuery({
    queryKey: ['interview-session', sessionId],
    queryFn: ({ signal }) => fetchSession(sessionId, signal),
    refetchInterval: (query) => (query.state.data?.status === 'generating' ? 2000 : false),
  })
  const current = session.data
  const visibleMessages = messages ?? current?.messages ?? []

  useEffect(
    () => () => {
      abort.current?.abort()
    },
    [],
  )

  function updateMessage(message: InterviewMessageRecord, append = false) {
    setMessages((existing) => applyMessageUpdate(existing, current?.messages, message, append))
  }

  const send = useMutation({
    mutationFn: async ({
      content,
      autoStart = false,
    }: {
      content: string
      autoStart?: boolean
    }) => {
      const optimisticId = Date.now()
      const assistantId = optimisticId + 1
      const base = [...visibleMessages]
      if (!autoStart)
        base.push({ id: optimisticId, role: 'user', content, createdAt: new Date().toISOString() })
      base.push({
        id: assistantId,
        role: 'assistant',
        content: '',
        createdAt: new Date().toISOString(),
      })
      setMessages(base)
      abort.current?.abort()
      abort.current = new AbortController()
      const context = base.filter((item) => item.id !== assistantId).slice(-MAX_CONTEXT_MESSAGES)
      await streamInterview(
        sessionId,
        { content, messages: context },
        (event) =>
          handleInterviewStreamEvent(event, assistantId, sessionId, client, {
            updateMessage,
            setConnectionStatus,
            setMessages,
            setShowReport,
            onError,
          }),
        abort.current.signal,
        autoStart,
      )
    },
    onSuccess: async () => {
      setConnectionStatus('')
      await session.refetch()
      setMessages(null)
      await client.invalidateQueries({ queryKey: ['interview-sessions'] })
    },
    onError: async (error) => {
      if (error.name === 'AbortError') return
      onError(error.message)
      await session.refetch()
      setConnectionStatus('')
      setMessages(null)
    },
  })

  useEffect(() => {
    if (
      !current ||
      current.messages.length ||
      autoStartedSessionId.current === sessionId ||
      current.status === 'finished'
    )
      return
    const timer = window.setTimeout(() => {
      if (autoStartedSessionId.current === sessionId) return
      autoStartedSessionId.current = sessionId
      send.mutate({ content: '', autoStart: true })
    }, 0)
    return () => window.clearTimeout(timer)
  }, [current, send, sessionId])

  const finish = useMutation({
    mutationFn: () => finishInterview(sessionId),
    onSuccess: async (result) => {
      setShowReport(true)
      client.setQueryData<InterviewSessionDetailResponse>(
        ['interview-session', sessionId],
        (old) =>
          old
            ? {
                ...old,
                status: result.status ?? 'generating',
                summaryReport: result.summaryReport || old.summaryReport,
              }
            : old,
      )
      await client.invalidateQueries({ queryKey: ['interview-sessions'] })
    },
    onError: (error) => onError(error.message),
  })
  return {
    session,
    current,
    messages: visibleMessages,
    showReport,
    setShowReport,
    connectionStatus,
    sending: send.isPending,
    finishing: finish.isPending,
    send: (content: string) => send.mutate({ content }),
    finish: () => finish.mutate(),
    updateMessage,
    refresh: () => void session.refetch(),
  }
}

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
            <h1
              className="workspace-header__title workspace-header__title--truncated"
              aria-label={headerTitle}
            >
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

export function MenuRow({
  label,
  value,
  submenu,
}: {
  label: string
  value?: string
  submenu?: boolean
}) {
  return (
    <>
      <span className="prelude-menu__label">{label}</span>
      {value && <span className="prelude-menu__detail">{value}</span>}
      {submenu && <ChevronRight className="prelude-menu__chevron" aria-hidden="true" />}
    </>
  )
}

export function InterviewModelMenu({
  config,
  providers,
  saving,
  onModelChange,
  onThinkingDepthChange,
  onManage,
}: {
  config: InterviewModelConfig
  providers: InterviewModelProvider[]
  saving: boolean
  onModelChange: (model: string) => void
  onThinkingDepthChange: (level: ReasoningLevel | null) => void
  onManage: () => void
}) {
  const provider = providers.find((item) => item.providerKey === config.provider)
  const models = [
    ...new Set([config.model, ...(provider?.models.map((item) => item.model) ?? [])]),
  ].filter(Boolean)
  const capability =
    provider?.models.find((item) => item.model === config.model) ??
    (config.capability.model === config.model ? config.capability : undefined)
  const reasoningSupported = capability?.reasoning ?? false
  const thinkingValue = config.reasoningLevel
  const ariaThinking = reasoningSupported ? `，思考深度：${REASONING_LABELS[thinkingValue]}` : ''

  return (
    <DropdownMenu
      side="top"
      layout="model"
      trigger={
        <button
          type="button"
          className="prompt-bar__control prompt-bar__model ui-action"
          aria-label={`模型：${config.model}${ariaThinking}`}
          disabled={saving}
        >
          <span className="prompt-bar__control-label">
            {config.model}
            {reasoningSupported ? ` · ${REASONING_LABELS[thinkingValue]}` : ''}
          </span>
          <ChevronDown aria-hidden="true" />
        </button>
      }
    >
      <DropdownMenuGroup>
        <DropdownMenuSubmenu trigger={<MenuRow label="模型" value={config.model} submenu />}>
          <DropdownMenuRadioGroup value={config.model} onValueChange={onModelChange}>
            {models.length ? (
              models.map((model) => (
                <DropdownMenuRadioItem key={model} value={model}>
                  <span className="prelude-menu__item-label">{model}</span>
                </DropdownMenuRadioItem>
              ))
            ) : (
              <DropdownMenuItem disabled>请先在模型管理中配置模型</DropdownMenuItem>
            )}
          </DropdownMenuRadioGroup>
        </DropdownMenuSubmenu>
        {reasoningSupported ? (
          <DropdownMenuSubmenu
            trigger={<MenuRow label="思考深度" value={REASONING_LABELS[thinkingValue]} submenu />}
          >
            <DropdownMenuRadioGroup
              value={thinkingValue}
              onValueChange={(value) => onThinkingDepthChange(value as ReasoningLevel)}
            >
              {(capability?.supportedReasoningLevels ?? []).map((level) => (
                <DropdownMenuRadioItem key={level} value={level}>
                  <span className="prelude-menu__item-label">{REASONING_LABELS[level]}</span>
                </DropdownMenuRadioItem>
              ))}
            </DropdownMenuRadioGroup>
          </DropdownMenuSubmenu>
        ) : null}
      </DropdownMenuGroup>
      <DropdownMenuSeparator />
      <DropdownMenuGroup>
        <DropdownMenuItem layout="leading-icon" onClick={onManage}>
          <Settings className="prelude-menu__icon--leading" aria-hidden="true" />
          <span className="prelude-menu__item-label">管理模型</span>
        </DropdownMenuItem>
      </DropdownMenuGroup>
    </DropdownMenu>
  )
}

export function ContextMenuLabel({
  icon,
  label,
  detail,
}: {
  icon: ReactNode
  label: string
  detail?: string
}) {
  return (
    <>
      <span className="prelude-menu__icon" aria-hidden="true">
        {icon}
      </span>
      <span className="prelude-menu__label">{label}</span>
      {detail && <span className="prelude-menu__detail">{detail}</span>}
    </>
  )
}

export function SubmenuLabel(props: Parameters<typeof ContextMenuLabel>[0]) {
  return (
    <>
      <ContextMenuLabel {...props} />
      <ChevronRight className="prelude-menu__chevron" aria-hidden="true" />
    </>
  )
}

export function InterviewContextMenu({
  resumes,
  positions,
  resumeId,
  positionId,
  jdEnabled,
  uploading,
  onResumeChange,
  onPositionChange,
  onJdEnabledChange,
  onUpload,
  onNewResume,
  onNewPosition,
}: {
  resumes: ResumeItem[]
  positions: Position[]
  resumeId: number | null
  positionId: number | null
  jdEnabled: boolean
  uploading: boolean
  onResumeChange: (id: number) => void
  onPositionChange: (id: number) => void
  onJdEnabledChange: (enabled: boolean) => void
  onUpload: () => void
  onNewResume: () => void
  onNewPosition: () => void
}) {
  const resumeName = resumes.find((item) => item.id === resumeId)?.fileName
  const positionName = positions.find((item) => item.id === positionId)?.name

  return (
    <DropdownMenu
      side="top"
      layout="structured"
      trigger={
        <Button type="button" size="icon-compact" variant="ghost" aria-label="添加面试上下文">
          <Plus aria-hidden="true" />
        </Button>
      }
    >
      <DropdownMenuGroup>
        <DropdownMenuItem disabled={uploading} onClick={onUpload}>
          <ContextMenuLabel icon={<Paperclip />} label={uploading ? '正在上传…' : '上传附件'} />
        </DropdownMenuItem>
        <DropdownMenuSubmenu
          trigger={
            <SubmenuLabel icon={<FileText />} label="选择简历" detail={resumeName ?? '未选择'} />
          }
        >
          <DropdownMenuRadioGroup
            value={resumeId === null ? '' : String(resumeId)}
            onValueChange={(value) => onResumeChange(Number(value))}
          >
            {resumes.length ? (
              resumes.map((resume) => (
                <DropdownMenuRadioItem key={resume.id} value={String(resume.id)}>
                  <span className="prelude-menu__item-label">{resume.fileName}</span>
                </DropdownMenuRadioItem>
              ))
            ) : (
              <DropdownMenuItem disabled>暂无可用简历</DropdownMenuItem>
            )}
          </DropdownMenuRadioGroup>
          <DropdownMenuSeparator />
          <DropdownMenuGroup>
            <DropdownMenuItem onClick={onNewResume}>
              <ContextMenuLabel icon={<Upload />} label="新建简历" />
            </DropdownMenuItem>
          </DropdownMenuGroup>
        </DropdownMenuSubmenu>
        <DropdownMenuSubmenu
          trigger={
            <SubmenuLabel icon={<Briefcase />} label="选择岗位" detail={positionName ?? '未选择'} />
          }
        >
          <DropdownMenuRadioGroup
            value={positionId === null ? '' : String(positionId)}
            onValueChange={(value) => onPositionChange(Number(value))}
          >
            {positions.length ? (
              positions.map((position) => (
                <DropdownMenuRadioItem key={position.id} value={String(position.id)}>
                  <span className="prelude-menu__item-label">{position.name}</span>
                </DropdownMenuRadioItem>
              ))
            ) : (
              <DropdownMenuItem disabled>暂无可用岗位</DropdownMenuItem>
            )}
          </DropdownMenuRadioGroup>
          <DropdownMenuSeparator />
          <DropdownMenuGroup>
            <DropdownMenuItem onClick={onNewPosition}>
              <ContextMenuLabel icon={<Plus />} label="新建岗位" />
            </DropdownMenuItem>
          </DropdownMenuGroup>
        </DropdownMenuSubmenu>
        <DropdownMenuCheckboxItem checked={jdEnabled} onCheckedChange={onJdEnabledChange}>
          <ContextMenuLabel
            icon={<ScanSearch />}
            label="JD 匹配"
            detail={jdEnabled ? '已开启' : '未开启'}
          />
        </DropdownMenuCheckboxItem>
      </DropdownMenuGroup>
    </DropdownMenu>
  )
}

export function LockedInterviewContextButton() {
  return (
    <IconTooltip label="面试开始后上下文已锁定">
      <span className="prompt-bar__locked-trigger" tabIndex={0}>
        <Button
          type="button"
          size="icon-compact"
          variant="ghost"
          aria-label="面试上下文已锁定"
          disabled
        >
          <Plus aria-hidden="true" />
        </Button>
      </span>
    </IconTooltip>
  )
}

export function PromptBarFact({ label, icon }: { label: string; icon: ReactNode }) {
  return (
    <IconTooltip label={label}>
      <span className="prompt-bar__control prompt-bar__fact" tabIndex={0}>
        {icon}
        <span className="prompt-bar__control-label">{label}</span>
      </span>
    </IconTooltip>
  )
}

export function ContextAttachment({
  label,
  kind,
  onRemove,
}: {
  label: string
  kind: 'resume' | 'position' | 'document' | 'image'
  onRemove?: () => void
}) {
  const Icon =
    kind === 'resume'
      ? FileText
      : kind === 'position'
        ? Briefcase
        : kind === 'image'
          ? Image
          : Paperclip
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
          className="prompt-bar__attachment-remove ui-action ui-action-icon"
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
          <Button type="submit" loading={creating} disabled={!canStart} size="action">
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

function voiceStatusLabel(status: string, recording: boolean): string {
  if (recording) return '正在聆听'
  if (status === 'processing') return '正在处理'
  if (status === 'speaking') return '面试官正在回答'
  return '语音模式已连接'
}

export function InterviewAnswerComposer({
  sessionId,
  resumeName,
  positionName,
  modelName,
  attachments,
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
  modelName: string
  attachments: AttachmentItem[]
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
    onTerminalError: () => setVoice(false),
  })
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
          <span className={cn('prompt-bar__status-dot', `is-${voiceState.status}`)} />
          <span>{voiceStatusLabel(voiceState.status, voiceState.recording)}</span>
        </div>
        <div
          className={cn('prompt-bar__wave', voiceState.recording && 'is-active')}
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
        size="hold"
        pressed={voiceState.recording}
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
      <Button type="submit" loading={sending} disabled={disabled || !answer.trim()} size="action">
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
          <PromptBarFact label={modelName} icon={<Terminal aria-hidden="true" />} />
          {jdMatched && <PromptBarFact label="JD 匹配" icon={<ScanSearch aria-hidden="true" />} />}
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

function frozenModelLabel(model?: string, reasoningLevel?: string) {
  const knownLevel =
    reasoningLevel && reasoningLevel in REASONING_LABELS
      ? REASONING_LABELS[reasoningLevel as keyof typeof REASONING_LABELS]
      : reasoningLevel
  return knownLevel ? `${model ?? '模型信息不可用'} · ${knownLevel}` : (model ?? '模型信息不可用')
}

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

export function InterviewSession({ sessionId }: { sessionId: number }) {
  const feedback = useFeedback()
  const [printing, setPrinting] = useState(false)
  const controller = useInterviewSession(sessionId, (message) => feedback.notify(message, 'error'))
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
          printing={printing}
          onFinish={controller.finish}
          onPrintReport={() => void printReport()}
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

export function InterviewPage() {
  const [params] = useSearchParams()
  const sessionId = Number(params.get('session')) || null
  return sessionId ? <InterviewSession key={sessionId} sessionId={sessionId} /> : <InterviewSetup />
}
