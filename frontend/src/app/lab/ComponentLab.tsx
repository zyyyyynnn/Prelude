import { useState } from 'react'
import {
  BarChart3,
  Bell,
  BriefcaseBusiness,
  Eye,
  EyeOff,
  FileText,
  Keyboard,
  LogOut,
  Mic,
  Palette,
  PanelLeft,
  Pencil,
  Plus,
  ScanSearch,
  Settings,
  SquareTerminal,
  Terminal,
  Upload,
  UserRound,
  X,
} from 'lucide-react'
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
import { sampleReport, sampleResumes } from './samples'
import { Button } from '@/shared/ui/button'
import { Field, Input, Textarea } from '@/shared/ui/field'
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
import { Panel } from '@/shared/ui/panel'
import {
  ContextAttachment,
  PromptBar,
  PromptBarFact,
  VoiceIndicator,
  type VoiceStatus,
} from '@/shared/ui/prompt-bar'
import { SessionGroup } from '@/shared/ui/session-row'
import { SidebarAction, SidebarFrame } from '@/shared/ui/sidebar'
import { SegmentedControl } from '@/shared/ui/segmented-control'
import { Select } from '@/shared/ui/select'
import { cn } from '@/shared/lib/cn'
import type { ReactNode } from 'react'

const themeChoices = [
  { value: 'light', label: '浅色', description: '暖色纸面' },
  { value: 'dark', label: '暗色', description: '低亮度阅读' },
  { value: 'system', label: '跟随系统', description: '自动同步' },
]

const settingsTabs = [
  { key: 'profile', label: '账号资料', icon: <UserRound aria-hidden="true" /> },
  { key: 'resumes', label: '简历管理', icon: <FileText aria-hidden="true" /> },
  { key: 'positions', label: '岗位管理', icon: <BriefcaseBusiness aria-hidden="true" /> },
  { key: 'llm', label: '模型管理', icon: <SquareTerminal aria-hidden="true" /> },
  { key: 'theme', label: '主题', icon: <Palette aria-hidden="true" /> },
]

function DemoGroup({ label, children }: { label: string; children: ReactNode }) {
  return (
    <div className="grid gap-sm">
      <h3 className="type-label">{label}</h3>
      <div className="flex flex-wrap items-center gap-sm">{children}</div>
    </div>
  )
}

/** One rail, rendered on the page colour exactly the way the shell renders it. */
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
        brand={
          <>
            <BrandMetaballs className="size-(--ui-height-control) flex-shrink-0 rounded-full" />
            <span className="font-serif text-md font-medium text-text-primary" data-sidebar-label>
              Prelude
            </span>
          </>
        }
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
        <nav className="flex flex-col gap-sm pt-sm" aria-label="实验台导航">
          <SidebarAction
            collapsed={collapsed}
            label="工作区"
            icon={<PanelLeft />}
            to="/interview"
          />
          <SidebarAction
            collapsed={collapsed}
            label="数据看板"
            icon={<BarChart3 />}
            to="/analytics"
          />
        </nav>
      </SidebarFrame>
    </div>
  )
}

