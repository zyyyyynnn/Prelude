import { FormEvent, useCallback, useEffect, useRef, useState } from 'react'
import { useSearchParams } from 'react-router'
import {
  Button,
  ContextAttachment,
  ErrorState,
  HiddenFileInput,
  Icon,
  IconTooltip,
  LoadingState,
  PageHeader,
  PromptBar,
  PromptBarActions,
  PromptBarFact,
  useFeedback,
} from '@/shared/ui'
import type { AttachmentItem } from '@/features/assets'
import { deleteAttachment, uploadAttachment } from '@/features/assets'
import {
  RiAddLine,
  RiArrowUpLine,
  RiAttachmentLine,
  RiImageLine,
  RiMicLine,
  RiTerminalBoxLine,
} from '@remixicon/react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { fetchLlmConfig, REASONING_LABELS } from '@/features/settings'
import {
  createResumeConversation,
  fetchResumeConversations,
  fetchResumeTurns,
  submitResumeInstruction,
} from './api'
import { ResumeStream } from './resume-stream'

/**
 * The resume assistant workspace: user instructions and assistant messages with
 * their tool-call groups, never interview chat bubbles.
 *
 * A conversation is created by the first instruction, never by opening the page: the
 * empty state is a pure resting view, exactly like the interview setup, so a visit that
 * sends nothing leaves nothing behind. The shells mirror the interview workspace — bare
 * `workspace-empty` while there are no turns, `workspace-active` with a header band once
 * there are — so the two sides cannot drift apart in chrome.
 */
