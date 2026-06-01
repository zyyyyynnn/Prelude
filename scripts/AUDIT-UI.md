执行以下前端 UI 架构终极防御与全局审计任务（仅读取和分析，不要修改任何文件）。
完成标准：作为项目 UI 规范的最高门禁，执行无死角的跨文件审查，确保 Token 体系 100% 覆盖、全局骨架 0 污染、交互细节无降级。

### 审计范围
* **核心基建**：`frontend/src/styles/index.css`
* **全局组件**：`AppSidebar.vue`, `WorkspaceHeader.vue`, `MessageThread.vue`
* **业务视图**：`frontend/src/views/*.vue`

---

###  第一道防线：Token 纯洁度与魔法数字 (Magic Numbers) 审计
* **动作**：全局检索（尤其是 `views` 目录下的 `<style scoped>` ）。
* **评估标准**：
  1. 绝对禁止使用硬编码的 `px` 值来定义 `margin`, `padding`, `gap`, `top/bottom/left/right`（除 `1px` 边框、图表画布等极少数物理约束外）。
  2. 基础组件高度必须使用 `var(--ui-height-base)` 或 `var(--ui-height-sm)`。
  3. 颜色必须 100% 使用 `var(--color-*)`，禁止裸写 `#HEX` 或 `rgba`（包括 JS 中的 ECharts 配置）。
* **输出**：若发现任何违规的魔法数字，精确输出文件路径、行号与数值。若无，输出「Token 纯洁度 100%」。

###  第二道防线：全局骨架防污染与 Scoped 越权审计
* **动作**：交叉对比 `index.css` 与所有 `.vue` 文件的 `<style scoped>`。
* **评估标准**：
  1. `.workspace-page`, `.workspace-header`, `.workspace-page__content`, `.page-grid`, `.panel` 等宏观骨架类，**只能且必须只存在于 `index.css` 中**。
  2. 任何 `.vue` 文件不得使用 `<style scoped>` 偷偷覆写上述全局类的结构属性（尤其是 padding 和 gap）。
* **输出**：列出所有存在“越权覆写全局类”的 Vue 组件。若无，输出「骨架防污染 100%」。

###  第三道防线：绝对对齐与双重边距 (Alignment & Double Padding) 审计
* **动作**：审查页面边界与卡片嵌套逻辑。
* **评估标准**：
  1. **40px 绝对对齐线**：检查 `.workspace-header`、`.workspace-page__content` 以及各种特例视图（如 `InterviewView` 的空白页、报告页、底部输入框），其左右水平内边距是否严格保持为 `var(--spacing-2xl)`。
  2. **免疫双重挤压**：检查嵌套在 `.panel`（已自带 16px padding）内部的子卡片（如 `.detail-card`, `.resume-row`, `.weakness-item`），其自身 padding 是否已合理降级（如降至 8px 或 16px），确保不出现 32px+ 的臃肿嵌套。
* **输出**：指出水平边界未对齐，或存在严重双重内边距挤压的具体类名。若无，输出「空间排版 100% 合规」。

###  第四道防线：微交互与阅读体验 (UX & Typography) 审计
* **动作**：排查细节交互与盒模型动态表现。
* **评估标准**：
  1. **阅读视距锁死**：检查承载大段文本的容器（如 `MessageThread` 中的 `.message-bubble`），是否具备绝对的最大宽度上限（如 `max-width: min(80%, 760px)`），严禁宽屏下文本无限拉伸。
  2. **动画抗抽动**：检查拥有状态切换动画的组件（如侧边栏收缩），其影响位移的属性是否已替换为确定的数值补偿（如固定 width + 拆分 padding/margin），严禁在过渡动画中混用 `auto` 或 `justify-content: center`。
  3. **层级与触底**：检查 `z-index` 堆叠上下文是否安全；确认历史记录类组件在挂载时（`onMounted`）和更新时（`watch`）均使用了 `nextTick` + `requestAnimationFrame` 保证滚动触底。
* **输出**：列出可能导致文本过长、交互抽动或层级遮挡的潜在风险点。若无，输出「微交互体验 100% 达标」。

### 最终裁决
要求：结论先行。汇总上述四大防线的审计结果。如有违规，按优先级列出修复清单；如全部完美通过，请输出“【最高级别验收通过】：前端 UI 架构已彻底锁死，可安全进入下一阶段业务开发。”