# Prelude UI 样式全量审查与规范收敛计划

## 1. 背景

本计划用于 Prelude 项目的前端 UI 样式全量审查与规范收敛。当前执行基线为 GitHub `zyyyyynnn/Prelude` 的 `main` 分支，原 `codex/fix-ui-polish` 分支已合并。

本轮工作采用分段流程：网页端 GPT 只负责代码审查、组件拆解、需求确认与计划编写；本地 agent 只在完整确认后的 `plan.md` 和 `prelude-ui-requirement-alignment-matrix.md` 基础上执行修改、验证与收尾。

## 2. 目标

1. 先根据本计划和 `prelude-ui-requirement-alignment-matrix.md` 修订 `DESIGN.md`，形成统一约束与规范；修订后的 `DESIGN.md` 是后续样式修改的最高约束。
2. 记录所有页面、业务组件、通用结构、表单、浮层、按钮和 UI primitives 的细粒度拆解。
3. 固化已完成的逐组件确认结论，本地执行阶段不得二次询问已确认项。
4. 修复 UI 漂移时同步修复必要数据源，不做只遮罩、只隐藏、只过滤的前端假修复。
5. 删除本轮已确认替换的旧 UI 逻辑、旧样式、旧组件 fallback 与兼容分支，不保留两套样式体系并存。必要的错误处理、空状态、接口防御不属于此处的删除对象。
6. 产出本地 agent 可直接执行的阶段化修改与验收标准。

## 3. 非目标

1. 不在网页端直接修改业务代码。
2. 不在网页端假设本地命令可运行。
3. 不在网页端提交 commit。
4. 不做无关业务重构。
5. 不引入新设计风格或新 UI 框架。
6. 不把已确认必须执行的内容降级为可选优化。
7. 不借 UI 收敛名义强加没有数据来源、评分语义或产品闭环的新功能。

## 3.1 新功能引入取舍门槛

本轮虽然包含若干新增能力，但判断标准不是“能做就做”，而是必须同时满足以下条件：

1. 已在需求对齐矩阵中确认，或是完成已确认项的必要技术前置。
2. 能服务 UI 样式收敛、交互一致性、数据真实性或正式发布质量。
3. 有明确的数据来源、存储方式、接口 contract 和验证路径。
4. 不引入无关重型依赖，不扩大到新的产品方向。
5. 无法满足上述条件时，本地 agent 必须暂停报告，不得用前端硬编码、假数据、隐藏字段或临时 fallback 糊过去。

三维雷达图改五维已在审查阶段取消，原因如下：

- 当前面试评分链路只真实产出技术、表达、逻辑三维（`ScoreHistory` 仅 technical/expression/logic）。
- 第四、第五维无真实评分语义、后端字段/DTO、seed 或计算逻辑支撑。
- 在无数据来源前提下扩展维度，等于"借 UI 收敛名义强加没有数据来源、评分语义或产品闭环的新功能"，违反本节取舍门槛。
- 因此本轮雷达图冻结为三维，只做样式 token 化收敛；维度扩展需另行立项的后端评分需求，先定义评分语义与打分逻辑，不在本 UI 收敛轮范围内。

同一取舍门槛适用于其他新增项：

- 头像：允许新增，因为它是已确认账号资料能力；但必须本地文件系统存储 + 静态路径映射，不引入云存储。
- 主题切换：允许新增，因为它是全站 token/暗色主题收敛的必要入口；但不得变成额外个性化主题系统。
- Rose Three：允许新增，因为它替代沙漏和等待反馈；但必须原生 SVG + RAF，不引入 Three.js、Lottie。
- Composer 模型下拉：允许新增，因为它替代现有模型入口行为；但只切当前 provider 的模型，不扩展 provider 管理。
- 设置 tab：当前只保持账号资料和 LLM 配置；除主题卡片作为设置内容外，不新增独立 tab。

## 4. 已确认需求决策

### 4.1 上位需求确认记录

| 原始确认项 | 决策含义 | 本地执行要求 |
|---|---|---|
| 第 1 项 | 已确认进入本轮执行范围 | 按本计划和 `prelude-ui-requirement-alignment-matrix.md` 执行，不重新开题 |
| 第 2 项 | 按上线标准处理 | 不做临时方案；必须通过构建、静态扫描和 smoke 验证 |
| 第 3 项 | 已确认范围全部执行 | 不拆成可选项，不放入后续优化 |
| 第 4 项 | 确认执行 | 按阶段计划落地 |
| 第 5 项 | 数据源也要修 | UI 异常来自 seed、接口字段、报告、趋势、薄弱点数据时，必须修数据源 |
| 第 6 项 | 确认执行 | 按计划落地，不二次询问 |
| 第 7 项 | 删除旧 UI/旧样式/旧展示逻辑 | 删除本轮确认替换的旧逻辑，不保留旧组件 fallback 或双样式并存 |
| 第 8 项 | 确认补齐 | 缺失的样式、状态、数据和验收项必须补齐 |

