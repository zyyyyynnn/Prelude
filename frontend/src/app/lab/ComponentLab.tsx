import { useState } from 'react'
import { AnswerComposerSurface, InterviewSetupComposer, SessionGroup } from '@/features/interview'
import { PositionRow, type Position } from '@/features/position'
import {
  REASONING_LABELS,
  sections,
  themeOptions,
  SettingsNavigation,
  ThemeChoiceGroup,
  type SettingsSection,
  type ThemeTone,
} from '@/features/settings'
import type { ReasoningLevel } from '@/features/settings'
import { BrandMetaballs } from '@/shared/brand/BrandMetaballs'
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
  sampleToolTrace,
} from './samples'
import type { ReactNode } from 'react'
import {
  RiAddLine,
  RiAttachmentLine,
  RiBarChartLine,
  RiBriefcaseLine,
  RiCloseLine,
  RiEyeLine,
  RiEyeOffLine,
  RiFileTextLine,
  RiImageLine,
  RiInformationLine,
  RiLogoutBoxLine,
  RiSettings3Line,
  RiSideBarLine,
  RiTerminalBoxLine,
} from '@remixicon/react'
import {
  Button,
  ContextAttachment,
  Dialog,
  DropdownMenu,
  DropdownMenuCheckboxItem,
  DropdownMenuGroup,
  DropdownMenuItem,
  DropdownMenuRadioGroup,
  DropdownMenuRadioItem,
  DropdownMenuSeparator,
  DropdownMenuSubmenu,
  EmptyState,
  ErrorState,
  Field,
  FieldAction,
  FieldActions,
  GeneratingSurface,
  Icon,
  IconTooltip,
  Input,
  LoadingState,
  MessageBubble,
  PageHeader,
  Panel,
  PromptBarFact,
  PromptBarToggle,
  ScrollRegion,
  SegmentedControl,
  Select,
  SidebarAction,
  SidebarBrand,
  SidebarFrame,
  SubSection,
  Textarea,
  ToolTrace,
  useFeedback,
} from '@/shared/ui'
import type { VoiceStatus } from '@/shared/ui'

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
  const [workspace, setWorkspace] = useState<'interview' | 'resume'>('interview')
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
          <div className="grid gap-sm">
            {!collapsed && (
              <SegmentedControl
                ariaLabel="工作区"
                items={[
                  { value: 'interview', label: '面试' },
                  { value: 'resume', label: '简历' },
                ]}
                value={workspace}
                onValueChange={setWorkspace}
              />
            )}
            <SidebarAction
              collapsed={collapsed}
              label="主要操作"
              icon={<Icon as={RiAddLine} />}
              tone="primary"
              onClick={noop}
            />
          </div>
        }
        footer={
          <SidebarAction
            collapsed={collapsed}
            label="次要操作"
            icon={<Icon as={RiSettings3Line} />}
            onClick={noop}
          />
        }
      >
        {collapsed ? (
          <nav className="flex flex-col gap-sm" aria-label="实验台导航">
            <SidebarAction
              collapsed
              label="分区一"
              icon={<Icon as={RiSideBarLine} />}
              to="/interview"
            />
            <SidebarAction
              collapsed
              label="分区二"
              icon={<Icon as={RiBarChartLine} />}
              to="/analytics"
            />
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
              <SidebarAction label="分区二" icon={<Icon as={RiBarChartLine} />} to="/analytics" />
            </nav>
          </>
        )}
      </SidebarFrame>
    </div>
  )
}

/** One answer composer, holding its own draft and mode so the toggle and the send button
 *  work exactly the way they do in a session. `voice` freezes the lane in a state a live
 *  session only reaches while it is connected, and `draft` seeds the text a transcript
 *  would have left behind; the meter reads no microphone here, so it holds the floor
 *  height the browser shows before any audio arrives. */
