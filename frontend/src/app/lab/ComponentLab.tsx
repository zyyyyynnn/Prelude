import { useState } from 'react'
import {
  BarChart3,
  Eye,
  EyeOff,
  Info,
  LogOut,
  PanelLeft,
  Plus,
  RefreshCw,
  Settings,
  Terminal,
  X,
} from 'lucide-react'
import { AnswerComposerSurface, InterviewSetupComposer } from '@/features/interview'
import { PositionRow, type Position } from '@/features/position'
import { REASONING_LABELS, sections, themeOptions } from '@/features/settings'
import type { ReasoningLevel } from '@/features/settings'
import { BrandMetaballs } from '@/shared/brand/BrandMetaballs'
import { RoseThree } from '@/shared/brand/RoseThree'
import { StructuredReport } from '@/features/report'
import { ResumeRow } from '@/features/resume'
import {
  ignoreDelete,
  rejectUpload,
  sampleAttachments,
  sampleContextNames,
  sampleModelConfig,
  sampleModelName,
  sampleModelProviders,
  sampleReportCopy,
  samplePositions,
  sampleReport,
  sampleResumes,
} from './samples'
import { Button } from '@/shared/ui/button'
import { Field, FieldAction, FieldActions, Input, Textarea } from '@/shared/ui/field'
import { useFeedback } from '@/shared/ui/feedback-context'
import { GeneratingCard } from '@/shared/ui/generating-card'
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
import { MessageBubble } from '@/shared/ui/message'
import { Dialog, IconTooltip } from '@/shared/ui/overlay'
import { NavItem } from '@/shared/ui/navigation'
import { OptionCard, ThemePreview } from '@/shared/ui/option-card'
import { Panel } from '@/shared/ui/panel'
import {
  ContextAttachment,
  PromptBarFact,
  PromptBarJdToggle,
  type VoiceStatus,
} from '@/shared/ui/prompt-bar'
import { SessionGroup } from '@/shared/ui/session-row'
import { SidebarAction, SidebarBrand, SidebarFrame } from '@/shared/ui/sidebar'
import { SegmentedControl } from '@/shared/ui/segmented-control'
import { Select } from '@/shared/ui/select'
import type { ReactNode } from 'react'

/** Role labels for a list the gallery does not own the words of. */
const ordinals = ['一', '二', '三', '四', '五', '六'] as const

function DemoGroup({ label, children }: { label: string; children: ReactNode }) {
  return (
    <div className="grid gap-sm">
      <h3 className="type-label">{label}</h3>
      <div className="flex flex-wrap items-start gap-sm">{children}</div>
    </div>
  )
}

/** The rail as the shell composes it, held at its content height so both states are
 *  readable in one glance. The cross-fade between the two rail panes is shell mechanics
 *  and is covered by the real workspace screenshots, not by a frozen gallery row. */
function LabRail({ collapsed = false }: { collapsed?: boolean }) {
  const noop = () => undefined
  return (
    <div
      className="rounded-lg bg-bg p-lg"
      data-slot={collapsed ? 'lab-rail-collapsed' : 'lab-rail-expanded'}
    >
      <SidebarFrame
        collapsed={collapsed}
        onToggle={noop}
        brand={<SidebarBrand />}
        primary={
          <SidebarAction
            collapsed={collapsed}
            label="主要操作"
            icon={<Plus />}
            tone="primary"
            onClick={noop}
          />
        }
        footer={
          <SidebarAction
            collapsed={collapsed}
            label="次要操作"
            icon={<Settings />}
            onClick={noop}
          />
        }
      >
        {collapsed ? (
          <nav className="flex flex-col gap-sm" aria-label="实验台导航">
            <SidebarAction collapsed label="分区一" icon={<PanelLeft />} to="/interview" />
            <SidebarAction collapsed label="分区二" icon={<BarChart3 />} to="/analytics" />
          </nav>
        ) : (
          <>
            <SessionGroup
              label="分组一"
              emptyLabel="空态文案"
              rows={[
                {
                  key: 'active',
                  name: '条目一',
                  state: 'active',
                  pinned: true,
                  onOpen: noop,
                  onTogglePin: noop,
                  onRemove: noop,
                },
                {
                  key: 'loading',
                  name: '条目二',
                  state: 'loading',
                  onOpen: noop,
                  onTogglePin: noop,
                  onRemove: noop,
                },
              ]}
            />
            <SessionGroup
              label="分组二"
              emptyLabel="空态文案"
              rows={[
                {
                  key: 'error',
                  name: '条目三',
                  state: 'error',
                  finished: true,
                  onOpen: noop,
                  onTogglePin: noop,
                  onRemove: noop,
                },
                {
                  key: 'idle',
                  name: '条目四',
                  finished: true,
                  onOpen: noop,
                  onTogglePin: noop,
                  onRemove: noop,
                },
              ]}
            />
            <SessionGroup label="分组三" emptyLabel="空态文案" rows={[]} />
            <nav className="flex flex-col gap-sm" aria-label="实验台导航">
              <SidebarAction label="分区二" icon={<BarChart3 />} to="/analytics" />
            </nav>
          </>
        )}
      </SidebarFrame>
    </div>
  )
}