说明：上表是上位决策的执行含义。组件级结论以 `prelude-ui-requirement-alignment-matrix.md` 中可追溯的逐项确认记录为准；不得把推断项当作已确认项。

### 4.2 已确认组件决策摘要

- BrandMetaballs 必须走 token 统一体系。
- 登录页、Sidebar、Workspace Header、消息区、Composer、报告页、设置弹窗、简历管理、数据看板整体视觉方向保持，但该补的 token、可访问性、Tooltip、focus、数据修复不能遗漏。
- Tooltip 保持使用，但全局 Tooltip 样式必须统一。
- `/interview` 空状态欢迎语增加多个文案并随机展示。
- Composer 文本输入框高度与字号走 token 体系。
- Composer 模型入口改为 dropdown：可切换当前固定 provider 的可选模型，并提供进入 LLM 配置页的按钮。
- 发送、按住说话、语音/文字切换按钮保持 34px。
- 面试结束后输入区整体置灰且不可操作。
- 报告生成中不保留沙漏字符。
- 加载动画新增 Rose Three 方向，参考 Math Curve Loaders，但必须按 Prelude 主题 token 化，不能照搬黑底或硬编码色。
- 报告 Markdown table 样式必须补齐。
- 所有弹窗遮罩统一为暖色半透明遮罩。
- 设置弹窗保持双栏结构，当前保持“账号资料 / LLM 配置”两个 tab，但结构要允许后续扩展。
- 账号资料中用户名改为可编辑；新增真实头像上传，未设置头像时显示用户名首字母。
- LLM provider 文案采用“OpenAI 兼容协议”；Thinking Depth 增加“默认（Default）”选项。
- 设置弹窗新增主题切换：浅色、暗色、跟随系统；使用左侧小卡片式 SVG 选项，不做普通按钮；主题作用于全站。
- 主题偏好需持久化：登录用户保存到后端，未登录用户保存到 localStorage。
- 数据看板雷达图本轮保持三维（技术能力、表达清晰度、逻辑思维），只做样式 token 化收敛；维度扩展已取消，详见 Q-128。
- 趋势图继续三条线，但只显示最近五次真实评分数据。
- Button 删除大号按钮体系，不保留 `lg/icon-lg` 作为业务可用变体。
- 本轮允许包含用户资料、头像、主题所需的后端改动；不包含数据看板维度扩展（已取消，保持三维）。

## 5. 流程约束

以下约束用于记录本计划的形成过程，不要求本地 agent 重复执行网页端审查流程：

1. 网页端 GPT 只做审查。
2. 审查后拆解所有组件。
3. 所有组件都必须向用户提问确认。
4. 用户完成全部确认后再写 `plan.md`。
5. `plan.md` 完成后，再交给本地 agent 执行修改。
6. 本地 agent 按计划修改、验证、收尾。

硬性边界：

- 网页端阶段不直接改代码。
- 网页端阶段不假设本地命令可运行。
- 网页端阶段不提交 commit。
- 不能只问有问题的组件，所有组件都要问。
- 没完成全部组件确认前，不得进入执行计划。
- 本地 agent 执行时不得二次询问已确认组件，只在计划与代码冲突、发现未覆盖新范围、或验证证明计划不可落地时暂停报告。
- 本地 agent 的第一项修改必须是根据本计划和 `prelude-ui-requirement-alignment-matrix.md` 修订 `DESIGN.md`。
- `DESIGN.md` 修订完成前，不得修改 `frontend/src/styles/index.css`、UI primitives 或业务组件。
- `DESIGN.md` 修订完成后，后续所有修改都必须以 `DESIGN.md` 为唯一样式规范入口；如果执行中发现新规则缺口，先回补 `DESIGN.md`，再继续落地。

## 6. DESIGN.md 约束摘要

### 6.1 高度体系

`--ui-height-base: 34px` 适用于：

- 默认 Button
- Input
- Select
- 表单控件
- send 框右侧主操作按钮：发送、按住说话、语音/文字切换

`--ui-height-compact: 30px` 只适用于：

- send 框左下角元信息控件：简历、岗位、模型、JD
- 这些 compact 控件对应的 dropdown trigger/item

禁止：

- `h-[30px]`
- `h-[32px]`
- `h-[34px]`
- 把 send 右侧主操作按钮改成 30px
- 把左侧元信息控件改成 34px

