import { Trash2 } from 'lucide-react'
import { Button } from '@/shared/ui'
import type { ResumeItem } from './types'

const timestamp = new Intl.DateTimeFormat('zh-CN', { dateStyle: 'medium', timeStyle: 'short' })

/** One entry of the resume library: the parsed file, how many sessions used it, and
 *  the delete action. Deletion is blocked while the resume backs a live session. */
export function ResumeRow({
  resume,
  pending = false,
  onDelete,
}: {
  resume: ResumeItem
  pending?: boolean
  onDelete: () => void
}) {
  return (
    <article className="list-row" data-slot="resume-row">
      <div className="flex-1 min-w-0" data-slot="resume-row-main">
        <div className="flex flex-col gap-xs min-w-(--layout-list-title-min-inline-size)">
          <h4 className="truncate-title">{resume.fileName}</h4>
          <p className="type-meta">
            {resume.createdAt ? timestamp.format(new Date(resume.createdAt)) : '已解析'} ·{' '}
            {resume.sessionCount ?? 0} 场面试
          </p>
        </div>
      </div>
      <Button
        size="icon"
        variant="ghost"
        aria-label={`删除 ${resume.fileName}`}
        disabled={resume.inUse || pending}
        onClick={onDelete}
      >
        <Trash2 aria-hidden="true" />
      </Button>
    </article>
  )
}
