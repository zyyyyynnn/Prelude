# 阶段 2.11B 图表资产计划修正报告

## 1. 阶段边界

* 本阶段不是阶段 3；
* 未修改正文；
* 未刷新图表；
* 未采集截图；
* 未冻结图号；
* 未冻结引用编号；
* 未生成 DOCX/PDF；
* 只修正 figure-assets-plan.md 的准入口径。

## 2. 修正输入

在重写图表准入计划和核对工作中，调阅并审查了以下文件资产：
1. 治理规范：[workflow-governance.md](file:///E:/Prelude/thesis-assets/meta/workflow-governance.md)
2. 证据锁定状态：[final-evidence-lock.md](file:///E:/Prelude/thesis-assets/meta/final-evidence-lock.md)
3. 2.10阶段审查：[phase-2.10-evidence-readiness.md](file:///E:/Prelude/thesis-assets/evidence/phase-reports/phase-2.10-evidence-readiness.md)
4. 2.11A阶段测试复核：[phase-2.11A-test-evidence-review.md](file:///E:/Prelude/thesis-assets/evidence/phase-reports/phase-2.11A-test-evidence-review.md)
5. 6月测试证据矩阵：[test-evidence-matrix-2026-06.md](file:///E:/Prelude/thesis-assets/evidence/test-data/test-evidence-matrix-2026-06.md)
6. 待重写的旧图表规划：[figure-assets-plan.md](file:///E:/Prelude/thesis-assets/evidence/figure-assets-plan.md)
7. 图表登记状态：[figure-table-register.md](file:///E:/Prelude/thesis-assets/evidence/figure-table-register.md)

## 3. 旧口径问题

| 问题 | 原表述 | 风险 | 修正结果 |
| :--- | :--- | :--- | :--- |
| **nature-figure 定位被夸大** | nature-figure 必须参与的候选场景... | 容易导致开发/写作时强行调用 nature-figure 伪造没有数据支撑的数据图表。 | 已将其降级为“可选增强工具”，仅限规范化真实存在的数据。 |
| **真实数据与缺失数据混淆** | 必须参与的候选场景包含：测试数据图、构建耗时对比图、Demo/历史真实 API 延迟对比图等。 | 未考虑高并发、限流熔断、并发长连接属于“未实测”状态，存在将缺失数据包装可视化的学术不端风险。 | 已将所有涉及“未实测”的指标列入“禁止可视化内容”清单。 |
| **未实测性能指标被可视化** | 规划生成“测试数据图”、“Demo/历史真实 API 延迟对比图”。 | 若以精美图表展示，会在论文中向评审传递“该系统已通过大规模高并发和公网性能验证”的错误暗示。 | 明确在论文第五章仅使用文字或简单测试表格陈述，严禁图形化未实测指标。 |
| **图表分层定位不清晰** | 仅有简单的工具分工，没有各层资产的准入边界。 | 混淆了 Mermaid 本文设计事实图、draw.io 排版美化、nature-figure 数据图和界面截图的关系。 | 重新划分了五个层级（Mermaid、draw.io、nature-figure、截图、测试表格）并逐一制定准入与禁止条件。 |

## 4. 新准入原则

| 原则 | 说明 | 对后续阶段的影响 |
| :--- | :--- | :--- |
| **先数据，后图表** | 没有真实底层的测试数据、代码事实或已登记截图，一律不得进行图表绘制。 | 阶段 2.11C/D/E 在刷新图表前必须先验证对应的数据证据是否在 test-data 中真实存在。 |
| **Mermaid 是唯一事实图源** | 架构图、用例图和 E-R 图的事实源均以 Mermaid 代码为准，不再以 nature-figure 凭空捏造。 | 限制了 nature-figure 在架构图绘制上的越权，确保系统逻辑与源码完全对齐。 |
| **nature-figure 是可选增强工具** | 仅作为数据可视化的可选美化器，不是毕业设计流程中的强制执行项。 | 允许在没有合适绘图数据时直接使用 Markdown 测试表格，不强制使用 Python 绘图。 |
| **UI 截图必须来自真实系统** | 界面截图必须基于当前 June 版本的系统真实运行捕获，且使用 Demo 隔离数据库的数据。 | 阻止在 2.11D 截图时使用 AI 生成的伪图或历史旧截图。 |
| **未实测性能不得可视化** | 并发压测、流量限制、熔断切换、语音 ASR 等无数据的能力绝不得生成任何可视化效果图。 | 严格划定了第五章的写作红线，从图表准入口径上屏蔽了浮夸宣称。 |
| **图号松耦合** | 在进入阶段 3 单章排版定位前，各图表统一使用 UI-xx 或占位编号，不冻结物理图号。 | 避免在阶段 2.11C/D/E 中由于结构调整造成正文图号断档，提升文档维护弹性。 |

## 5. 后续任务清单

| 阶段 | 任务 | 允许修改 | 禁止事项 | 通过条件 |
| :--- | :--- | :--- | :--- | :--- |
| **2.11C** | **核心架构图刷新与核对** | [fig-3.1-core-use-case.mmd](file:///E:/Prelude/thesis-assets/evidence/diagrams/fig-3.1-core-use-case.mmd)、[fig-3.2-database-er.mmd](file:///E:/Prelude/thesis-assets/evidence/diagrams/fig-3.2-database-er.mmd)、[fig-3.3-system-architecture.mmd](file:///E:/Prelude/thesis-assets/evidence/diagrams/fig-3.3-system-architecture.mmd) 及对应的导出 PNG。 | 严禁增加代码中不存在的模块，严禁修改正文 chapters/*.md。 | 架构图、E-R 图与当前最新代码及数据库 DDL 完全一致。 |
| **2.11D** | **界面截图真实采集** | docs/images/ 路径下的界面截图（UI-01 ~ UI-07）。 | 严禁使用 AI 补造、拼接界面，严禁混入旧系统截图。 | 截图数据均展示隔离环境（Demo 模式），反映最新 Vue 路由。 |
| **2.11E** | **图表登记补齐** | [figure-table-register.md](file:///E:/Prelude/thesis-assets/evidence/figure-table-register.md) 注册表。 | 严禁修改 chapters/*.md 引用。 | 将 UI-01 ~ UI-07 及测试数据表格完整登记入注册表，建立章节对应。 |
| **阶段 3** | **正文单章学术降噪** | chapters/*.md 中的单章文件。 | 严禁全文一次性送入大模型，严禁扩大修改范围。 | 根据已锁定图表与测试证据进行单章润色，清除营销色彩，对齐最终图号。 |

## 6. P0/P1/P2 问题清单

### P0 问题（阻止推进的破坏性红线）
* **无。** [figure-assets-plan.md](file:///E:/Prelude/thesis-assets/evidence/figure-assets-plan.md) 中已彻底删除“nature-figure 必须参与”表述，并严格禁止生成任何未实测的性能图，未触发 P0 拦截。

### P1 问题（建议在后续图表刷新与截图前解决）
* **P1-1：UI 截图尚未采集。** UI-01 到 UI-07 的 7 张候选界面截图尚未按照 June 最新路由和界面样式进行物理采集，将在 2.11D 阶段执行。
* **P1-2：UI 截图尚未完成登记。** 前述 7 张截图尚未在 [figure-table-register.md](file:///E:/Prelude/thesis-assets/evidence/figure-table-register.md) 中进行登记，将在 2.11E 阶段完成。
* **P1-3：用例与架构图未重新核对。** 图3.1 至 图3.3 虽有历史遗留 Mermaid 代码，但尚未与 June 最新代码细节（如 Structured Output 机制、虚拟线程及 Resilience4j 参数）进行物理核对，将在 2.11C 执行。

### P2 问题（非阻塞性工具链依赖）
* **P2-1：nature-figure 工具链尚未在 June 真实环境下执行验证。** 鉴于本阶段未运行 nature-figure，Python 环境及依赖包尚未拉起。但由于该工具已降级为“可选增强”，该问题不阻塞后续流程。
* **P2-2：draw.io 排版人工微调链路未锁定。**

## 7. 阶段结论

**PASS**
*(注：figure-assets-plan.md 已完整重写，彻底消除了“必须参与”等不切实际口径，划定了清晰的“禁止可视化清单”和“图表准入红线”。旧口径问题全部出清，允许在用户和审查官复核后，进入阶段 2.11C：架构与用例图事实刷新。)*

## 8. 阶段安全声明

* 阶段 3 仍未开始；
* 正文未修改；
* 图表未刷新；
* 截图未采集；
* 图号未冻结；
* 引用编号未冻结；
* DOCX/PDF 未生成。