按钮宽度规则：

- 默认按钮按内容自适应。
- 登录 submit 可以 `w-full`。
- Sidebar 按钮保持 full width。
- Icon button 保持正方形。
- Composer 左侧 compact 控件可以使用 token 推导的固定/最小宽度。
- 删除大号按钮体系，不保留 `lg/icon-lg` 作为业务可用尺寸。

### 6.2 字体体系

UI 控件默认使用 serif：

- button
- input
- select
- dropdown
- combobox
- tooltip
- sidebar
- header
- settings
- label
- helper text

允许 sans 的范围：

- 对话气泡正文
- 面试报告正文
- 其他真正长阅读内容

禁止：

- 下拉菜单误用 sans
- 设置页按钮误用 sans
- send 框控件字体漂移

### 6.3 浮层体系

Dropdown、Select、Combobox、Tooltip 统一使用低浮层视觉：

- `bg-surface`
- token radius
- token padding
- 弱边框或 `border-transparent`
- `shadow-whisper`

禁止：

- 无依据使用 `shadow-md`
- 无依据使用 `border-border`
- 原生 `title=`
- 每个组件单独写一套浮层样式

Dialog、Confirm、Toast 也必须收敛到同一纸感浮层体系。

### 6.4 颜色体系

业务组件禁止新增或保留：

- 非 token `#hex`
- `rgba()`
- `white`
- `black`

允许：

- token
- CSS var
- Tailwind token utility
- `color-mix()`

token 定义文件可以集中定义基础色值，但新增 token 必须说明用途。

### 6.5 动效体系

禁止：

- `transition-all`
- 写死分散的 `xxx ms` duration

允许：

- 明确属性 transition，例如 `color`、`background-color`、`border-color`、`box-shadow`、`opacity`、`transform`
- 通过统一 motion token/config 管理 duration、easing、delay 和常用 motion preset

不允许夸张动效，不允许无依据引入缩放、弹簧、布局补间。

Rose Three 加载动画要求：

- 严格基于提供的 Math Curve 公式重构为 Vue 3 独立组件。
- 利用 `SVG` 和直接 DOM 属性修改（绕过 Vue 响应式）渲染 76 个粒子。
- 颜色、透明度、尺寸必须走 Token 或 CSS var（利用 `currentColor` 继承父级色值）。
- 动效速度抽取 `speedMultiplier` 属性，与全局 motion config 结合。
- 用于报告生成、AI 思考、加载中等需要明确等待反馈的场景，不能替代普通按钮 loading 状态。

## 7. 组件拆解总表

本节是网页端审查阶段的拆解留档，用于帮助本地 agent 理解覆盖范围；不是要求本地 agent 重新逐项提问。

### 7.1 页面级

- 登录页
- 主面试页
- 简历管理页
- 数据看板页
- 设置弹窗
- 面试报告页

### 7.2 通用结构

- 页面容器
- 页面标题
- 区块标题
- section header
- card shell
- card header
- card body
- list item
- empty state
- loading state
- error state

### 7.3 表单组件

- label
- helper text
- error text
- input
- textarea
- password visibility button
- select trigger
- select content
- select item
- combobox trigger
- combobox option

### 7.4 浮层组件

- dropdown trigger
- dropdown content
- dropdown item
- tooltip trigger
- tooltip content
- dialog overlay
- dialog shell
- dialog header
- dialog body
- dialog footer

### 7.5 按钮组件

- primary button
- secondary button
- ghost button
- icon button
- destructive button
- disabled button
- loading button

### 7.6 业务组件

- 登录/注册切换滑块
- 面试/报告切换滑块
- sidebar 会话 item
- workspace header
- send composer 外壳
- send composer textarea
- 简历/岗位/模型/JD 元信息控件
- 发送按钮
- 按住说话按钮
- 语音/文字切换按钮
- 用户消息气泡
- 面试官消息气泡
- 即时评分标签
- 评分 tooltip
- 报告 Markdown
- 数据看板图表
- 薄弱点列表
- 次数 badge

## 8. 逐组件确认记录

本地 agent 必须按网页端确认结论执行，不重新解释需求，不跳过“保持但需补齐”的项目。组件级确认详情见 `prelude-ui-requirement-alignment-matrix.md`。

已在网页端完成提问对齐的组件决策，本地 agent 不得二次询问用户确认，也不得把这些决策重新拆成待确认事项。本地 agent 只在以下情况暂停报告：`plan.md` 与当前代码事实冲突、发现未覆盖的新组件/新范围、执行会破坏已确认规则、或验证结果显示计划不可直接落地。

