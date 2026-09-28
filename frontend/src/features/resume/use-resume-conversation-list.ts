import { useFeedback } from '@/shared/ui'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { useEffect, useRef, useState } from 'react'
import { useLocation, useNavigate } from 'react-router'
import {
  deleteResumeConversation,
  fetchResumeConversations,
  fetchResumeTurns,
  pinResumeConversation,
} from './api'
import type { ResumeConversation } from './types'

/** One row of the sidebar's resume conversation list, ready for `SessionGroup`. */
export interface ResumeConversationRow {
  key: number
  name: string
  finished: boolean
  pinned: boolean
  state: 'idle' | 'active' | 'loading' | 'error'
  onOpen: () => void
  onTogglePin: () => void
  onRemove: () => void
}

export interface ResumeConversationGroup {
  label: string
  finished: boolean
  rows: ResumeConversationRow[]
}

/**
 * The sidebar's resume conversation list: loading, opening, pinning and deleting, with
 * the in-flight request cancelled when the user picks another conversation. It mirrors
 * the interview session list one row behaviour at a time — same groups, same row states,
 * same confirm-and-notify copy — so the two workspaces cannot drift apart.
 */
export function useResumeConversationList(activeConversationId: number | null, enabled = true) {
  const feedback = useFeedback()
  const client = useQueryClient()
  const navigate = useNavigate()
  const location = useLocation()
  const conversationRequest = useRef<AbortController | null>(null)
  const [loadingConversationId, setLoadingConversationId] = useState<number | null>(null)
  const [failedConversationId, setFailedConversationId] = useState<number | null>(null)

  const conversations = useQuery({
    queryKey: ['resume-conversations'],
    queryFn: ({ signal }) => fetchResumeConversations(signal),
    enabled,
  })

  useEffect(() => () => conversationRequest.current?.abort(), [])

  async function togglePin(conversation: ResumeConversation) {
    const pinned = !conversation.pinned
    try {
      await pinResumeConversation(conversation.id, pinned)
      await client.invalidateQueries({ queryKey: ['resume-conversations'] })
      feedback.notify(pinned ? '会话已置顶' : '已取消置顶', 'success')
    } catch (error) {
      feedback.notify(error instanceof Error ? error.message : '置顶状态更新失败', 'error')
    }
  }

  async function openConversation(conversation: ResumeConversation, controller: AbortController) {
    setLoadingConversationId(conversation.id)
    setFailedConversationId(null)
    try {
      await client.fetchQuery({
        queryKey: ['resume-turns', conversation.id],
        queryFn: ({ signal }) =>
          fetchResumeTurns(conversation.id, AbortSignal.any([signal, controller.signal])),
      })
      if (controller.signal.aborted) return
      setLoadingConversationId(null)
      await navigate(`/resume?conversation=${conversation.id}`)
    } catch (error) {
      if (controller.signal.aborted) return
      setLoadingConversationId(null)
      setFailedConversationId(conversation.id)
      feedback.notify(error instanceof Error ? error.message : '会话加载失败', 'error')
    }
  }

  async function removeConversation(conversation: ResumeConversation) {
    const conversationName = conversation.title?.trim() || '未命名简历工作'
    const accepted = await feedback.confirm({
      title: '删除会话',
      message: `“${conversationName}”的指令记录与修改结果都会被永久删除，无法恢复。`,
      confirmText: '删除',
      danger: true,
    })
    if (!accepted) return
    try {
      await deleteResumeConversation(conversation.id)
      await client.invalidateQueries({ queryKey: ['resume-conversations'] })
      if (activeConversationId === conversation.id) void navigate('/resume')
      feedback.notify('会话已删除', 'success')
    } catch (error) {
      feedback.notify(error instanceof Error ? error.message : '会话删除失败', 'error')
    }
  }

  const handleSelectConversation = (conversation: ResumeConversation) => {
    conversationRequest.current?.abort()
    const controller = new AbortController()
    conversationRequest.current = controller
    void openConversation(conversation, controller)
  }

  const rows = conversations.data ?? []
  const toRow = (conversation: ResumeConversation, finished: boolean): ResumeConversationRow => ({
    key: conversation.id,
    name: conversation.title?.trim() || '未命名简历工作',
    finished,
    pinned: conversation.pinned,
    state:
      location.pathname.startsWith('/resume') && activeConversationId === conversation.id
        ? 'active'
        : loadingConversationId === conversation.id
          ? 'loading'
          : failedConversationId === conversation.id
            ? 'error'
            : 'idle',
    onOpen: () => handleSelectConversation(conversation),
    onTogglePin: () => void togglePin(conversation),
    onRemove: () => void removeConversation(conversation),
  })

  const groups: ResumeConversationGroup[] = [
    {
      label: '进行中',
      finished: false,
      rows: rows.filter((row) => row.status !== 'finished').map((row) => toRow(row, false)),
    },
    {
      label: '已完成',
      finished: true,
      rows: rows.filter((row) => row.status === 'finished').map((row) => toRow(row, true)),
    },
  ]

  return { groups, isPending: conversations.isPending }
}
