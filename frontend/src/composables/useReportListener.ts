import { ref, type ComputedRef, type Ref } from 'vue'

type ReplayState = {
  status?: string
  summaryReport?: string
}

type SessionItem = {
  sessionId: number
  status?: string
  summaryReport?: string
}

type UseReportListenerOptions = {
  activeSessionId: Ref<number | null>
  isGenerating: ComputedRef<boolean>
  replay: Ref<ReplayState | null>
  sessions: Ref<SessionItem[]>
  reportMarkdown: Ref<string>
  showingReport: Ref<boolean>
  authToken: () => string
  loadSession: (sessionId: number, silent?: boolean) => Promise<void>
  showNotice: (message: string, type?: 'success' | 'error' | 'warning' | 'info') => void
}

function parseSseEvent(rawEvent: string) {
  const lines = rawEvent.split('\n')
  const eventName = lines.find((line) => line.startsWith('event:'))?.slice(6).trim() || 'message'
  const data = lines
    .filter((line) => line.startsWith('data:'))
    .map((line) => line.slice(5).trimStart())
    .join('\n')

  return { eventName, data }
}

export function useReportListener(options: UseReportListenerOptions) {
  const listenController = ref<AbortController | null>(null)

  function stopListeningReport() {
    if (listenController.value) {
      listenController.value.abort()
      listenController.value = null
    }
  }

  function markSessionStatus(sessionId: number, status: string, summaryReport?: string) {
    if (options.replay.value) {
      options.replay.value.status = status
      if (summaryReport !== undefined) {
        options.replay.value.summaryReport = summaryReport
      }
    }
    const index = options.sessions.value.findIndex((session) => session.sessionId === sessionId)
    if (index !== -1) {
      options.sessions.value[index].status = status
      if (summaryReport !== undefined) {
        options.sessions.value[index].summaryReport = summaryReport
      }
    }
  }

  async function handleReportEvent(sessionId: number, eventName: string, data: string) {
    if (eventName === 'report_ready') {
      options.reportMarkdown.value = data
      markSessionStatus(sessionId, 'finished', data)
      options.showingReport.value = true
      options.showNotice('评估报告已生成并就绪', 'success')
      stopListeningReport()
      await options.loadSession(sessionId, true)
    } else if (eventName === 'fallback') {
      options.showNotice(data || '已为您自动切换至备用通道', 'warning')
    } else if (eventName === 'error') {
      options.showNotice(data || '报告生成失败', 'error')
      markSessionStatus(sessionId, 'ongoing')
      stopListeningReport()
    }
  }

  async function startListeningReport(sessionId: number) {
    stopListeningReport()
    const controller = new AbortController()
    listenController.value = controller
    const apiBaseUrl = import.meta.env.VITE_API_BASE_URL || '/api'

    try {
      const response = await fetch(`${apiBaseUrl}/interview/${sessionId}/listen`, {
        headers: {
          Authorization: `Bearer ${options.authToken()}`,
        },
        signal: controller.signal,
      })

      if (!response.ok || !response.body) {
        throw new Error('监听接口连接失败')
      }

      const reader = response.body.getReader()
      const decoder = new TextDecoder()
      let buffer = ''

      while (true) {
        const { done, value } = await reader.read()
        if (done) break

        buffer += decoder.decode(value, { stream: true }).replace(/\r/g, '')
        let boundary = buffer.indexOf('\n\n')
        while (boundary !== -1) {
          const rawEvent = buffer.slice(0, boundary).trim()
          buffer = buffer.slice(boundary + 2)
          if (rawEvent) {
            const parsed = parseSseEvent(rawEvent)
            await handleReportEvent(sessionId, parsed.eventName, parsed.data)
          }
          boundary = buffer.indexOf('\n\n')
        }
      }
    } catch (error: any) {
      if (error.name === 'AbortError') return
      console.error('Listen stream error:', error)
      if (options.activeSessionId.value === sessionId && options.isGenerating.value) {
        setTimeout(() => {
          if (options.activeSessionId.value === sessionId && options.isGenerating.value) {
            void startListeningReport(sessionId)
          }
        }, 3000)
      }
    }
  }

  return {
    listenController,
    startListeningReport,
    stopListeningReport,
  }
}
