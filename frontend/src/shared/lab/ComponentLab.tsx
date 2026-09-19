import { useState } from 'react'
import { Bell, Eye, EyeOff, Gauge, Plus, X } from 'lucide-react'
import { BrandMetaballs } from '@/shared/brand/BrandMetaballs'
import { RoseThree } from '@/shared/brand/RoseThree'
import { Button } from '@/shared/ui/button'
import { Field, Input, Textarea } from '@/shared/ui/field'
import { useFeedback } from '@/shared/ui/feedback-context'
import { cn } from '@/shared/lib/cn'
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
import { Dialog, IconTooltip } from '@/shared/ui/overlay'
import { Panel } from '@/shared/ui/panel'
import { SegmentedControl } from '@/shared/ui/segmented-control'
import { Select } from '@/shared/ui/select'
import type { ReactNode } from 'react'

const themeChoices = [
  { value: 'light', label: '浅色', description: '暖色纸面' },
  { value: 'dark', label: '暗色', description: '低亮度阅读' },
  { value: 'system', label: '跟随系统', description: '自动同步' },
]

function DemoGroup({ label, children }: { label: string; children: ReactNode }) {
  return (
    <div className="grid gap-sm">
      <h3 className="type-label">{label}</h3>
      <div className="flex flex-wrap items-center gap-sm">{children}</div>
    </div>
  )
}

export function ComponentLab() {
  const feedback = useFeedback()
  const [model, setModel] = useState('deepseek')
  const [view, setView] = useState('session')
  const [sort, setSort] = useState('recent')
  const [jdMatch, setJdMatch] = useState(true)
  const [showPassword, setShowPassword] = useState(false)
  const [pressed, setPressed] = useState(false)
  const [navActive, setNavActive] = useState('会话')
  const [themeChoice, setThemeChoice] = useState('light')
  const [dialogOpen, setDialogOpen] = useState(false)
  const [workspaceDialogOpen, setWorkspaceDialogOpen] = useState(false)

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
          description="shared/ui/panel · 标题行拥有操作区；fill 自带滚动内容区，card 随内容增高"
        >
          <DemoGroup label="card">
            <Panel
              layout="card"
              level={3}
              className="w-full"
              title="岗位库"
              actions={
                <Button size="icon" variant="ghost" aria-label="新建岗位">
                  <Plus />
                </Button>
              }
            >
              <p className="type-body">三级标题读作 type-subtitle，操作区与标题同行竖直居中。</p>
            </Panel>
          </DemoGroup>
          <DemoGroup label="fill">
            <div className="grid h-(--layout-demo-frame-block-size) w-full overflow-hidden rounded-lg border border-border">
              <Panel title="模型管理" actions={<Button>保存设置</Button>}>
                <p className="type-body">内容超出可用高度时只有内容区滚动，标题行保持不动。</p>
                <p className="type-body">设置弹窗的五个分区都是这一形态。</p>
                <p className="type-body">重复段落用于占满高度，验证滚动边界。</p>
                <p className="type-body">重复段落用于占满高度，验证滚动边界。</p>
              </Panel>
            </div>
          </DemoGroup>
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
                  { value: 'deepseek', label: 'DeepSeek' },
                  { value: 'openai-responses', label: 'OpenAI Responses' },
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
                  defaultValue="123456"
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
          title="List & Navigation"
          description="shared/styles · 导航项、列表行与选项卡共用同一档控件高度"
        >
          <DemoGroup label="导航项">
            <div className="grid w-(--layout-settings-sidebar-inline-size) gap-sm">
              {['会话', '看板', '设置'].map((label) => (
                <button
                  key={label}
                  type="button"
                  className={cn(
                    'nav-item ui-action ui-action-nav',
                    navActive === label && 'is-active',
                  )}
                  aria-current={navActive === label ? 'page' : undefined}
                  onClick={() => setNavActive(label)}
                >
                  <Gauge aria-hidden="true" />
                  {label}
                </button>
              ))}
              <button type="button" className="nav-item nav-item-danger ui-action ui-action-danger">
                <X aria-hidden="true" />
                退出登录
              </button>
            </div>
          </DemoGroup>
          <DemoGroup label="列表行">
            <div className="list-row w-full">
              <div className="flex min-w-0 flex-1 flex-col gap-xs">
                <h4 className="truncate-title">Java 后端工程师简历.pdf</h4>
                <p className="type-meta">2026/09/12 14:20 · 3 场面试</p>
              </div>
              <Button size="icon" variant="ghost" aria-label="删除简历">
                <X />
              </Button>
            </div>
            <div className="row-label-end w-full">
              <span className="truncate-title">分布式事务岗位</span>
              <Button size="icon" variant="ghost" aria-label="编辑岗位">
                <Plus />
              </Button>
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
            <>
              <Button variant="secondary" onClick={() => setWorkspaceDialogOpen(false)}>
                取消
              </Button>
              <Button
                onClick={() => {
                  setWorkspaceDialogOpen(false)
                  feedback.notify('已更新会话', 'success')
                }}
              >
                保存
              </Button>
            </>
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