### 登录页

- 页面背景保持当前纸感背景。
- 登录卡片保留，按正式发布标准处理。
- BrandMetaballs 必须 token 化。
- 品牌 caption 保持弱化全大写。
- 登录/注册切换保持 SegmentedControl。
- label 保持 15px serif。
- input 保持 34px。
- 密码可见按钮保持透明图标按钮，但补 aria/focus。
- submit 保持 34px，登录页允许 full width。

### App Shell 与 Sidebar

- 主应用保持 100vh flex shell。
- Sidebar 保持 260px 展开和约 51px 折叠。
- 折叠态品牌隐藏。
- 折叠按钮不强制 Tooltip，但要有 aria/focus。
- “开始新面试”保持 sidebar 唯一强主操作。
- 会话分组标题保持弱化 uppercase。
- 会话 item 只显示岗位名，长文本 Tooltip。
- active/hover 只用背景和文字色，不加左侧条。
- pin/delete hover actions 保持，补 aria。
- empty 状态保持“暂无”。
- 底部导航继续放 sidebar。

### Workspace Header

- 标题保持 TooltipText。
- 状态 Badge 保持。
- 生成报告按钮保持 secondary，不改 primary。
- 面试/报告切换继续放右侧。
- header actions 保持较宽间距，优先清晰。

### 消息区

- 消息区保持上方滚动、底部输入框固定。
- 空状态保持一句提示。
- 用户气泡靠右、弱强调。
- 面试官气泡靠左、纸面背景。
- 对话内容保持纯文本，不渲染 Markdown。
- 消息正文使用 sans，标签与评分使用 serif。
- 角色标签保留。
- 思考中状态保留，但动效要符合规则。
- 即时评分保留在用户回答下方。
- 评分 hint 保持 Tooltip，但必须修复全局 Tooltip 样式不一致。
- 重连状态保持消息区内小提示，不弹 Toast。

### Composer

- 外层输入面板保留边框和轻阴影。
- 无会话时 composer 居中。
- 无会话欢迎语增加多个文案并随机展示。
- 会话中 composer 固定底部。
- 文本输入框视觉保持，但高度/字号走 token，不用裸 `min-h-[80px]`、`text-[15px]`。
- JD 输入保持覆盖式输入。
- 简历/岗位 trigger 和 dropdown 保持 compact。
- 简历 dropdown 保留上传 PDF。
- 岗位 dropdown 只显示岗位名称。
- 模型入口改为 dropdown：只切换当前 provider 的可选模型，不在 composer 内切 provider，dropdown 底部提供进入“LLM 配置页”的按钮。
- JD 状态按钮保持短文案。
- 开始面试、发送、按住说话保持 base 34px。
- 语音/文字切换按钮只显示图标，补 aria-label。
- 语音波形区保留。
- 语音状态文字保留在波形左侧。
- 面试结束后输入区整体置灰且不可操作。
- 所有可点击控件必须有暖色 focus 边界。

### 报告页

- 报告生成中保留居中状态卡片。
- 不保留沙漏字符。
- 报告正文保留纸面容器。
- 报告宽度保持约 800px。
- Markdown 标题用 serif。
- Markdown 正文用 sans。
- 补齐 Markdown table 样式。
- code/pre 保持浅底色和 mono，但圆角、背景走 token。
- 导出 PDF 按钮只在报告页显示。
- 报告空状态显示安静 EmptyState，不留空白。

### 设置弹窗

- 所有弹窗遮罩统一暖色半透明遮罩。
- 设置弹窗保持左侧导航、右侧内容双栏结构。
- 设置弹窗隐藏默认右上角 X。
- 当前只保留“账号资料 / LLM 配置”两个 tab，但结构要方便后续扩展。
- 退出登录保持左侧底部。
- 保存、测试按钮保持右上角。
- 内容较长时只滚动右侧内容。
- 用户名改为可编辑。
- 新增真实头像上传；后端保存头像 URL/路径，未设置头像时显示用户名首字母。
- 新增主题设置：浅色、暗色、跟随系统，使用左侧小卡片式 SVG 选项。
- 暗色主题作用于全站，不只作用于设置弹窗。
- 主题偏好登录用户保存到后端，未登录用户保存到 localStorage。
- LLM provider 文案统一为“OpenAI 兼容协议”。
- Thinking Depth 增加“默认（Default）”选项。

### 数据看板

- score cards 保持技术、表达、逻辑三个卡片。
- 雷达图保持三维（技术能力、表达清晰度、逻辑思维），只做样式 token 化收敛；维度扩展已取消（Q-128），不在本轮范围。
- 趋势图保持三条线，但显示最近五次。
- 趋势日期必须来自真实会话/评分数据。
- ECharts tooltip 尽量贴近全站 Tooltip 风格。