export function ComponentLab() {
  const feedback = useFeedback()
  const [model, setModel] = useState('first')
  const [view, setView] = useState('session')
  const [sort, setSort] = useState('recent')
  const [jdMatch, setJdMatch] = useState(true)
  const [showPassword, setShowPassword] = useState(false)
  const [pressed, setPressed] = useState(false)
  const [tab, setTab] = useState('账号资料')
  const [themeChoice, setThemeChoice] = useState('light')
  const [answer, setAnswer] = useState('')
  const [voiceStatus, setVoiceStatus] = useState<VoiceStatus>('listening')
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
          description="shared/ui/panel · 标题行拥有操作区；本页每个面板都是 layout=card，fill 形态如下"
        >
          <div className="grid h-(--layout-demo-frame-block-size) w-full overflow-hidden rounded-lg border border-border">
            <Panel title="模型管理" actions={<Button>保存设置</Button>}>
              <div className="form-grid gap-md">
                <Field label="接入方式" htmlFor="lab-provider">
                  <Select
                    id="lab-provider"
                    value={model}
                    options={[
                      { value: 'first', label: '选项一' },
                      { value: 'second', label: '选项二' },
                    ]}
                    onValueChange={setModel}
                  />
                </Field>
                <Field label="模型" htmlFor="lab-model">
                  <Input id="lab-model" defaultValue="模型名称" />
                </Field>
              </div>
              <p className="type-body">内容超出可用高度时只有内容区滚动，标题行保持不动。</p>
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
            <Button>默认</Button>
            <Button size="icon" aria-label="通知">
              <Bell />
            </Button>
          </DemoGroup>
          <DemoGroup label="Shape">
            <Button shape="action">发送</Button>
            <Button shape="hold">按住说话</Button>
          </DemoGroup>
        </Panel>

        <Panel
          layout="card"
          title="Field"
          description="shared/ui/field · Field 组合标签、控件与说明"
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
            <Field label="密码" htmlFor="lab-password" hint="field-actions-* 预留尾部图标位">
              <div className="field-actions-1">
                <Input
                  id="lab-password"
                  type={showPassword ? 'text' : 'password'}
                  defaultValue="示例值"
                />
                <div className="absolute inset-y-0 inset-e-(--ui-control-inset) flex items-center">
                  <IconTooltip label={showPassword ? '隐藏密码' : '显示密码'}>
                    <button
                      type="button"
                      className="field-action ui-action ui-action-icon"
                      aria-label={showPassword ? '隐藏密码' : '显示密码'}
                      onClick={() => setShowPassword((value) => !value)}
                    >
                      {showPassword ? <Eye /> : <EyeOff />}
                    </button>
                  </IconTooltip>
                </div>
              </div>
            </Field>
            <Field label="只读字段" htmlFor="lab-disabled">
              <Input id="lab-disabled" disabled placeholder="不可编辑" />
            </Field>
          </div>
          <div className="grid gap-md border-t border-line-decor pt-md">
            <h3 className="type-subtitle">小节</h3>
            <p className="type-meta">
              面板内再分层时，用一条细线加 16px 内边距把小节与上方内容分开：细线上下各留 16px，
              标题与其控件仍按 8px 绑定。设置弹窗的「修改密码」「高级设置」即此形态。
            </p>
            <div className="form-grid gap-md">
              <Field label="思考深度" htmlFor="lab-reasoning">
                <Select
                  id="lab-reasoning"
                  value={sort}
                  options={[
                    { value: 'recent', label: '自动' },
                    { value: 'created', label: '高' },
                  ]}
                  onValueChange={setSort}
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
              { value: 'insight', label: '看板' },
            ]}
            value={view}
            onValueChange={setView}
          />
        </Panel>

        <Panel
          layout="card"
          title="Prompt Bar"
          description="shared/ui/prompt-bar · 输入面、上下文附件、事实位与语音指示"
        >
          <DemoGroup label="准备态">
            <div className="w-full">
              <PromptBar
                value={answer}
                inputLabel="职位描述（可选）"
                placeholder="输入或粘贴职位描述以开启 JD 匹配（可选）"
                onValueChange={setAnswer}
                onSubmit={(event) => event.preventDefault()}
                attachments={
                  <>
                    <ContextAttachment kind="resume" label="示例简历.pdf" onRemove={noop} />
                    <ContextAttachment kind="position" label="示例岗位" onRemove={noop} />
                    <ContextAttachment kind="document" label="示例文档.pdf" onRemove={noop} />
                  </>
                }
                leftActions={
                  <>
                    <IconTooltip label="添加面试上下文">
                      <Button type="button" size="icon" variant="ghost" aria-label="添加面试上下文">
                        <Plus />
                      </Button>
                    </IconTooltip>
                    <PromptBarFact label="当前模型 · 默认" icon={<Terminal aria-hidden="true" />} />
                    <button
                      type="button"
                      className="prompt-bar-control prompt-bar-control-jd ui-action"
                      aria-pressed="true"
                      onClick={noop}
                    >
                      <ScanSearch aria-hidden="true" />
                      <span>JD 匹配</span>
                    </button>
                  </>
                }
                rightActions={
                  <Button type="submit" shape="action" disabled>
                    开始面试
                  </Button>
                }
              />
            </div>
          </DemoGroup>
          <DemoGroup label="回答态">
            <div className="w-full">
              <PromptBar
                value={answer}
                inputLabel="面试回答"
                placeholder="输入回答..."
                inputDisabled={voiceStatus !== 'idle'}
                onValueChange={setAnswer}
                onSubmit={(event) => event.preventDefault()}
                attachments={<ContextAttachment kind="resume" label="示例简历.pdf" />}
                leftActions={
                  <>
                    <IconTooltip label="面试开始后上下文已锁定">
                      <span className="inline-flex" tabIndex={0}>
                        <Button
                          type="button"
                          size="icon"
                          variant="ghost"
                          aria-label="面试上下文已锁定"
                          disabled
                        >
                          <Plus aria-hidden="true" />
                        </Button>
                      </span>
                    </IconTooltip>
                    <PromptBarFact label="当前模型 · 默认" icon={<Terminal aria-hidden="true" />} />
                    <PromptBarFact label="JD 匹配" icon={<ScanSearch aria-hidden="true" />} />
                  </>
                }
                rightActions={
                  <>
                    <IconTooltip label="切换到语音输入">
                      <Button
                        type="button"
                        size="icon"
                        variant="secondary"
                        aria-label="切换到语音输入"
                        onClick={() => setVoiceStatus('listening')}
                      >
                        <Mic />
                      </Button>
                    </IconTooltip>
                    <Button type="submit" shape="action" disabled={!answer.trim()}>
                      发送
                    </Button>
                  </>
                }
              />
            </div>
          </DemoGroup>
          <DemoGroup label="语音态">
            <div className="w-full">
              <PromptBar
                inputLabel="面试回答"
                inputContent={
                  <VoiceIndicator
                    status={voiceStatus}
                    recording={voiceStatus === 'listening'}
                    label={voiceStatus === 'listening' ? '正在聆听' : '语音模式已连接'}
                  />
                }
                onSubmit={(event) => event.preventDefault()}
                leftActions={
                  <SegmentedControl
                    ariaLabel="语音状态"
                    items={[
                      { value: 'listening', label: '聆听' },
                      { value: 'processing', label: '处理' },
                      { value: 'speaking', label: '播报' },
                    ]}
                    value={voiceStatus === 'idle' ? 'listening' : voiceStatus}
                    onValueChange={(value) => setVoiceStatus(value)}
                  />
                }
                rightActions={
                  <>
                    <IconTooltip label="切换到文字输入">
                      <Button
                        type="button"
                        size="icon"
                        variant="secondary"
                        aria-label="切换到文字输入"
                      >
                        <Keyboard />
                      </Button>
                    </IconTooltip>
                    <Button type="button" shape="hold" pressed>
                      松开发送
                    </Button>
                  </>
                }
              />
            </div>
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
          title="Session list"
          description="shared/ui/session-row · 会话行、悬停动作与分组，状态由 state 决定"
        >
          <div className="w-full max-w-(--layout-settings-sidebar-inline-size)">
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
          </div>
        </Panel>

        <Panel
          layout="card"
          title="App rail"
          description="shared/ui/sidebar · SidebarFrame 的展开与折叠两态，折叠时标签与品牌淡出、箭头互换"
        >
          <DemoGroup label="展开">
            <LabRail />
          </DemoGroup>
          <DemoGroup label="折叠">
            <LabRail collapsed />
          </DemoGroup>
        </Panel>

        <Panel
          layout="card"
          title="List & Navigation"
          description="shared/styles · 设置分区导航、列表行与选项卡共用同一档控件高度"
        >
          <DemoGroup label="分区导航">
            <div className="grid w-(--layout-settings-sidebar-inline-size) gap-sm">
              {settingsTabs.map((item) => (
                <button
                  key={item.key}
                  type="button"
                  className={cn(
                    'nav-item ui-action ui-action-nav',
                    tab === item.label && 'is-active',
                  )}
                  aria-current={tab === item.label ? 'page' : undefined}
                  onClick={() => setTab(item.label)}
                >
                  {item.icon}
                  {item.label}
                </button>
              ))}
              <button type="button" className="nav-item nav-item-danger ui-action ui-action-danger">
                <LogOut aria-hidden="true" />
                退出登录
              </button>
            </div>
          </DemoGroup>
          <DemoGroup label="简历行">
            <div className="grid w-full gap-sm">
              {sampleResumes.map((resume) => (
                <ResumeRow key={resume.id} resume={resume} onDelete={noop} />
              ))}
            </div>
          </DemoGroup>
          <DemoGroup label="岗位行">
            <div className="position-item-grid w-full">
              <div className="row-label-end">
                <span className="truncate-title">岗位条目一</span>
                <Button size="icon" variant="ghost" aria-label="编辑 岗位条目一">
                  <Pencil />
                </Button>
              </div>
              <div className="row-label-end">
                <span className="truncate-title">岗位条目二</span>
                <Button size="icon" variant="ghost" aria-label="编辑 岗位条目二">
                  <Pencil />
                </Button>
              </div>
            </div>
          </DemoGroup>
          <DemoGroup label="选项卡">
            <div className="grid w-full grid-cols-3 gap-sm">
              {themeChoices.map((option) => (
                <button
                  key={option.value}
                  type="button"
                  role="radio"
                  aria-checked={themeChoice === option.value}
                  className={cn(
                    'option-card ui-action ui-action-selectable',
                    themeChoice === option.value && 'is-active',
                  )}
                  onClick={() => setThemeChoice(option.value)}
                >
                  <span className="grid h-(--ui-height-control) grid-cols-2 gap-xs">
                    <span
                      className={cn(
                        'rounded-sm',
                        option.value === 'dark' ? 'bg-text-secondary' : 'bg-surface-muted',
                      )}
                    />
                    <span
                      className={cn(
                        'rounded-sm',
                        option.value === 'light' ? 'bg-surface-muted' : 'bg-text-secondary',
                      )}
                    />
                  </span>
                  <span className="grid gap-xs">
                    <span className="font-serif text-sm font-semibold">{option.label}</span>
                    <span className="type-meta">{option.description}</span>
                  </span>
                </button>
              ))}
            </div>
          </DemoGroup>
        </Panel>

        {/* The report is a page surface, so it is shown at the width the product
            gives it rather than inside a card: a `card` Panel is already capped at
            that width, and the skin's own padding would steal the last column. */}
        <section className="grid gap-lg" data-slot="lab-report">
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

        <section className="grid gap-lg" data-slot="lab-report-blocks">
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
                <Upload />
                重新加载
              </Button>
            </div>
          </DemoGroup>
        </Panel>

        <Panel
          layout="card"
          title="DropdownMenu"
          description="shared/ui/menu · 分组、子菜单、单选与多选项"
        >
          <DemoGroup label="普通菜单">
            <DropdownMenu trigger={<Button variant="secondary">面试上下文</Button>}>
              <DropdownMenuGroup>
                <DropdownMenuItem onClick={() => feedback.notify('已新建会话', 'success')}>
                  新建会话
                </DropdownMenuItem>
                <DropdownMenuSubmenu trigger="排序方式">
                  <DropdownMenuRadioGroup value={sort} onValueChange={setSort}>
                    <DropdownMenuRadioItem value="recent">最近活跃</DropdownMenuRadioItem>
                    <DropdownMenuRadioItem value="created">创建时间</DropdownMenuRadioItem>
                  </DropdownMenuRadioGroup>
                </DropdownMenuSubmenu>
                <DropdownMenuSeparator />
                <DropdownMenuCheckboxItem checked={jdMatch} onCheckedChange={setJdMatch}>
                  JD 匹配
                </DropdownMenuCheckboxItem>
                <DropdownMenuItem disabled>已归档</DropdownMenuItem>
              </DropdownMenuGroup>
            </DropdownMenu>
          </DemoGroup>
          <DemoGroup label="结构化布局">
            <DropdownMenu
              layout="structured"
              align="end"
              trigger={<Button variant="secondary">模型选择</Button>}
            >
              <DropdownMenuGroup>
                <DropdownMenuItem layout="leading-icon" onClick={() => undefined}>
                  管理模型
                </DropdownMenuItem>
                <DropdownMenuSeparator />
                <DropdownMenuItem disabled>思考深度：自动</DropdownMenuItem>
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
            <IconTooltip label="未读通知">
              <Button size="icon" variant="secondary" aria-label="未读通知">
                <Bell />
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
            <Button
              variant="secondary"
              onClick={() => feedback.notify('已保存到工作台', 'success')}
            >
              成功
            </Button>
            <Button variant="secondary" onClick={() => feedback.notify('正在同步…', 'info')}>
              信息
            </Button>
            <Button variant="secondary" onClick={() => feedback.notify('模型额度偏低', 'warning')}>
              警告
            </Button>
            <Button
              variant="secondary"
              onClick={() => feedback.notify('请求失败，请重试', 'error')}
            >
              错误
            </Button>
          </DemoGroup>
          <DemoGroup label="Confirm">
            <Button
              variant="secondary"
              onClick={() => {
                void feedback
                  .confirm({ title: '切换模型', message: '新会话将使用所选模型。' })
                  .then((accepted) => accepted && feedback.notify('已切换', 'success'))
              }}
            >
              普通确认
            </Button>
            <Button
              variant="danger"
              onClick={() => {
                void feedback
                  .confirm({
                    title: '删除会话',
                    message: '删除后无法恢复面试记录与报告。',
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
          description="shared/brand · 生成态品牌图形，reduced-motion 下静止"
        >
          <div className="flex flex-wrap items-center gap-lg">
            <RoseThree className="size-(--layout-generating-rose-inline-size) text-brand" />
            <BrandMetaballs className="size-(--layout-brand-mark-inline-size) rounded-full" />
            <p className="type-body">左侧为回答生成中的占位图形，右侧为品牌标识的流体色块。</p>
          </div>
        </Panel>
      </div>

      <Dialog open={dialogOpen} onOpenChange={setDialogOpen} title="Dialog">
        <div className="grid gap-md">
          <h2 className="type-title">标准浮层</h2>
          <p className="type-body">Dialog 由 shared/ui/overlay 提供遮罩、圆角与关闭按钮。</p>
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
                feedback.notify('已更新会话', 'success')
              }}
            >
              保存
            </Button>
          }
        >
          <p className="type-body">
            标题行拥有标题、辅助说明与关闭入口，内容区自行拥有内边距与滚动，底部动作条排在内容之后。设置弹窗即此形态，字段与选择器属于内容区自己的组合，不由浮层定义。
          </p>
        </Panel>
      </Dialog>
    </section>
  )
}