export function ResumeWorkspace() {
  const queryClient = useQueryClient()
  const feedback = useFeedback()
  const [searchParams, setSearchParams] = useSearchParams()
  /* The route names the open conversation, so it is derived rather than mirrored into
     state: the sidebar navigates here, and the first instruction writes the id it created
     back into the same param. One source of truth, no synchronising effect. */
  const conversationId = (() => {
    const raw = searchParams.get('conversation')
    return raw == null ? null : Number(raw) || null
  })()
  const [draft, setDraft] = useState('')
  const [attachments, setAttachments] = useState<AttachmentItem[]>([])
  const attachmentInput = useRef<HTMLInputElement>(null)
  const scroller = useRef<HTMLDivElement>(null)

  const conversations = useQuery({
    queryKey: ['resume-conversations'],
    queryFn: ({ signal }) => fetchResumeConversations(signal),
    enabled: conversationId != null,
  })
  const conversationTitle =
    conversations.data?.find((row) => row.id === conversationId)?.title?.trim() || '新简历工作'

  const turns = useQuery({
    queryKey: ['resume-turns', conversationId],
    enabled: conversationId != null,
    queryFn: ({ signal }) => fetchResumeTurns(conversationId!, signal),
    refetchInterval: (query) => {
      const rows = query.state.data
      return rows?.some((turn) => turn.status === 'running' || turn.status === 'queued')
        ? 1500
        : false
    },
  })

  useEffect(() => {
    scroller.current?.scrollTo({ top: scroller.current.scrollHeight })
  }, [turns.data])

  const submit = useMutation({
    mutationFn: async (instruction: string) => {
      /* The first instruction creates the conversation it lands in. The id is written to
         the route before the turn is posted, so a failed turn still leaves the user inside
         the conversation it was meant for instead of orphaning a fresh empty one. */
      let targetId = conversationId
      if (targetId == null) {
        const conversation = await createResumeConversation()
        targetId = conversation.id
        setSearchParams(
          (current) => {
            const next = new URLSearchParams(current)
            next.set('conversation', String(targetId))
            return next
          },
          { replace: true },
        )
        void queryClient.invalidateQueries({ queryKey: ['resume-conversations'] })
      }
      return submitResumeInstruction(targetId, instruction)
    },
    onSuccess: () => {
      setDraft('')
      /* The key carries the conversation id, which the turn just created may have set in
         the same batch; invalidating by prefix covers whichever id the query landed on. */
      void queryClient.invalidateQueries({ queryKey: ['resume-turns'] })
    },
    onError: (error) => {
      feedback.notify(error instanceof Error ? error.message : '指令提交失败', 'error')
    },
  })

  const upload = useMutation({
    mutationFn: (file: File) => uploadAttachment(file),
    onSuccess: (attachment) => {
      setAttachments((current) => (current.length >= 5 ? current : [...current, attachment]))
    },
  })

  const removeAttachment = useMutation({
    mutationFn: (id: number) => deleteAttachment(id),
    onSuccess: (_void, id) => {
      setAttachments((current) => current.filter((item) => item.id !== id))
    },
  })

  function pickFiles(files: FileList | null) {
    if (!files) return
    for (const file of Array.from(files).slice(0, Math.max(0, 5 - attachments.length))) {
      upload.mutate(file)
    }
  }

  const llm = useQuery({ queryKey: ['llm-config'], queryFn: fetchLlmConfig })
  const modelLabel = (() => {
    const config = llm.data
    if (!config) return '模型'
    const level = config.reasoningLevel
    const depth = level in REASONING_LABELS ? REASONING_LABELS[level] : level
    return depth ? `${config.model} · ${depth}` : config.model
  })()

  const running = turns.data?.some((turn) => turn.status === 'running' || turn.status === 'queued')

  const onSubmit = useCallback(
    (event: FormEvent<HTMLFormElement>) => {
      event.preventDefault()
      const instruction = draft.trim()
      if (!instruction || submit.isPending) return
      submit.mutate(instruction)
    },
    [draft, submit],
  )

  if (conversationId != null && turns.isPending) {
    return <LoadingState message="正在加载会话…" className="flex-1" />
  }
  if (turns.isError) {
    return (
      <ErrorState
        message={turns.error?.message ?? '工作区不可用'}
        onRetry={() => void turns.refetch()}
        className="flex-1"
      />
    )
  }

  const promptBar = (
    <>
      <PromptBar
        value={draft}
        onValueChange={setDraft}
        inputLabel="简历制作指令"
        placeholder="描述你希望如何修改这份简历…"
        attachments={
          attachments.length > 0 ? (
            <>
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
                  onRemove={() => removeAttachment.mutate(attachment.id)}
                />
              ))}
            </>
          ) : undefined
        }
        leftActions={
          <PromptBarActions>
            <IconTooltip label="上传文件">
              <Button
                type="button"
                size="icon"
                variant="ghost"
                aria-label="上传文件"
                loading={upload.isPending}
                onClick={() => attachmentInput.current?.click()}
              >
                <Icon as={RiAddLine} aria-hidden="true" />
              </Button>
            </IconTooltip>
            <PromptBarFact
              label={modelLabel}
              icon={<Icon as={RiTerminalBoxLine} aria-hidden="true" />}
            />
          </PromptBarActions>
        }
        rightActions={
          <>
            <IconTooltip label="切换到语音输入">
              <Button type="button" size="icon" variant="secondary" aria-label="切换到语音输入">
                <Icon as={RiMicLine} aria-hidden="true" />
              </Button>
            </IconTooltip>
            {running && (
              <Button type="button" variant="secondary">
                排队
              </Button>
            )}
            <IconTooltip label="发送">
              <Button
                type="submit"
                size="icon"
                loading={submit.isPending}
                disabled={!draft.trim()}
                aria-label="发送"
              >
                <Icon as={RiArrowUpLine} aria-hidden="true" />
              </Button>
            </IconTooltip>
          </>
        }
        onSubmit={onSubmit}
      />
      <HiddenFileInput
        id="resume-attachment-upload"
        label="选择简历附件"
        multiple
        accept=".pdf,.docx,.txt,.md,.markdown,image/png,image/jpeg,image/webp"
        inputRef={attachmentInput}
        onFiles={pickFiles}
      />
    </>
  )

  const hasTurns = (turns.data ?? []).length > 0

  if (!hasTurns) {
    return (
      <div className="flex min-h-0 flex-1 flex-col" data-slot="resume-workspace">
        <div className="workspace-empty">
          <div className="flex w-full max-w-(--layout-workspace-content-max-inline-size) flex-col items-center gap-lg">
            <h1 className="type-hero text-center">准备开始简历制作</h1>
            {promptBar}
          </div>
        </div>
      </div>
    )
  }

  return (
    <div className="flex min-h-0 flex-1 flex-col" data-slot="resume-workspace">
      <div className="workspace-active" data-slot="workspace-active">
        <PageHeader title={conversationTitle} />
        <div
          className="relative flex min-h-0 flex-1 flex-col overflow-hidden"
          data-slot="workspace-active-main"
        >
          <div ref={scroller} className="min-h-0 flex-1 overflow-y-auto p-lg">
            <ResumeStream turns={turns.data ?? []} />
          </div>
          <div className="mx-auto w-full max-w-(--layout-workspace-content-max-inline-size) px-lg pb-lg">
            {promptBar}
          </div>
        </div>
      </div>
    </div>
  )
}