## 9. 样式漂移矩阵

| 优先级 | 组件 | 问题 | 违反规则 | 建议方向 | 影响文件 | 风险 |
|---|---|---|---|---|---|---|
| P0 | Confirm Dialog | `bg-black/80`、`shadow-lg`、`border-border` | 遮罩和浮层硬规则 | 改为 mask-overlay、token border、token shadow | `AlertDialogContent.vue` | 删除确认视觉明显漂移 |
| P0 | Analytics 图表 | `rgba()`、hex fallback、`hexToRgba()` | 禁止业务硬编码颜色 | 图表颜色和透明度改 token 派生 | `AnalyticsView.vue` | 图表暗色/纸感不一致 |
| P0 | Interview 恢复提示 | `window.confirm` | 禁止原生 confirm | 改统一 ConfirmDialog | `InterviewView.vue` | 原生弹窗破坏体验 |
| P0 | BrandMetaballs | 多处硬编码 hex | 品牌视觉也必须 token | 改为 token 驱动或 CSS var 注入 | `BrandMetaballs.vue` | 品牌色与主题脱节 |
| P1 | Tooltip | 局部 class 导致大小/底色不一致 | 浮层统一规则 | 收敛 TooltipContent 与业务调用 | tooltip + 业务组件 | 浅底色页面可能融合 |
| P1 | Dialog | `shadow-lg`、`border-border` | 浮层统一规则 | 改 token | `DialogContent.vue` | 设置弹窗底层漂移 |
| P1 | Toast | `shadow-lg`、`border-border` | 浮层统一规则 | 改状态化 token 样式 | `Sonner.vue` | 通知视觉漂移 |
| P1 | Textarea | `dark:bg-zinc-950` | 主题不应硬编码 | 改 token | `Textarea.vue` | 主题冲突 |
| P1 | Composer textarea | 裸高度、裸字号 | 禁止散落硬编码 | 提 class/token | `InterviewComposer.vue` | 后续难维护 |
| P1 | ECharts tooltip | 内联 HTML style | token 统一规则 | formatter 与 tooltip 样式收敛 | `AnalyticsView.vue` | tooltip 与全站不一致 |
| P1 | Button size | 保留 `lg/icon-lg` 大号体系 | 已确认删除大号按钮体系 | 删除或移出业务可用范围 | `button` primitive + 调用方 | 业务继续误用大按钮 |
| P1 | 设置主题 | 暗色主题需求未落地 | 已确认全站主题切换 | 建立浅色/暗色/跟随系统和持久化 | settings + theme store/API | 影响全站 token |
| P1 | 账号资料 | 用户名只读、无头像 | 已确认可编辑用户名和真实头像上传 | 补 UI、API、后端存储和首字母 fallback | profile settings + backend | 需要前后端 contract |
| P1 | Analytics 雷达图 | 颜色/tooltip 未 token 化 | 浮层与颜色统一规则 | 三维雷达样式 token 化、tooltip 收敛；维度保持三维 | `AnalyticsView.vue` | 与全站 tooltip 不一致 |
| P1 | Motion | 动效分散，duration 可能散落 | 已确认重建动效体系 | 增加 motion token/config | styles + primitives | 全局体感变化 |
| P2 | Loading | 沙漏字符/普通加载反馈不足 | 已确认引入 Rose Three | 以 token 化 Rose Three 用于等待场景 | loading component + usage | 需要控制视觉强度 |
| P2 | Markdown table | 缺少专门样式 | 报告完整性 | 补 table 样式 | `index.css` | 报告可读性不足 |

## 10. 数据依赖审查

第 5 项已确认：对应相关数据必须补齐与修改，不允许只做前端遮罩式修复。

必须执行：

- 如果 UI 异常来自 demo seed、接口字段、报告内容、分数趋势、薄弱点数据，必须修复数据源。
- 不允许只在前端硬编码日期。
- 不允许只在前端过滤旧数据。
- 不允许只隐藏数据库 id。
- 数据看板趋势日期必须来自真实 `session / score_history` 数据。
- 数据看板趋势图只显示最近五次真实评分数据。
- 雷达图保持三维（技术、表达、逻辑），数据来自 `ScoreHistory` 真实字段，不增加展示维度，不前端硬造。
- demo seed 必须幂等。
- demo session id 不应漂移。
- UI 不展示数据库内部 id，例如 `#77-80`。
- 用户名、头像、主题偏好需要后端字段/API 支持；未登录主题偏好用 localStorage。
- 头像上传存储必须采用：本地文件系统物理存储 + Spring WebMvc 静态资源路径映射。
- 修改 `schema.sql` 后，如果未通过工具自动迁移，必须确保后端应用重启时结构修改能真实应用到开发数据库中。
- 如果修改 `backend`、`schema.sql`、`data.sql` 或 analytics/interview 相关 DTO/service/controller，必须运行后端验证。

