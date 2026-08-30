import {
  Briefcase,
  ChevronRight,
  FileText,
  Paperclip,
  Plus,
  ScanSearch,
  Upload,
} from 'lucide-react'
import type { ResumeItem } from '@/features/resume'
import type { PositionTemplate } from '@/features/template'
import type { ReactNode } from 'react'
import {
  Button,
  DropdownMenu,
  DropdownMenuCheckboxItem,
  DropdownMenuGroup,
  DropdownMenuItem,
  DropdownMenuRadioGroup,
  DropdownMenuRadioItem,
  DropdownMenuSeparator,
  DropdownMenuSubmenu,
  IconTooltip,
} from '@/shared/ui'

function ContextMenuLabel({
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

function SubmenuLabel(props: Parameters<typeof ContextMenuLabel>[0]) {
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
  positions: PositionTemplate[]
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
      className="prelude-menu--structured"
      trigger={
        <Button
          type="button"
          size="icon"
          variant="ghost"
          className="prompt-bar__add"
          aria-label="添加面试上下文"
        >
          <Plus aria-hidden="true" />
        </Button>
      }
    >
      <DropdownMenuGroup>
        <DropdownMenuItem disabled={uploading} onClick={onUpload}>
          <ContextMenuLabel
            icon={<Paperclip />}
            label={uploading ? '正在上传…' : '上传附件'}
          />
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
            <SubmenuLabel
              icon={<Briefcase />}
              label="选择岗位"
              detail={positionName ?? '未选择'}
            />
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
          size="icon"
          variant="ghost"
          className="prompt-bar__add"
          aria-label="面试上下文已锁定"
          disabled
        >
          <Plus aria-hidden="true" />
        </Button>
      </span>
    </IconTooltip>
  )
}
