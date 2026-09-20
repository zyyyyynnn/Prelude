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
import {
  ReportCarouselNavigation,
  ReviewDetail,
  ScoreCard,
  StructuredReport,
  Trait,
} from '@/features/report'
import { ResumeRow } from '@/features/resume'
import {
  ignoreDelete,
  rejectUpload,
  sampleAttachments,
  sampleContextNames,
  sampleModelConfig,
  sampleModelProviders,
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
            label="开始新面试"
            icon={<Plus />}
            tone="primary"
            onClick={noop}
          />
        }
        footer={
          <SidebarAction collapsed={collapsed} label="设置" icon={<Settings />} onClick={noop} />
        }
      >
        {collapsed ? (
          <nav className="flex flex-col gap-sm" aria-label="实验台导航">
            <SidebarAction collapsed label="工作区" icon={<PanelLeft />} to="/interview" />
            <SidebarAction collapsed label="数据看板" icon={<BarChart3 />} to="/analytics" />
          </nav>
        ) : (
          <>
            <SessionGroup
              label="进行中"
              emptyLabel="暂无会话"
              rows={[
                {
                  key: 'active',
                  name: '当前会话',
                  state: 'active',
                  pinned: true,
                  onOpen: noop,
                  onTogglePin: noop,
                  onRemove: noop,
                },
                {
                  key: 'loading',
                  name: '加载中会话',
                  state: 'loading',
                  onOpen: noop,
                  onTogglePin: noop,
                  onRemove: noop,
                },
              ]}
            />
            <SessionGroup
              label="已完成"
              emptyLabel="暂无会话"
              rows={[
                {
                  key: 'error',
                  name: '失败会话',
                  state: 'error',
                  finished: true,
                  onOpen: noop,
                  onTogglePin: noop,
                  onRemove: noop,
                },
                {
                  key: 'idle',
                  name: '已结束会话',
                  finished: true,
                  onOpen: noop,
                  onTogglePin: noop,
                  onRemove: noop,
                },
              ]}
            />
            <SessionGroup label="已归档" emptyLabel="暂无会话" rows={[]} />
            <nav className="flex flex-col gap-sm" aria-label="实验台导航">
              <SidebarAction label="数据看板" icon={<BarChart3 />} to="/analytics" />
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
      modelName="当前模型 · 默认"
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
  const [view, setView] = useState('session')
  const [sort, setSort] = useState('recent')
  const [reasoning, setReasoning] = useState<ReasoningLevel>('AUTO')
  const [jdMatch, setJdMatch] = useState(true)
  const [showPassword, setShowPassword] = useState(false)
  const [pressed, setPressed] = useState(false)
  const [tab, setTab] = useState('账号资料')
  const [themeChoice, setThemeChoice] = useState('light')
  const [dialogOpen, setDialogOpen] = useState(false)
  const [workspaceDialogOpen, setWorkspaceDialogOpen] = useState(false)
  const [sampleIndex, setSampleIndex] = useState(0)

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
        <Panel
          layout="card"
          title="Typography"
          description="shared/styles · type-* 角色，字号与行高成对绑定；h2 读 type-title，h3 读 type-subtitle"
        >
          <div className="grid gap-sm">
            <p className="type-eyebrow">段落引导 · type-eyebrow</p>
            <p className="type-hero">响应式大标题 · type-hero</p>
            <p className="type-title">区块标题 · type-title</p>
            <p className="type-subtitle">次级标题 · type-subtitle</p>
            <p className="type-label">字段标签 · type-label</p>
            <p className="type-body">正文，用于成段说明与列表描述。 · type-body</p>
            <p className="type-meta">辅助信息 · type-meta</p>
            <p className="type-metric">92</p>
          </div>
        </Panel>

        <Panel
          layout="card"
          title="Panel"
          description="shared/ui/panel · 标题行拥有标题、说明与右侧操作区；fill 形态撑满给定高度，内容超出时只有内容区滚动"
        >
          <div className="grid h-(--layout-demo-frame-block-size) w-full overflow-hidden rounded-lg border border-border">
            <Panel title="面板标题" actions={<Button>主要操作</Button>}>
              <p className="type-body">标题行不随滚动移动，内容区自带内边距并独立滚动。</p>
              <p className="type-body">正文示例，用于撑出可滚动的内容。</p>
              <p className="type-body">正文示例，用于撑出可滚动的内容。</p>
            </Panel>
          </div>
        </Panel>

        <Panel
          layout="card"
          title="Button"
          description="shared/ui/button · variant / box / shape / loading / pressed"
        >
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

        <Panel
          layout="card"
          title="Field"
          description="shared/ui/field · Field 组合标签、控件与说明，尾部操作位由 FieldActions 拥有"
        >
          <div className="form-grid gap-md">
            <Field label="用户名" htmlFor="lab-input">
              <Input id="lab-input" placeholder="请输入用户名" />
            </Field>
            <Field label="接入模型" htmlFor="lab-select" hint="切换后对新会话生效">
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
            <Field label="岗位描述" htmlFor="lab-textarea">
              <Textarea id="lab-textarea" placeholder="粘贴岗位描述" />
            </Field>
            <Field label="密码" htmlFor="lab-password" hint="尾部操作位按按钮数量留白">
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
            <Field label="只读字段" htmlFor="lab-disabled">
              <Input id="lab-disabled" disabled placeholder="不可编辑" />
            </Field>
          </div>
          <div className="grid gap-md border-t border-border pt-md">
            <h3 className="type-subtitle">小节</h3>
            <div className="form-grid gap-md">
              <Field label="思考深度" htmlFor="lab-reasoning">
                <Select
                  id="lab-reasoning"
                  value={reasoning}
                  options={Object.entries(REASONING_LABELS).map(([value, label]) => ({
                    value,
                    label,
                  }))}
                  onValueChange={(value) => setReasoning(value as ReasoningLevel)}
                />
              </Field>
              <Field label="最大回复长度" htmlFor="lab-output">
                <Input id="lab-output" defaultValue="示例值" />
              </Field>
            </div>
          </div>
        </Panel>

        <Panel
          layout="card"
          title="SegmentedControl"
          description="shared/ui/segmented-control · 单选分段轨道，滑块跟随当前项"
        >
          <SegmentedControl
            ariaLabel="实验台视图"
            items={[
              { value: 'session', label: '会话' },
              { value: 'report', label: '报告' },
              { value: 'analytics', label: '看板' },
            ]}
            value={view}
            onValueChange={setView}
          />
        </Panel>

        <Panel
          layout="card"
          title="Prompt Bar"
          description="features/interview + shared/ui/prompt-bar · 每一态都是面试里那个组合器本身"
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
            <ContextAttachment kind="resume" label="示例简历.pdf" onRemove={noop} />
            <ContextAttachment kind="position" label="示例岗位" onRemove={noop} />
            <ContextAttachment kind="document" label="示例文档.pdf" />
            <ContextAttachment kind="image" label="示例截图.png" />
            <PromptBarJdToggle onDisable={noop} />
            <PromptBarFact label="当前模型 · 默认" icon={<Terminal aria-hidden="true" />} />
          </DemoGroup>
        </Panel>

        <Panel
          layout="card"
          title="Conversation"
          description="shared/ui/message + generating-card · 实时回合只标说话人，评分归报告"
        >
          <DemoGroup label="回合">
            <div className="flex w-full flex-col">
              <MessageBubble side="assistant" speaker="面试官">
                示例问题，用于检查气泡的最大宽度、行高与换行。
              </MessageBubble>
              <MessageBubble side="user" speaker="我">
                示例回答，用于检查用户侧气泡的对齐、内边距与表面色。
              </MessageBubble>
              <MessageBubble side="assistant" speaker="面试官" pending />
            </div>
          </DemoGroup>
          <DemoGroup label="生成态">
            <div className="flex w-full items-center justify-center bg-surface p-xl">
              <GeneratingCard title="AI 评估报告生成中…" hint="正在整理答题表现并生成训练建议。" />
            </div>
          </DemoGroup>
        </Panel>

        <Panel
          layout="card"
          title="App rail"
          description="shared/ui/sidebar + session-row · 展开与折叠两态按内容高度呈现；会话分组、悬停动作与空态就在 rail 里，贴底与交叉淡入由工作区真实截图覆盖"
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
          title="List & Navigation"
          description="shared/ui + shared/styles · 左列按设置弹窗的真实导航宽度，右列是行与选项卡"
        >
          <div className="flex w-full items-start gap-lg">
            <div className="w-(--layout-settings-sidebar-inline-size) shrink-0">
              <DemoGroup label="分区导航">
                <div className="grid gap-sm">
                  {sections.map(({ key, title, icon: Icon }) => (
                    <NavItem
                      key={key}
                      active={tab === title}
                      icon={<Icon aria-hidden="true" />}
                      label={title}
                      onClick={() => setTab(title)}
                    />
                  ))}
                  <NavItem
                    label="退出登录"
                    tone="danger"
                    icon={<LogOut aria-hidden="true" />}
                    onClick={noop}
                  />
                </div>
              </DemoGroup>
            </div>
            <div className="grid min-w-0 flex-1 gap-md">
              <DemoGroup label="简历行">
                <div className="grid w-full gap-sm">
                  {sampleResumes.map((resume) => (
                    <ResumeRow key={resume.id} resume={resume} onDelete={noop} />
                  ))}
                </div>
              </DemoGroup>
              <DemoGroup label="岗位行">
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
                  {themeOptions.map((option) => (
                    <OptionCard
                      key={option.value}
                      checked={themeChoice === option.value}
                      label={option.label}
                      description={option.description}
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
            <p className="type-meta">
              features/report · StructuredReport 整页，与报告页同一内容宽度，样例文案按角色命名
            </p>
          </div>
          <div className="max-w-(--layout-workspace-content-max-inline-size)">
            <StructuredReport report={sampleReport} />
          </div>
        </section>

        <section
          className="grid max-w-(--layout-workspace-content-max-inline-size) gap-lg"
          data-slot="lab-report-blocks"
        >
          <div className="grid gap-xs">
            <h2 className="type-title">Report blocks</h2>
            <p className="type-meta">
              features/report · 轮播导航、空态分件与逐条复盘字段，都是整页里同一批组件
            </p>
          </div>
          <div className="max-w-(--layout-workspace-content-max-inline-size)">
            <div className="document-sheet w-full">
              <ScoreCard report={sampleReport} />
              <section className="border-t border-border py-lg">
                <header className="mb-lg flex items-center justify-between gap-lg">
                  <div>
                    <p className="type-eyebrow">阶段复盘</p>
                    <h2 className="type-title text-balance">轮播导航</h2>
                  </div>
                  <ReportCarouselNavigation
                    ariaLabel="实验台轮播导航"
                    index={sampleIndex}
                    count={3}
                    previousLabel="上一项"
                    nextLabel="下一项"
                    onPrevious={() => setSampleIndex((value) => Math.max(0, value - 1))}
                    onNext={() => setSampleIndex((value) => Math.min(2, value + 1))}
                  />
                </header>
                <div className="report-columns gap-xl">
                  <Trait title="核心优势" items={[]} empty="暂无可归纳的优势。" />
                  <Trait title="主要短板" items={[]} empty="暂无已沉淀的薄弱点。" />
                </div>
              </section>
              <section className="border-t border-border py-lg">
                <div className="grid gap-md">
                  <ReviewDetail label="评分理由" value="评分理由示例文本。" />
                  <ReviewDetail label="改进建议" value="改进建议示例文本。" />
                </div>
              </section>
            </div>
          </div>
        </section>

        <Panel
          layout="card"
          title="Empty & Error"
          description="shared/styles · empty-state 统一加载、空库与失败的落点"
        >
          <DemoGroup label="状态">
            <div className="empty-state w-full">正在读取简历库…</div>
            <div className="empty-state w-full">
              <p>简历服务暂时不可用。</p>
              <Button variant="secondary" onClick={() => feedback.notify('已重新加载', 'success')}>
                <RefreshCw />
                重新加载
              </Button>
            </div>
          </DemoGroup>
        </Panel>

        <Panel
          layout="card"
          title="DropdownMenu"
          description="shared/ui/menu · 分组、子菜单、单选与多选项，条目按角色命名"
        >
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
          description="shared/ui/overlay + feedback · 浮层与系统提示共用一套 chrome"
        >
          <DemoGroup label="Tooltip 与 Dialog">
            <IconTooltip label="提示">
              <Button size="icon" variant="secondary" aria-label="提示">
                <Info />
              </Button>
            </IconTooltip>
            <Button variant="secondary" onClick={() => setDialogOpen(true)}>
              打开 Dialog
            </Button>
            <Button variant="secondary" onClick={() => setWorkspaceDialogOpen(true)}>
              打开工作台 Dialog
            </Button>
          </DemoGroup>
          <DemoGroup label="Toast">
            <Button variant="secondary" onClick={() => feedback.notify('成功提示', 'success')}>
              成功
            </Button>
            <Button variant="secondary" onClick={() => feedback.notify('信息提示', 'info')}>
              信息
            </Button>
            <Button variant="secondary" onClick={() => feedback.notify('警告提示', 'warning')}>
              警告
            </Button>
            <Button variant="secondary" onClick={() => feedback.notify('错误提示', 'error')}>
              错误
            </Button>
          </DemoGroup>
          <DemoGroup label="Confirm">
            <Button
              variant="secondary"
              onClick={() => {
                void feedback
                  .confirm({ title: '确认操作', message: '确认后将产生的影响说明。' })
                  .then((accepted) => accepted && feedback.notify('已确认', 'success'))
              }}
            >
              普通确认
            </Button>
            <Button
              variant="danger"
              onClick={() => {
                void feedback
                  .confirm({
                    title: '破坏性确认',
                    message: '此操作不可恢复的说明文本。',
                    confirmText: '删除',
                    danger: true,
                  })
                  .then((accepted) => accepted && feedback.notify('已删除', 'error'))
              }}
            >
              破坏性确认
            </Button>
          </DemoGroup>
        </Panel>

        <Panel
          layout="card"
          title="Brand"
          description="shared/brand · 左为回答生成中的占位图形，右为品牌标识的流体色块；reduced-motion 下静止"
        >
          <div className="flex flex-wrap items-center gap-lg">
            <RoseThree className="size-(--layout-generating-rose-inline-size) text-brand" />
            <BrandMetaballs className="size-(--layout-brand-mark-inline-size) rounded-full" />
          </div>
        </Panel>
      </div>

      <Dialog open={dialogOpen} onOpenChange={setDialogOpen} title="Dialog">
        <div className="grid gap-md">
          <h2 className="type-title">标准浮层</h2>
          <p className="type-body">遮罩、圆角与关闭按钮由浮层拥有，内容区自己拥有排布。</p>
          <div className="flex justify-end gap-sm">
            <Button onClick={() => setDialogOpen(false)}>知道了</Button>
          </div>
        </div>
      </Dialog>

      <Dialog
        layout="workspace"
        open={workspaceDialogOpen}
        onOpenChange={setWorkspaceDialogOpen}
        title="工作台 Dialog"
      >
        <Panel
          className="size-full min-h-0"
          title="工作台浮层"
          description="full-bleed 壳层只提供遮罩、圆角与尺寸，标题行与内边距由内容自己拥有"
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
                feedback.notify('已提交', 'success')
              }}
            >
              保存
            </Button>
          }
        >
          <p className="type-body">
            标题行拥有标题、辅助说明与关闭入口，内容区自行拥有内边距与滚动，底部动作条排在内容之后。设置弹窗即此形态。
          </p>
        </Panel>
      </Dialog>
    </section>
  )
}