## 11. UI Primitives 审查要求

必须逐项审查：

- Button
- Input
- Textarea
- Select
- DropdownMenu
- Combobox
- Tooltip
- Dialog
- Badge
- Card
- Separator
- Toast
- SegmentedControl
- ConfirmDialog

每项都要说明：

- 当前 API
- size 体系
- variant 体系
- token 使用
- 是否被业务组件绕过
- 是否存在重复样式

每个组件至少覆盖：

- default
- hover
- active
- selected
- disabled
- loading
- empty
- error
- focus-visible
- long text
- narrow width

## 12. 修改阶段计划

### 阶段 0：DESIGN.md 约束固化

涉及文件：

- `DESIGN.md`

任务：

- 只根据本计划和 `prelude-ui-requirement-alignment-matrix.md` 修订 `DESIGN.md`。
- 把高度、字体、颜色、浮层、动效、卡片、Tooltip、Toast、Dialog、Confirm、主题、数据展示、Rose Three、按钮尺寸、Markdown、ECharts、后端数据源边界全部写成统一规范。
- 明确 `DESIGN.md` 是后续样式修改的唯一规范入口。
- 明确 token 命名、用途、允许例外和禁止事项。
- 不修改 `frontend/src/styles/index.css`、UI primitives 或业务组件。

风险：

- 如果 `DESIGN.md` 没先固化，后续组件修改会继续依赖散落判断。

验收：

- `DESIGN.md` 已覆盖本计划和矩阵中的所有样式、状态、数据展示、主题与动效约束。
- 后续阶段可以直接引用 `DESIGN.md` 执行，不需要重新解释需求。
- 没有把任何已确认必须执行项写成可选项或后续优化。

### 阶段 1：Token 与全局样式落地

涉及文件：

- `frontend/src/styles/index.css`

任务：

- 严格按已修订的 `DESIGN.md` 补齐高度、字体、颜色、浮层、动效、卡片、Tooltip、Toast、Dialog、Confirm 规则。
- 建立 motion token/config，覆盖 duration、easing、delay、常用 transition preset；禁止分散写死 `xxx ms`。
- 补齐浅色/暗色/跟随系统主题 token。
- 补齐 Rose Three 加载动画需要的 token，包括颜色、透明度、线宽、速度和背景。
- 清理或标记 legacy 样式入口。
- 增加必要语义 token，集中定义用途。

风险：

- 全局 token 影响范围大。

验收：

- token 集中定义。
- 业务组件不需要直接写硬编码色、高度、阴影、圆角。
- `index.css` 与 `DESIGN.md` 一致；若发现缺口，先回补 `DESIGN.md`。

### 阶段 2：UI primitives 收敛

涉及文件：

- `frontend/src/components/ui/button/**`
- `frontend/src/components/ui/input/**`
- `frontend/src/components/ui/textarea/**`
- `frontend/src/components/ui/shared-dropdown.ts`
- `frontend/src/components/ui/select/**`
- `frontend/src/components/ui/dropdown-menu/**`
- `frontend/src/components/ui/combobox/**`
- `frontend/src/components/ui/tooltip/**`
- `frontend/src/components/ui/dialog/**`
- `frontend/src/components/ui/alert-dialog/**`
- `frontend/src/components/ui/badge/**`
- `frontend/src/components/ui/card/**`
- `frontend/src/components/ui/separator/**`
- `frontend/src/components/ui/sonner/**`
- `frontend/src/components/ui/empty-state/**`
- `frontend/src/components/ui/segmented-control/**`

任务：

- Button default/sm/icon 统一 34px，compact/icon-compact 统一 30px。
- 删除大号按钮体系，不保留 `lg/icon-lg` 给业务调用。
- Textarea 移除暗色硬编码背景。
- Select、DropdownMenu、Combobox、Tooltip 统一低浮层纸感。
- Dialog、Confirm、Toast 统一遮罩、阴影、边框、圆角。
- Badge、Card、Separator、EmptyState 接入统一 token。

风险：

- 业务组件可能绕过 primitive 写了局部样式。

验收：

- primitives 不再制造独立视觉体系。
- Confirm、Dialog、Tooltip、Toast、Dropdown、Select、Combobox 低浮层视觉一致。