function LabAnswerRow({
  voice,
  draft = '',
  modelName = sampleModelName,
}: {
  voice?: { status: VoiceStatus; recording: boolean }
  draft?: string
  modelName?: string
}) {
  const [answer, setAnswer] = useState(draft)
  const [voiceOpen, setVoiceOpen] = useState(Boolean(voice))
  const noop = () => undefined
  return (
    <AnswerComposerSurface
      answer={answer}
      attachments={sampleAttachments}
      disabled={false}
      jdMatched
      modelName={modelName}
      positionName={sampleContextNames.positionName}
      resumeName={sampleContextNames.resumeName}
      sending={false}
      voice={
        voiceOpen ? { ...(voice ?? { status: 'idle', recording: false }), media: null } : undefined
      }
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
  const [settingsSection, setSettingsSection] = useState<SettingsSection>('profile')
  const [themeChoice, setThemeChoice] = useState<ThemeTone>('light')
  const [dialogOpen, setDialogOpen] = useState(false)
  const [workspaceDialogOpen, setWorkspaceDialogOpen] = useState(false)

  const noop = () => undefined
  const modelReasoningLabel =
    llmConfig.capability.model === llmConfig.model && llmConfig.capability.reasoning
      ? ` · ${REASONING_LABELS[llmConfig.reasoningLevel]}`
      : ''
  const modelLabel = `${llmConfig.model}${modelReasoningLabel}`

  return (
    <section className="workspace-page">
      <PageHeader title="Component Lab" />
      <ScrollRegion shell="workspace-page__content">
        <Panel layout="card" title="Brand" description="shared/brand">
          <DemoGroup label="标识组合">
            <div className="flex flex-wrap items-center gap-lg">
              <BrandMetaballs className="size-(--layout-brand-mark-inline-size) rounded-full" />
              <span className="font-serif text-lg font-medium text-text-primary">Prelude</span>
            </div>
          </DemoGroup>
          <DemoGroup label="核心色板">
            <div className="grid w-full grid-cols-6 gap-sm">
              <div className="flex flex-col gap-xs rounded-md border border-border-warm bg-surface p-sm">
                <div className="h-(--ui-height-control) w-full rounded bg-brand" />
                <span className="type-label">Brand</span>
                <span className="type-meta">--color-brand</span>
              </div>
              <div className="flex flex-col gap-xs rounded-md border border-border-warm bg-surface p-sm">
                <div className="h-(--ui-height-control) w-full rounded border border-border-warm bg-surface" />
                <span className="type-label">Surface</span>
                <span className="type-meta">--color-surface</span>
              </div>
              <div className="flex flex-col gap-xs rounded-md border border-border-warm bg-surface p-sm">
                <div className="h-(--ui-height-control) w-full rounded border border-border-warm bg-bg" />
                <span className="type-label">Background</span>
                <span className="type-meta">--color-bg</span>
              </div>
              <div className="flex flex-col gap-xs rounded-md border border-border-warm bg-surface p-sm">
                <div className="h-(--ui-height-control) w-full rounded bg-text-primary" />
                <span className="type-label">Primary</span>
                <span className="type-meta">--color-text-primary</span>
              </div>
              <div className="flex flex-col gap-xs rounded-md border border-border-warm bg-surface p-sm">
                <div className="h-(--ui-height-control) w-full rounded bg-text-secondary" />
                <span className="type-label">Secondary</span>
                <span className="type-meta">--color-text-secondary</span>
              </div>
              <div className="flex flex-col gap-xs rounded-md border border-border-warm bg-surface p-sm">
                <div className="h-(--ui-height-control) w-full rounded bg-error" />
                <span className="type-label">Error</span>
                <span className="type-meta">--color-error</span>
              </div>
            </div>
          </DemoGroup>
        </Panel>

        <Panel layout="card" title="Typography" description="shared/styles">
          <div className="grid gap-md">
            <div className="flex flex-col gap-xs rounded-md border border-border-warm bg-surface p-md">
              <div className="flex flex-wrap items-center justify-between gap-xs">
                <span className="type-label">type-hero</span>
                <span className="font-mono text-xs text-text-tertiary">
                  --font-size-2xl (40px) · display (1.15) · medium · Lora
                </span>
              </div>
              <p className="type-hero">页面大标题 · Prelude Typography</p>
            </div>

            <div className="flex flex-col gap-xs rounded-md border border-border-warm bg-surface p-md">
              <div className="flex flex-wrap items-center justify-between gap-xs">
                <span className="type-label">type-document-title</span>
                <span className="font-mono text-xs text-text-tertiary">
                  --font-size-xl (32px) · display (1.15) · medium · Lora
                </span>
              </div>
              <p className="type-document-title">文档报告主标题 · Evaluation Report</p>
            </div>

            <div className="flex flex-col gap-xs rounded-md border border-border-warm bg-surface p-md">
              <div className="flex flex-wrap items-center justify-between gap-xs">
                <span className="type-label">type-title</span>
                <span className="font-mono text-xs text-text-tertiary">
                  --font-size-lg (24px) · tight (1.25) · medium · Lora
                </span>
              </div>
              <p className="type-title">区块主标题 · Section Title</p>
            </div>

            <div className="flex flex-col gap-xs rounded-md border border-border-warm bg-surface p-md">
              <div className="flex flex-wrap items-center justify-between gap-xs">
                <span className="type-label">type-subtitle</span>
                <span className="font-mono text-xs text-text-tertiary">
                  --font-size-md (16px) · compact (1.4) · medium · Lora
                </span>
              </div>
              <p className="type-subtitle">次级副标题 · Subtitle & Subsection</p>
            </div>

            <div className="grid grid-cols-2 gap-sm">
              <div className="flex flex-col gap-xs rounded-md border border-border-warm bg-surface p-md">
                <div className="flex flex-wrap items-center justify-between gap-xs">
                  <span className="type-label">type-label</span>
                  <span className="font-mono text-xs text-text-tertiary">
                    --font-size-sm (14px) · compact · Lora
                  </span>
                </div>
                <p className="type-label">字段与条目标签 · Field Label</p>
              </div>

              <div className="flex flex-col gap-xs rounded-md border border-border-warm bg-surface p-md">
                <div className="flex flex-wrap items-center justify-between gap-xs">
                  <span className="type-label">type-eyebrow</span>
                  <span className="font-mono text-xs text-text-tertiary">
                    --font-size-xs (12px) · tight · Lora
                  </span>
                </div>
                <p className="type-eyebrow">EYEBROW · 标题引导标签</p>
              </div>
            </div>

            <div className="flex flex-col gap-xs rounded-md border border-border-warm bg-surface p-md">
              <div className="flex flex-wrap items-center justify-between gap-xs">
                <span className="type-label">type-body</span>
                <span className="font-mono text-xs text-text-tertiary">
                  --font-size-sm (14px) · relaxed (1.6) · regular · Inter
                </span>
              </div>
              <p className="type-body">
                成段正文文本，用于对话气泡、设置项说明与通用表单提示。保持舒适的行高与清晰的无衬线体字形可读性。
              </p>
            </div>

            <div className="flex flex-col gap-xs rounded-md border border-border-warm bg-surface p-md">
              <div className="flex flex-wrap items-center justify-between gap-xs">
                <span className="type-label">type-lead & type-reading</span>
                <span className="font-mono text-xs text-text-tertiary">
                  --font-size-sm (14px) · copy (1.7) · Inter · 约束行宽
                </span>
              </div>
              <p className="type-lead">
                导语文本 · 采用限定阅读宽度（58ch），提供舒适的首段视觉焦点。
              </p>
              <p className="type-reading">
                报告正文 · 采用报告专有阅读宽度（68ch）与 copy 行高，保证长文阅读体验。
              </p>
            </div>

            <div className="grid grid-cols-3 gap-sm">
              <div className="flex flex-col gap-xs rounded-md border border-border-warm bg-surface p-md">
                <div className="flex flex-wrap items-center justify-between gap-xs">
                  <span className="type-label">type-caption</span>
                  <span className="font-mono text-xs text-text-tertiary">12px · Lora</span>
                </div>
                <p className="type-caption">图注与时间戳 · 12:45 PM</p>
              </div>

              <div className="flex flex-col gap-xs rounded-md border border-border-warm bg-surface p-md">
                <div className="flex flex-wrap items-center justify-between gap-xs">
                  <span className="type-label">type-meta</span>
                  <span className="font-mono text-xs text-text-tertiary">12px · Inter</span>
                </div>
                <p className="type-meta">计数与辅助说明 · 共 12 个条目</p>
              </div>

              <div className="flex flex-col gap-xs rounded-md border border-border-warm bg-surface p-md">
                <div className="flex flex-wrap items-center justify-between gap-xs">
                  <span className="type-label">type-metric</span>
                  <span className="font-mono text-xs text-text-tertiary">32px · Tabular</span>
                </div>
                <p className="type-metric">92.8%</p>
              </div>
            </div>
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
              <Icon as={RiAddLine} />
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
                    icon={showPassword ? <Icon as={RiEyeLine} /> : <Icon as={RiEyeOffLine} />}
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
            <Field label="字段五 · 错误态" htmlFor="lab-error" error="示例错误提示：输入格式不正确">
              <Input id="lab-error" aria-invalid="true" defaultValue="非法输入值" />
            </Field>
          </div>
          <SubSection title="小节一">
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
          </SubSection>
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

        <Panel layout="card" title="Panel" description="shared/ui/panel">
          <div className="demo-frame grid h-(--layout-demo-frame-block-size) w-full overflow-hidden">
            <Panel title="面板标题" actions={<Button>主要操作</Button>}>
              <p className="type-body">示例正文一。</p>
              <p className="type-body">示例正文二。</p>
              <p className="type-body">示例正文三。</p>
              <p className="type-body">示例正文四。</p>
            </Panel>
          </div>
        </Panel>

        <Panel
          layout="card"
          title="App rail"
          description="shared/ui/sidebar · features/interview/components/session-row"
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
            <LabAnswerRow modelName={modelLabel} />
          </DemoGroup>
          <DemoGroup label="语音态 · 按住">
            <LabAnswerRow modelName={modelLabel} voice={{ status: 'listening', recording: true }} />
          </DemoGroup>
          <DemoGroup label="语音态 · 处理中">
            <LabAnswerRow
              modelName={modelLabel}
              voice={{ status: 'processing', recording: false }}
              draft="示例文本一。"
            />
          </DemoGroup>
          <DemoGroup label="上下文与事实位">
            <ContextAttachment
              icon={<Icon as={RiFileTextLine} aria-hidden="true" />}
              kindLabel="简历"
              label={sampleContextNames.resumeName}
              onRemove={noop}
            />
            <ContextAttachment
              icon={<Icon as={RiBriefcaseLine} aria-hidden="true" />}
              kindLabel="岗位"
              label={sampleContextNames.positionName}
              onRemove={noop}
            />
            <ContextAttachment
              icon={<Icon as={RiAttachmentLine} aria-hidden="true" />}
              kindLabel="附件"
              label="示例文件三.pdf"
            />
            <ContextAttachment
              icon={<Icon as={RiImageLine} aria-hidden="true" />}
              kindLabel="图片"
              label="示例图片一.png"
            />
            <PromptBarToggle label="JD 匹配" onDisable={noop} />
            <PromptBarFact
              label={modelLabel}
              icon={<Icon as={RiTerminalBoxLine} aria-hidden="true" />}
            />
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
            <GeneratingSurface
              title="示例标题"
              hint="示例文本一，长度用来检查卡片在长提示下的换行。"
            />
          </DemoGroup>
        </Panel>

        <Panel
          layout="card"
          title="Tool Trace"
          description="shared/ui/tool-trace · shared/ui/loading-indicator"
        >
          <DemoGroup label="完成态">
            <ToolTrace
              labels={{ summary: sampleToolTrace.summary, status: '已完成 · 8s' }}
              defaultOpenRows={['think-3', 'edit-exp', 'run-failed']}
              steps={sampleToolTrace.steps}
            />
          </DemoGroup>
          <DemoGroup label="运行态">
            <ToolTrace
              running
              labels={{ summary: '思考 1轮 · 读1次文件…', status: '正在处理 · 37s' }}
              steps={[
                { id: 'run-think', icon: 'think', text: '思考了 3s' },
                { id: 'run-read', icon: 'read', text: '读取', chips: ['示例文件一.md'] },
              ]}
            />
          </DemoGroup>
        </Panel>

        <Panel
          layout="card"
          title="List & Navigation"
          description="features/settings · features/resume/ResumeRow · features/position/PositionRow · shared/ui/option-card"
        >
          <div className="flex w-full items-start gap-lg">
            <div className="shrink-0">
              <DemoGroup label="导航列">
                <SettingsNavigation
                  items={sections.map(({ key, icon: Glyph }, index) => ({
                    key,
                    label: `分区${ordinals[index]}`,
                    icon: <Icon as={Glyph} />,
                  }))}
                  active={settingsSection}
                  onSelect={setSettingsSection}
                  danger={{
                    label: '危险操作',
                    icon: <Icon as={RiLogoutBoxLine} aria-hidden="true" />,
                    onSelect: noop,
                  }}
                />
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
                <div className="item-grid w-full" role="list" aria-label="岗位列表">
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
                <div className="grid w-full gap-sm">
                  <ThemeChoiceGroup
                    value={themeChoice}
                    options={themeOptions.map((option, index) => ({
                      value: option.value,
                      label: `选项${ordinals[index]}`,
                      description: `说明${ordinals[index]}`,
                    }))}
                    onSelect={setThemeChoice}
                  />
                </div>
              </DemoGroup>
            </div>
          </div>
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
                <DropdownMenuItem icon={<Icon as={RiAddLine} />} onClick={noop}>
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
          title="Empty & Error"
          description="shared/ui/empty-state · shared/styles"
        >
          <DemoGroup label="状态">
            <div className="grid w-full grid-cols-3 gap-md">
              <div className="flex min-h-(--layout-demo-frame-block-size) items-center justify-center rounded-lg border border-border-warm bg-surface p-md">
                <LoadingState message="加载示例文本" className="w-full" />
              </div>
              <div className="flex min-h-(--layout-demo-frame-block-size) items-center justify-center rounded-lg border border-border-warm bg-surface p-md">
                <EmptyState message="空态示例文本" className="w-full" />
              </div>
              <div className="flex min-h-(--layout-demo-frame-block-size) items-center justify-center rounded-lg border border-border-warm bg-surface p-md">
                <ErrorState
                  message="失败示例文本"
                  retryLabel="次要操作"
                  onRetry={() => feedback.notify('示例提示', 'success')}
                  className="w-full"
                />
              </div>
            </div>
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
                <Icon as={RiInformationLine} />
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
      </ScrollRegion>

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
              <Icon as={RiCloseLine} />
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
