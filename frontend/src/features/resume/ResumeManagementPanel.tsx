import { EmptyState, ErrorState, LoadingState } from '@/shared/ui/empty-state'
import { HiddenFileInput } from '@/shared/ui/file-input'
import { useEffect, useRef } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Button } from '@/shared/ui/button'
import { Panel } from '@/shared/ui/panel'
import { useFeedback } from '@/shared/ui/feedback-context'
import { sectionTitles } from '@/features/settings'
import { ResumeRow } from './ResumeRow'
import { deleteResume, fetchResumes, uploadResume } from './api'

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
      <HiddenFileInput
        id="settings-resume-upload"
        label="选择 PDF 简历"
        accept="application/pdf"
        inputRef={input}
        onFiles={(files) => selectFile(files[0])}
      />
      <section className="grid gap-sm" aria-labelledby="resume-library-title">
        <h3 id="resume-library-title" className="type-subtitle" data-slot="section-title">
          已上传简历
        </h3>
        {resumes.isPending ? (
          <LoadingState message="正在读取简历库…" />
        ) : resumes.isError ? (
          <ErrorState message={resumes.error.message} onRetry={() => void resumes.refetch()} />
        ) : resumes.data?.length ? (
          <div className="flex flex-col gap-sm">
            {resumes.data.map((resume) => (
              <ResumeRow
                key={resume.id}
                resume={resume}
                pending={remove.isPending}
                onDelete={() => {
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
              />
            ))}
          </div>
        ) : (
          <EmptyState message="暂无简历，上传 PDF 后开始训练。" />
        )}
      </section>
    </Panel>
  )
}