### 阶段 3：核心页面与业务组件修复

涉及文件：

- `frontend/src/views/LoginView.vue`
- `frontend/src/views/InterviewView.vue`
- `frontend/src/components/BrandMetaballs.vue`
- `frontend/src/components/layout/AppSidebar.vue`
- `frontend/src/components/workspace/WorkspaceHeader.vue`
- `frontend/src/components/workspace/MessageThread.vue`
- `frontend/src/components/workspace/InterviewComposer.vue`

任务：

- BrandMetaballs token 化。
- 登录页密码按钮补 aria/focus。
- `/interview` 空状态欢迎语随机。
- 替换 `window.confirm` 为统一 ConfirmDialog，且必须重构原同步逻辑为 Promise 异步等待。
- MessageThread 保持纯文本消息，正文 sans，标签/评分 serif。
- Composer textarea 去掉裸高度/裸字号。
- Composer 模型入口改为 dropdown。
- 语音/文字切换按钮补 `aria-label`。
- 面试结束后输入区整体置灰且不可操作。
- 引入 token 化 Rose Three 加载动画，替换报告生成中沙漏，并评估是否用于 AI 思考/长等待场景。

风险：

- Composer 是交互密集区，易影响文本/语音模式。

验收：

- `/login`、`/interview` 空状态、进行中会话、文本模式、语音模式均符合确认规则。

### 阶段 4：设置、用户资料、主题、报告、简历、数据看板与数据源

涉及文件：

- `frontend/src/components/settings/GlobalSettingsModal.vue`
- `frontend/src/components/settings/LlmSettingsPanel.vue`
- `frontend/src/components/settings/UserProfilePanel.vue`
- `frontend/src/views/ResumeManagementView.vue`
- `frontend/src/views/AnalyticsView.vue`
- `frontend/src/styles/index.css`
- `frontend/src/api/**`
- 必要时：`backend/src/main/resources/data.sql`
- 必要时：`backend/src/main/resources/schema.sql`
- 必要时：analytics/interview 相关 service/controller/DTO

任务：

- 用户名改为可编辑。
- 新增头像真实上传，后端保存 URL/路径，未设置时显示用户名首字母。
- 设置页新增浅色/暗色/跟随系统主题切换，使用小卡片式 SVG 选项。
- 主题偏好登录用户保存到后端，未登录用户保存到 localStorage。
- LLM 文案统一为“OpenAI 兼容协议”。
- Thinking Depth 增加“默认（Default）”。
- 补齐 Markdown h1/h2/h3/h4、paragraph、ul/ol/li、blockquote、table、code/pre、长报告滚动样式。
- 修复 ECharts 颜色、tooltip、日期格式、雷达图、趋势图 token 化。
- 雷达图保持三维（技术能力、表达清晰度、逻辑思维），只做颜色/tooltip token 化；维度扩展已取消（Q-128），不在本轮范围。
- 趋势图显示最近五次。
- 薄弱点列表同一弱点下的描述必须同层级展示。
- 不允许薄弱点列表出现 summary/detail 伪层级。
- 不允许第一条无圆点、第二条有圆点这种结构漂移。
- 数据异常必须回到数据源修复。

风险：

- 数据源修复可能触及后端和 seed。
- 主题、头像会触及前后端 contract，需要同步 DTO/API；这类改动属"完成已确认功能的必要技术前置"，但不得借机扩展无关后端重构。
- 头像、主题、用户名可编辑涉及 `User` 实体新增字段（avatar/theme）与对应更新接口，是阶段 4 的硬前置：落代码前必须先确定字段名、类型、接口 contract 与存储方式（头像本地文件系统 + WebMvc 静态映射），再改 schema/实体/DTO，避免字段命名反复返工。

验收：

- `/resumes`、`/analytics`、报告页和空/加载/错误状态均通过 smoke。
- 趋势日期来自真实数据。
- 用户资料、头像上传、主题切换、LLM 设置通过 smoke。

### 阶段 5：静态扫描、浏览器 smoke 与收尾

涉及文件：

- 所有本轮修改文件。

任务：

- 执行前端构建。
- 如脚本存在且适用，执行 BYOK 验证。
- 如修改 backend 或 data.sql，执行后端测试。
- 执行静态扫描。
- 执行浏览器 smoke。

风险：

- 样式扫描可能命中 token 定义文件中的合法基础值，需要人工区分。

验收：

- 所有验收命令通过，或明确未验证原因。

## 13. 静态扫描清单

必须扫描：

