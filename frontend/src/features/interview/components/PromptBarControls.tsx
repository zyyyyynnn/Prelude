import type { Position } from '@/features/position'
import type { ResumeItem } from '@/features/resume'
import {
  RiAddLine,
  RiAttachmentLine,
  RiBriefcaseLine,
  RiFileTextLine,
  RiScanLine,
  RiTerminalBoxLine,
  RiUploadLine,
} from '@remixicon/react'
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
  Icon,
  IconTooltip,
  MenuLabel,
  PromptBarActions,
  PromptBarFact,
} from '@/shared/ui'

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
          <Icon as={RiAddLine} aria-hidden="true" />
        </Button>
      }
    >
      <DropdownMenuGroup>
        <DropdownMenuItem disabled={uploading} onClick={onUpload}>
          <MenuLabel
            icon={<Icon as={RiAttachmentLine} />}
            label={uploading ? '正在上传…' : '上传附件'}
          />
        </DropdownMenuItem>
        <DropdownMenuSubmenu
          trigger={
            <MenuLabel
              submenu
              icon={<Icon as={RiFileTextLine} />}
              label="选择简历"
              detail={resumeName ?? '未选择'}
            />
          }
        >
          <DropdownMenuRadioGroup
            value={resumeId === null ? '' : String(resumeId)}
            onValueChange={(value) => onResumeChange(Number(value))}
          >
            {resumes.length ? (
              resumes.map((resume) => (
                <DropdownMenuRadioItem key={resume.id} value={String(resume.id)}>
                  {resume.fileName}
                </DropdownMenuRadioItem>
              ))
            ) : (
              <DropdownMenuItem disabled>暂无可用简历</DropdownMenuItem>
            )}
          </DropdownMenuRadioGroup>
          <DropdownMenuSeparator />
          <DropdownMenuGroup>
            <DropdownMenuItem onClick={onNewResume}>
              <MenuLabel icon={<Icon as={RiUploadLine} />} label="新建简历" />
            </DropdownMenuItem>
          </DropdownMenuGroup>
        </DropdownMenuSubmenu>
        <DropdownMenuSubmenu
          trigger={
            <MenuLabel
              submenu
              icon={<Icon as={RiBriefcaseLine} />}
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
                  {position.name}
                </DropdownMenuRadioItem>
              ))
            ) : (
              <DropdownMenuItem disabled>暂无可用岗位</DropdownMenuItem>
            )}
          </DropdownMenuRadioGroup>
          <DropdownMenuSeparator />
          <DropdownMenuGroup>
            <DropdownMenuItem onClick={onNewPosition}>
              <MenuLabel icon={<Icon as={RiAddLine} />} label="新建岗位" />
            </DropdownMenuItem>
          </DropdownMenuGroup>
        </DropdownMenuSubmenu>
        <DropdownMenuCheckboxItem checked={jdEnabled} onCheckedChange={onJdEnabledChange}>
          <MenuLabel
            icon={<Icon as={RiScanLine} />}
            label="JD 匹配"
            detail={jdEnabled ? '已开启' : '未开启'}
          />
        </DropdownMenuCheckboxItem>
      </DropdownMenuGroup>
    </DropdownMenu>
  )
}

/** The composer's context row once an interview is running: the locked context control
 *  plus the model and JD facts frozen at the moment it started. */
export function InterviewContextFacts({
  modelName,
  jdMatched,
}: {
  modelName: string
  jdMatched: boolean
}) {
  return (
    <PromptBarActions>
      <LockedInterviewContextButton />
      <PromptBarFact label={modelName} icon={<Icon as={RiTerminalBoxLine} aria-hidden="true" />} />
      {jdMatched && (
        <PromptBarFact label="JD 匹配" icon={<Icon as={RiScanLine} aria-hidden="true" />} />
      )}
    </PromptBarActions>
  )
}

export function LockedInterviewContextButton() {
  return (
    <IconTooltip label="面试开始后上下文已锁定">
      <span className="inline-flex" tabIndex={0}>
        <Button type="button" size="icon" variant="ghost" aria-label="面试上下文已锁定" disabled>
          <Icon as={RiAddLine} aria-hidden="true" />
        </Button>
      </span>
    </IconTooltip>
  )
}
