# Prelude UI 收敛维护基线

## 1. 背景

本文件记录 Prelude UI 样式收敛完成后的维护基线。执行和审查时以仓库根目录 `DESIGN.md` 为最高规范入口，本文件只保留已落地范围、后续验证清单和禁止事项。

## 2. 当前最终状态

- 设置页包含三个 tab：账号资料、主题、LLM 配置。
- 主题支持浅色、暗色、跟随系统；登录用户偏好保存到后端，未登录用户偏好保存到 localStorage。
- 用户资料支持用户名、邮箱、头像；头像采用本地文件系统存储并通过静态资源路径访问。
- Composer 模型入口只切当前 provider 的模型，不在 composer 内切 provider，并保留进入 LLM 配置的入口。
- 数据看板雷达图为三维：技术能力、表达清晰度、逻辑思维。
- 趋势图显示最近五次真实评分数据，日期来自真实评分/会话数据。
- BrandMetaballs 使用专用 logo token palette，视觉目标是延续旧版暖棕层次。

## 3. DESIGN.md 作为最高规范

- UI 控件、按钮、菜单、表单、设置、Sidebar、Header、Tooltip 默认使用 serif；对话气泡正文和面试报告正文等长阅读内容允许 sans。
- 默认表单控件和 send 右侧主操作使用 `--ui-height-base: 34px`。
- send 左侧元信息控件及对应 compact dropdown 使用 `--ui-height-compact: 30px`。
- Dropdown、Select、Combobox、Tooltip、Dialog、Toast 使用低浮层纸感：`bg-surface`、弱边框或 `border-transparent`、`shadow-whisper`、token radius/padding。
- 业务组件禁止新增硬编码 `#hex`、`rgba()`、`white`、`black`、非 token 高度、非 token 字体、`transition-all`、原生 `title=`。
- 动效使用 motion token，禁止分散写死 duration，禁止动画 layout 属性。
- 修复 UI 异常时，如果根因来自 seed、接口字段、报告内容、评分趋势或薄弱点数据，必须修数据源。

## 4. 已落地范围摘要

- UI primitives：Button/Input/Textarea/Select/Dropdown/Combobox/Tooltip/Dialog/Toast/Badge/Card/SegmentedControl 已按 token 体系收敛。
- Workspace：Sidebar、Header、Composer、MessageThread、StageBar、报告与空状态已收敛到统一尺寸、字体、Tooltip 和浮层体系。
- Settings：账号资料、主题、LLM 配置已分离；头像上传、主题持久化、LLM BYOK 交互已接入。
- Analytics：三维雷达图、最近五次趋势图、薄弱点列表使用真实数据，并完成 token 化样式收敛。
- Loading：Rose Three 采用 SVG + requestAnimationFrame，使用 currentColor 与 motion 参数，不引入重型动画依赖。

## 5. 保留的验证清单

- `cd frontend; npm run build`
- `cd frontend; npm run verify:byok`
- `cd frontend; npm run verify:dark`
- 如修改 backend/schema/data：`cd backend; mvn test`
- 静态扫描：
  - `git diff --check`
  - `rg -n "var\\(--color-text\\)|transition-all|window\\.confirm|title=|shadow-md|border-border|rgba\\(|h-\\[(30|32|34)px\\]|text-\\[[0-9.]+px\\]" frontend DESIGN.md docs`
- 浏览器 smoke：登录页、主界面空状态、进行中会话、文本/语音 send 框、简历/岗位/模型 dropdown、设置三 tab、简历管理、数据看板、暗色主题、窄屏。

## 6. 已知收尾项

- BrandMetaballs 每次 token 调整后都需要人工视觉确认，目标是旧版暖棕层次而不是新视觉。
- 暗色主题每次改动后都需要专项 smoke，重点覆盖输入框/autofill、设置弹窗、Composer、Analytics、Dialog/Toast、Sidebar。
- ECharts 主题 token 变更后需要确认图表重绘、tooltip、坐标轴、雷达图和趋势图在浅色/暗色下都清晰。

## 7. 不允许事项

- 不引入前端硬造的数据看板维度。
- 不把主题重新混入账号资料页。
- 不复活大号按钮体系或两套按钮尺寸体系。
- 不引入云存储、Base64 存库或无关后端重构来处理头像。
- 不用前端隐藏、过滤、硬编码替代真实数据修复。
- 不让旧 UI、旧样式、旧组件 fallback 与新体系并存。