- `transition-all`
- `title=`
- `shadow-md`
- `border-border`
- `rgba(`
- 非 token `#hex`
- `white`
- `black`
- `h-[30px]`
- `h-[32px]`
- `h-[34px]`
- `text-[xxpx]`
- `duration-[0-9]+`
- 分散写死的 `xxxms`/`xxx ms`
- 非 token radius
- 非 token shadow
- 非 token z-index
- scoped CSS 中重复定义 primitive 样式

PowerShell 示例：

```powershell
rg -n "transition-all|title=|window\.confirm|shadow-md|shadow-lg|border-border|rgba\(|#[0-9a-fA-F]{3,8}|\bwhite\b|\bblack\b|h-\[(30|32|34)px\]|text-\[[0-9.]+px\]" frontend backend DESIGN.md
```

```powershell
rg -n "duration-[0-9]+|[0-9]+m?s|ease-out|ease-linear|zoom-in|zoom-out|bg-black|dark:bg|style=\"[^\"]*(color|background|border|box-shadow)" frontend
```

## 14. 浏览器 Smoke 清单

必须覆盖：

- `/login`
- `/interview` 空状态
- `/interview` 进行中会话
- send 框文本模式
- send 框语音模式
- 简历 dropdown
- 岗位 dropdown
- 模型 dropdown
- JD 状态
- 面试/报告切换
- 设置弹窗 LLM 配置
- 设置弹窗账号资料：用户名编辑、头像上传、首字母 fallback
- 设置弹窗主题切换：浅色、暗色、跟随系统
- `/resumes`
- `/analytics`
- 窄屏宽度

## 15. Markdown 报告验收

必须覆盖：

- h1/h2/h3/h4
- paragraph
- ul 黑圆点
- ol 数字序号
- li 缩进
- blockquote
- table
- code
- 长报告滚动
- serif/sans 是否符合规则

## 16. 数据看板验收

必须覆盖：

- 分数趋势日期来源
- xAxis 日期格式
- tooltip 日期格式
- 雷达图三维：技术能力、表达清晰度、逻辑思维（维度不扩展）
- 趋势图最近五次
- 薄弱点列表
- 薄弱点 item 层级
- 次数 badge
- empty/loading/error

特别要求：

- 薄弱点列表不能出现 summary/detail 伪层级。
- 同一弱点下的描述必须同层级展示。
- 不能出现第一条无圆点、第二条有圆点这种结构漂移。

## 17. 后端/数据验证清单

如果修改 backend、`schema.sql` 或 `data.sql`：

```powershell
cd backend
mvn test
```

还必须检查：

- demo seed 幂等。
- demo session id 不漂移。
- 趋势日期来自真实 `session / score_history`。
- UI 不展示数据库内部 id。
- DTO、service、controller、前端 contract 字段一致。
- 用户资料、头像、主题偏好字段/API 与前端一致。

## 18. 最终验收标准

- 两份文档都体现“先审查、全组件提问、再 plan、再本地执行”。
- 8 条确认项全部写入决策记录。
- 第 2 项按上线标准处理。
- 第 3 项全部执行，不降级为可选。
- 第 5 项明确包含相关数据补齐与修改。
- 第 7 项明确删除旧逻辑。
- 第 8 项明确补齐。
- 高度、字体、浮层、颜色、动效、Tooltip、数据展示规则全部写清楚。
- 遗漏项全部进入审查范围和验收清单。
- 全站无业务硬编码颜色。
- 无 `transition-all`。
- 无原生 `title=`。
- 无 `window.confirm`。
- 无业务散落 `h-[30px] / h-[32px] / h-[34px]`。
- 无裸 `text-[xxpx]`。
- Tooltip、Dropdown、Select、Combobox、Dialog、Confirm、Toast 统一。
- Button 大号按钮体系已删除。
- Motion token/config 已建立，无散落固定 duration。
- Rose Three 加载动画已 token 化并适配 Prelude 主题。
- 用户名可编辑、头像上传、主题切换与持久化可用。
- 数据看板三维雷达图（不扩展维度）和最近五次趋势可用，数据来自真实来源。
- Login、Sidebar、Header、Composer、MessageThread、Report、Settings、Resume、Analytics 全部符合确认决策。
- `npm run build` 通过。
- `npm run verify:byok` 通过或明确不适用。
- 如修改 backend 或 data.sql，`mvn test` 通过。

## 19. 不允许事项

不得出现或保留：

- 可保留旧逻辑
- 临时兼容
- 后续再清理
- 两套样式并存
- 保留旧组件 fallback
- 仅隐藏不删除
- 暂不处理
- 只做前端遮罩式修复
- 只硬编码日期
- 只过滤旧数据
- 只隐藏数据库 id
