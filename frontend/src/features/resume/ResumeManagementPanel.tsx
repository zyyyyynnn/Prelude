import { useEffect, useRef } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { RefreshCw, Trash2 } from 'lucide-react'
import { Button } from '@/shared/ui/button'
import { Panel } from '@/shared/ui/panel'
import { useFeedback } from '@/shared/ui/feedback-context'
import { sectionTitles } from '@/features/settings'
import { deleteResume, fetchResumes, uploadResume } from './index'

export function ResumeManagementPanel({ uploadRequest }: { uploadRequest?: number }) {
  const input = useRef<HTMLInputElement>(null)
  const client = useQueryClient()
  const feedback = useFeedback()
  const resumes = useQuery({ queryKey: ['resumes'], queryFn: ({ signal }) => fetchResumes(signal) })
  const upload = useMutation({
    mutationFn: (file: File) => uploadResume(file),
    onSuccess: () => {
      feedback.notify('简历已上传并完成解析', 'success')
      void client.invalidateQueries({ queryKey: ['resumes'] })
    },
    onError: (error) => feedback.notify(error.message, 'error'),
  })
  const remove = useMutation({
    mutationFn: deleteResume,
    onSuccess: () => {
      feedback.notify('简历已删除', 'success')
      void client.invalidateQueries({ queryKey: ['resumes'] })
    },
    onError: (error) => feedback.notify(error.message, 'error'),
  })

  useEffect(() => {
    if (uploadRequest) input.current?.click()
  }, [uploadRequest])

  function selectFile(file?: File) {
    if (!file) return
    if (file.type !== 'application/pdf' && !file.name.toLowerCase().endsWith('.pdf')) {
      feedback.notify('仅支持 PDF 简历', 'error')
      return
    }
    upload.mutate(file)
  }

  return (
    <Panel
      title={sectionTitles.resumes}
      actions={
        <Button onClick={() => input.current?.click()} loading={upload.isPending}>
          上传简历
        </Button>
      }
    >
      <label className="sr-only" htmlFor="settings-resume-upload">
        选择 PDF 简历
      </label>
      <input
        id="settings-resume-upload"
        ref={input}
        className="sr-only"
        type="file"
        accept="application/pdf"
        onChange={(event) => {
          selectFile(event.target.files?.[0])
          event.currentTarget.value = ''
        }}
      />
      <section className="grid gap-sm" aria-labelledby="resume-library-title">
        <h3 id="resume-library-title" className="type-subtitle" data-slot="section-title">
          已上传简历
        </h3>
        {resumes.isPending ? (
          <div className="empty-state" aria-live="polite">
            正在读取简历库…
          </div>
        ) : resumes.isError ? (
          <div className="empty-state">
            <p>{resumes.error.message}</p>
            <Button variant="secondary" onClick={() => void resumes.refetch()}>
              <RefreshCw aria-hidden="true" />
              重新加载
            </Button>
          </div>
        ) : resumes.data?.length ? (
          <div className="flex flex-col gap-sm">
            {resumes.data.map((resume) => (
              <article className="list-row" key={resume.id} data-slot="resume-row">
                <div className="flex-1 min-w-0" data-slot="resume-row-main">
                  <div className="flex flex-col gap-xs min-w-(--layout-list-title-min-inline-size)">
                    <h4 className="truncate-title">{resume.fileName}</h4>
                    <p className="type-meta">
                      {resume.createdAt
                        ? new Intl.DateTimeFormat('zh-CN', {
                            dateStyle: 'medium',
                            timeStyle: 'short',
                          }).format(new Date(resume.createdAt))
                        : '已解析'}{' '}
                      · {resume.sessionCount ?? 0} 场面试
                    </p>
                  </div>
                </div>
                <Button
                  size="icon"
                  variant="ghost"
                  aria-label={`删除 ${resume.fileName}`}
                  disabled={resume.inUse || remove.isPending}
                  onClick={() => {
                    void feedback
                      .confirm({
                        title: '删除简历',
                        message: `确认删除“${resume.fileName}”？删除后无法恢复。`,
                        confirmText: '删除',
                        danger: true,
                      })
                      .then((accepted) => {
                        if (accepted) remove.mutate(resume.id)
                      })
                  }}
                >
                  <Trash2 aria-hidden="true" />
                </Button>
              </article>
            ))}
          </div>
        ) : (
          <div className="empty-state">
            <p>暂无简历，上传 PDF 后开始训练。</p>
          </div>
        )}
      </section>
    </Panel>
  )
}
