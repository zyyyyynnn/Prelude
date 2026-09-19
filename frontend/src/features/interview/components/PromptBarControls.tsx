import type { ReactNode } from 'react'
import { Briefcase, FileText, Image, Paperclip, Plus, ScanSearch, Upload, X } from 'lucide-react'
import type { Position } from '@/features/position'
import type { ResumeItem } from '@/features/resume'
import { Button } from '@/shared/ui/button'
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
import { ContextMenuLabel, SubmenuLabel } from './MenuPrimitives'

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
        <Button type="button" size="icon" variant="ghost" aria-label="添加面试上下文">
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
      <span className="inline-flex" tabIndex={0}>
        <Button type="button" size="icon" variant="ghost" aria-label="面试上下文已锁定" disabled>
          <Plus aria-hidden="true" />
        </Button>
      </span>
    </IconTooltip>
  )
}

export function PromptBarFact({ label, icon }: { label: string; icon: ReactNode }) {
  return (
    <IconTooltip label={label}>
      <span className="prompt-bar-control prompt-bar-control-text opacity-72" tabIndex={0}>
        {icon}
        <span className="min-w-0 flex-1 truncate text-start">{label}</span>
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
    <div className="prompt-bar-attachment">
      <Icon aria-hidden="true" />
      <IconTooltip label={label}>
        <span className="min-w-0 truncate" tabIndex={0}>
          {label}
        </span>
      </IconTooltip>
      {onRemove && (
        <button
          type="button"
          className="prompt-bar-attachment-remove ui-action ui-action-icon"
          aria-label={`移除${kindLabel}：${label}`}
          onClick={onRemove}
        >
          <X aria-hidden="true" />
        </button>
      )}
    </div>
  )
}