/** One answer composer, holding its own draft and mode so the toggle works exactly the
 *  way it does in a session. `voice` freezes the lane in a state a live session only
 *  reaches while it is connected; without it the row starts in text mode and the frozen
 *  idle lane is what the microphone button switches to. */
function LabAnswerRow({ voice }: { voice?: { status: VoiceStatus; recording: boolean } }) {
  const [answer, setAnswer] = useState('')
  const [voiceOpen, setVoiceOpen] = useState(Boolean(voice))
  const noop = () => undefined
  return (
    <AnswerComposerSurface
      answer={answer}
      attachments={sampleAttachments}
      disabled={false}
      jdMatched
      modelName={sampleModelName}
      positionName={sampleContextNames.positionName}
      resumeName={sampleContextNames.resumeName}
      sending={false}
      voice={voiceOpen ? (voice ?? { status: 'idle', recording: false }) : undefined}
      onAnswerChange={setAnswer}
      onHoldEnd={noop}
      onHoldStart={noop}
      onSubmit={(event) => event.preventDefault()}
      onToggleVoice={() => setVoiceOpen((open) => !open)}
    />
  )
}

export function ComponentLab() {
  const feedback = useFeedback()
  const [model, setModel] = useState('first')
  const [llmConfig, setLlmConfig] = useState(sampleModelConfig)
  const [segmentTwo, setSegmentTwo] = useState('one')
  const [segmentThree, setSegmentThree] = useState('one')
  const [sort, setSort] = useState('recent')
  const [reasoning, setReasoning] = useState<ReasoningLevel>('AUTO')
  const [jdMatch, setJdMatch] = useState(true)
  const [showPassword, setShowPassword] = useState(false)
  const [pressed, setPressed] = useState(false)
  const [tab, setTab] = useState(0)
  const [themeChoice, setThemeChoice] = useState('light')
  const [dialogOpen, setDialogOpen] = useState(false)
  const [workspaceDialogOpen, setWorkspaceDialogOpen] = useState(false)

  const noop = () => undefined

  return (
    <section className="workspace-page">
      <header className="workspace-header">
        <div className="workspace-header__main">
          <div className="workspace-header__title-area">
            <h1 className="workspace-header__title">Component Lab</h1>
          </div>
        </div>
      </header>
      <div className="workspace-page__content scrollable">
        <Panel layout="card" title="Typography" description="shared/styles">
          <div className="grid gap-sm">
            <p className="type-eyebrow">示例文本 · type-eyebrow</p>
            <p className="type-hero">示例文本 · type-hero</p>
            <p className="type-title">示例文本 · type-title</p>
            <p className="type-subtitle">示例文本 · type-subtitle</p>
            <p className="type-label">示例文本 · type-label</p>
            <p className="type-body">示例文本 · type-body</p>
            <p className="type-meta">示例文本 · type-meta</p>
            <p className="type-metric">92</p>
          </div>
        </Panel>

        <Panel layout="card" title="Panel" description="shared/ui/panel">
          <div className="grid h-(--layout-demo-frame-block-size) w-full overflow-hidden rounded-lg border border-border">
            <Panel title="面板标题" actions={<Button>主要操作</Button>}>
              <p className="type-body">示例正文一。</p>
              <p className="type-body">示例正文二。</p>
              <p className="type-body">示例正文三。</p>
              <p className="type-body">示例正文四。</p>
            </Panel>
          </div>
        </Panel>

        <Panel layout="card" title="Button" description="shared/ui/button">
          <DemoGroup label="Variant">
            <Button>主要操作</Button>
            <Button variant="secondary">次要操作</Button>
            <Button variant="ghost">轻操作</Button>
            <Button variant="danger">破坏性操作</Button>
          </DemoGroup>
          <DemoGroup label="State">
            <Button loading>处理中</Button>
            <Button disabled>不可用</Button>
            <Button
              pressed={pressed}
              onClick={() => {
                setPressed((value) => !value)
                feedback.notify(pressed ? '已取消选中' : '已选中', 'info')
              }}
            >
              按下态
            </Button>
          </DemoGroup>
          <DemoGroup label="Box">
            <Button size="icon" aria-label="图标操作">
              <Plus />
            </Button>
          </DemoGroup>
          <DemoGroup label="Shape">
            <Button shape="action">固定宽度动作</Button>
            <Button shape="hold">按住式动作</Button>
          </DemoGroup>
        </Panel>

        <Panel layout="card" title="Field" description="shared/ui/field">
          <div className="form-grid gap-md">
            <Field label="字段一" htmlFor="lab-input">
              <Input id="lab-input" placeholder="示例占位" />
            </Field>
            <Field label="字段二" htmlFor="lab-select" hint="说明文本">
              <Select
                id="lab-select"
                value={model}
                options={[
                  { value: 'first', label: '选项一' },
                  { value: 'second', label: '选项二' },
                ]}
                onValueChange={setModel}
              />
            </Field>
            <Field label="字段三" htmlFor="lab-textarea">
              <Textarea id="lab-textarea" placeholder="示例占位" />
            </Field>
            <Field label="字段四" htmlFor="lab-password" hint="说明文本">
              <FieldActions
                actions={[
                  <FieldAction
                    label={showPassword ? '隐藏密码' : '显示密码'}
                    icon={showPassword ? <Eye /> : <EyeOff />}
                    onClick={() => setShowPassword((value) => !value)}
                  />,
                ]}
              >
                <Input
                  id="lab-password"
                  type={showPassword ? 'text' : 'password'}
                  defaultValue="示例值"
                />
              </FieldActions>
            </Field>
            <Field label="字段五" htmlFor="lab-disabled">
              <Input id="lab-disabled" disabled placeholder="示例占位" />
            </Field>
          </div>
          <div className="grid gap-sm border-t border-border pt-md">
            <h3 className="type-subtitle">小节一</h3>
            <div className="form-grid gap-md">
              <Field label="字段六" htmlFor="lab-reasoning">
                <Select
                  id="lab-reasoning"
                  value={reasoning}
                  options={Object.keys(REASONING_LABELS).map((value, index) => ({
                    value,
                    label: `选项${ordinals[index]}`,
                  }))}
                  onValueChange={(value) => setReasoning(value as ReasoningLevel)}
                />
              </Field>
              <Field label="字段七" htmlFor="lab-output">
                <Input id="lab-output" defaultValue="示例值" />
              </Field>
            </div>
          </div>
        </Panel>

        <Panel layout="card" title="SegmentedControl" description="shared/ui/segmented-control">
          <DemoGroup label="两项">
            <SegmentedControl
              ariaLabel="实验台两项分段"
              items={[
                { value: 'one', label: '选项一' },
                { value: 'two', label: '选项二' },
              ]}
              value={segmentTwo}
              onValueChange={setSegmentTwo}
            />
          </DemoGroup>
          <DemoGroup label="三项">
            <SegmentedControl
              ariaLabel="实验台三项分段"
              items={[
                { value: 'one', label: '选项一' },
                { value: 'two', label: '选项二' },
                { value: 'three', label: '选项三' },
              ]}
              value={segmentThree}
              onValueChange={setSegmentThree}
            />
          </DemoGroup>
        </Panel>

        <Panel
          layout="card"
          title="Prompt Bar"
          description="features/interview · shared/ui/prompt-bar"
        >
          <DemoGroup label="准备态">
            <div className="w-full">
              <InterviewSetupComposer
                resumes={sampleResumes}
                positions={samplePositions}
                llmConfig={llmConfig}
                llmProviders={sampleModelProviders}
                uploadingAttachment={false}
                savingModel={false}
                creating={false}
                onUploadAttachment={rejectUpload}
                onDeleteAttachment={ignoreDelete}
                onModelChange={(model) => setLlmConfig((config) => ({ ...config, model }))}
                onThinkingDepthChange={(level) =>
                  setLlmConfig((config) => ({ ...config, reasoningLevel: level ?? 'AUTO' }))
                }
                onManageModel={noop}
                onNewResume={noop}
                onNewPosition={noop}
                onStart={noop}
              />
            </div>
          </DemoGroup>
          <DemoGroup label="回答态">
            <LabAnswerRow />
          </DemoGroup>
          <DemoGroup label="语音态 · 聆听">
            <LabAnswerRow voice={{ status: 'listening', recording: true }} />
          </DemoGroup>
          <DemoGroup label="语音态 · 播报">
            <LabAnswerRow voice={{ status: 'speaking', recording: false }} />
          </DemoGroup>
          <DemoGroup label="上下文与事实位">
            <ContextAttachment
              kind="resume"
              label={sampleContextNames.resumeName}
              onRemove={noop}
            />
            <ContextAttachment
              kind="position"
              label={sampleContextNames.positionName}
              onRemove={noop}
            />
            <ContextAttachment kind="document" label="示例文件三.pdf" />
            <ContextAttachment kind="image" label="示例图片一.png" />
            <PromptBarJdToggle onDisable={noop} />
            <PromptBarFact label={sampleModelName} icon={<Terminal aria-hidden="true" />} />
          </DemoGroup>
        </Panel>

        <Panel
          layout="card"
          title="Conversation"
          description="shared/ui/message · shared/ui/generating-card"
        >
          <DemoGroup label="回合">
            <div className="flex w-full flex-col">
              <MessageBubble side="assistant" speaker="说话人一">
                示例文本一，长度用来检查气泡的最大宽度与换行。示例文本二。
              </MessageBubble>
              <MessageBubble side="user" speaker="说话人二">
                示例文本一，长度用来检查用户侧的对齐与内边距。示例文本二。
              </MessageBubble>
              <MessageBubble side="assistant" speaker="说话人一" pending />
            </div>
          </DemoGroup>
          <DemoGroup label="生成态">
            <div className="flex w-full items-center justify-center bg-surface p-xl">
              <GeneratingCard
                title="示例标题"
                hint="示例文本一，长度用来检查卡片在长提示下的换行。"
              />
            </div>
          </DemoGroup>
        </Panel>

        <Panel
          layout="card"
          title="App rail"
          description="shared/ui/sidebar · shared/ui/session-row"
        >
          <div className="flex flex-wrap items-start gap-lg">
            <DemoGroup label="展开">
              <LabRail />
            </DemoGroup>
            <DemoGroup label="折叠">
              <LabRail collapsed />
            </DemoGroup>
          </div>
        </Panel>

        <Panel layout="card" title="List & Navigation" description="shared/ui · shared/styles">
          <div className="flex w-full items-start gap-lg">
            <div className="w-(--layout-settings-sidebar-inline-size) shrink-0">
              <DemoGroup label="导航列">
                <div className="grid gap-sm">
                  {sections.map(({ key, icon: Icon }, index) => (
                    <NavItem
                      key={key}
                      active={tab === index}
                      icon={<Icon aria-hidden="true" />}
                      label={`分区${ordinals[index]}`}
                      onClick={() => setTab(index)}
                    />
                  ))}
                  <NavItem
                    label="危险操作"
                    tone="danger"
                    icon={<LogOut aria-hidden="true" />}
                    onClick={noop}
                  />
                </div>
              </DemoGroup>
            </div>
            <div className="grid min-w-0 flex-1 gap-md">
              <DemoGroup label="列表行">
                <div className="grid w-full gap-sm">
                  {sampleResumes.map((resume) => (
                    <ResumeRow key={resume.id} resume={resume} onDelete={noop} />
                  ))}
                </div>
              </DemoGroup>
              <DemoGroup label="只读行">
                <div className="position-item-grid w-full" role="list" aria-label="岗位列表">
                  {samplePositions.map((position: Position) => (
                    <PositionRow
                      key={position.id}
                      name={position.name}
                      editable={position.editable}
                      onEdit={noop}
                    />
                  ))}
                </div>
              </DemoGroup>
              <DemoGroup label="选项卡">
                <div
                  className="grid w-full grid-cols-3 gap-sm"
                  role="radiogroup"
                  aria-label="主题偏好"
                >
                  {themeOptions.map((option, index) => (
                    <OptionCard
                      key={option.value}
                      checked={themeChoice === option.value}
                      label={`选项${ordinals[index]}`}
                      description={`说明${ordinals[index]}`}
                      onSelect={() => setThemeChoice(option.value)}
                    >
                      <ThemePreview tone={option.value} />
                    </OptionCard>
                  ))}
                </div>
              </DemoGroup>
            </div>
          </div>
        </Panel>

        {/* The report is a page surface, so it is shown at the width the product
            gives it rather than inside a card: a `card` Panel is already capped at
            that width, and the skin's own padding would steal the last column. */}
        <section
          className="grid max-w-(--layout-workspace-content-max-inline-size) gap-lg"
          data-slot="lab-report"
        >
          <div className="grid gap-xs">
            <h2 className="type-title">Report</h2>
            <p className="type-meta">features/report</p>
          </div>
          <div className="max-w-(--layout-workspace-content-max-inline-size)">
            <StructuredReport report={sampleReport} copy={sampleReportCopy} />
          </div>
        </section>

        <Panel layout="card" title="Empty & Error" description="shared/styles">
          <DemoGroup label="状态">
            <div className="empty-state w-full">加载示例文本</div>
            <div className="empty-state w-full">
              <p>失败示例文本</p>
              <Button variant="secondary" onClick={() => feedback.notify('示例提示', 'success')}>
                <RefreshCw />
                次要操作
              </Button>
            </div>
          </DemoGroup>
        </Panel>

        <Panel layout="card" title="DropdownMenu" description="shared/ui/menu">
          <DemoGroup label="普通菜单">
            <DropdownMenu trigger={<Button variant="secondary">菜单触发器</Button>}>
              <DropdownMenuGroup>
                <DropdownMenuItem onClick={() => feedback.notify('已执行菜单项', 'success')}>
                  菜单项
                </DropdownMenuItem>
                <DropdownMenuSubmenu trigger="子菜单">
                  <DropdownMenuRadioGroup value={sort} onValueChange={setSort}>
                    <DropdownMenuRadioItem value="recent">单选项一</DropdownMenuRadioItem>
                    <DropdownMenuRadioItem value="created">单选项二</DropdownMenuRadioItem>
                  </DropdownMenuRadioGroup>
                </DropdownMenuSubmenu>
                <DropdownMenuSeparator />
                <DropdownMenuCheckboxItem checked={jdMatch} onCheckedChange={setJdMatch}>
                  多选项
                </DropdownMenuCheckboxItem>
                <DropdownMenuItem disabled>禁用项</DropdownMenuItem>
              </DropdownMenuGroup>
            </DropdownMenu>
          </DemoGroup>
          <DemoGroup label="结构化布局">
            <DropdownMenu
              layout="structured"
              align="end"
              trigger={<Button variant="secondary">结构化菜单</Button>}
            >
              <DropdownMenuGroup>
                <DropdownMenuItem layout="leading-icon" onClick={noop}>
                  带图标菜单项
                </DropdownMenuItem>
                <DropdownMenuSeparator />
                <DropdownMenuItem disabled>只读信息项</DropdownMenuItem>
              </DropdownMenuGroup>
            </DropdownMenu>
          </DemoGroup>
        </Panel>

        <Panel
          layout="card"
          title="Overlay & Feedback"
          description="shared/ui/overlay · shared/ui/feedback"
        >
          <DemoGroup label="Tooltip 与 Dialog">
            <IconTooltip label="提示">
              <Button size="icon" variant="secondary" aria-label="提示">
                <Info />
              </Button>
            </IconTooltip>
            <Button variant="secondary" onClick={() => setDialogOpen(true)}>
              打开浮层
            </Button>
            <Button variant="secondary" onClick={() => setWorkspaceDialogOpen(true)}>
              打开工作台浮层
            </Button>
          </DemoGroup>
          <DemoGroup label="Toast">
            <Button variant="secondary" onClick={() => feedback.notify('示例提示', 'success')}>
              成功
            </Button>
            <Button variant="secondary" onClick={() => feedback.notify('示例提示', 'info')}>
              信息
            </Button>
            <Button variant="secondary" onClick={() => feedback.notify('示例提示', 'warning')}>
              警告
            </Button>
            <Button variant="secondary" onClick={() => feedback.notify('示例提示', 'error')}>
              错误
            </Button>
          </DemoGroup>
          <DemoGroup label="Confirm">
            <Button
              variant="secondary"
              onClick={() => {
                void feedback
                  .confirm({ title: '确认操作', message: '示例文本一。示例文本二。' })
                  .then((accepted) => accepted && feedback.notify('示例提示', 'success'))
              }}
            >
              普通确认
            </Button>
            <Button
              variant="danger"
              onClick={() => {
                void feedback
                  .confirm({
                    title: '危险确认',
                    message: '示例文本一，长度用来检查确认框正文的换行。示例文本二。',
                    confirmText: '危险操作',
                    danger: true,
                  })
                  .then((accepted) => accepted && feedback.notify('示例提示', 'error'))
              }}
            >
              危险确认
            </Button>
          </DemoGroup>
        </Panel>

        <Panel layout="card" title="Brand" description="shared/brand">
          <div className="flex flex-wrap items-center gap-lg">
            <RoseThree className="size-(--layout-generating-rose-inline-size) text-brand" />
            <BrandMetaballs className="size-(--layout-brand-mark-inline-size) rounded-full" />
          </div>
        </Panel>
      </div>

      <Dialog open={dialogOpen} onOpenChange={setDialogOpen} title="浮层">
        <div className="grid gap-md">
          <h2 className="type-title">小节一</h2>
          <p className="type-body">示例文本一，长度用来检查浮层正文的换行。示例文本二。</p>
          <div className="flex justify-end gap-sm">
            <Button onClick={() => setDialogOpen(false)}>主要操作</Button>
          </div>
        </div>
      </Dialog>

      <Dialog
        layout="workspace"
        open={workspaceDialogOpen}
        onOpenChange={setWorkspaceDialogOpen}
        title="工作台浮层"
      >
        <Panel
          className="size-full min-h-0"
          title="面板标题"
          description="shared/ui/panel"
          actions={
            <Button
              size="icon"
              variant="ghost"
              aria-label="关闭工作台浮层"
              onClick={() => setWorkspaceDialogOpen(false)}
            >
              <X />
            </Button>
          }
          footer={
            <Button
              onClick={() => {
                setWorkspaceDialogOpen(false)
                feedback.notify('示例提示', 'success')
              }}
            >
              主要操作
            </Button>
          }
        >
          <p className="type-body">
            示例文本一，长度用来检查内容区在浮层高度下的滚动与内边距。示例文本二。示例文本三。
          </p>
        </Panel>
      </Dialog>
    </section>
  )
}
