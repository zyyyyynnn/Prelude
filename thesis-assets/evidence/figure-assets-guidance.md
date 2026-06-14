# 图表资产准入说明

## 1. 文件定位

* 本文件用于约束论文图表、界面截图和数据可视化资产的准入条件；
* 本文件不是正文；
* 本文件不冻结正文图号；
* 本文件不生成图表；
* 本文件不替代测试证据；
* 图表只能表达已有代码事实、已有测试证据或已登记截图事实。

## 2. 图表资产分层

| 资产类型 | 主要工具 | 适用内容 | 当前状态 | 准入条件 | 禁止事项 |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **Mermaid / .mmd** | Mermaid CLI | 用例图、E-R 图、系统架构图、流程图。 | 主事实图源 | 必须对应当前代码、实体、接口、数据库或系统实际设计。 | 绝对禁止加入源码中不存在的虚构模块、组件或未实现网络流。 |
| **draw.io / 人工排版** | draw.io | 对 Mermaid 导出图或基础框图进行版面排版优化与文本微调。 | 可选人工微调 | 优化过程不得改变图中事实节点、层次结构和依赖关系。 | 不得为了追求视觉美观而添加不存在的架构节点或依赖线。 |
| **nature-figure** | Python Matplotlib / Seaborn | 对已有真实测试数据（如单用户编译/打包时延、真实公网 LLM 报告延迟）的可视化增强。 | 可选增强工具 | 必须具备真实数据源、已在 [figure-table-register.md](file:///E:/Prelude/thesis-assets/evidence/figure-table-register.md) 登记，且通过用户与审查官复核。 | 绝对禁止生成任何未实测的性能图，也不得凭空生成学术式 / 科研式伪造图表。 |
| **界面截图** | 浏览器截图 / Playwright 自动捕获 | 毕业论文与答辩演示中用于展示系统实际页面、功能入口与流程可达性。 | 待刷新与登记 | 必须来自于当前真实运行的系统，必须使用 Demo 模式隔离数据，且必须先行登记。 | 绝对禁止使用历史旧版本截图蒙混过关，也禁止使用大模型/AI 虚构、补造或修补系统界面图。 |
| **测试表格** | Markdown 表格 | 第五章测试报告中用以展示功能测试用例、环境配置验证、Demo 隔离测试结论。 | 优先使用的呈现方式 | 必须真实来自 `test-data` 目录下的运行结果或实际手动验证记录。 | 绝对禁止将“未实测”或“部分实测”项在表格中编写或暗示为“完全通过”。 |

## 3. 当前准入图表清单

| 编号 | 图表名称 | 类型 | 当前资产 | 事实来源 | 准入状态 | 后续动作 |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| **图3.1** | 系统核心用例图 | Mermaid 导出图 | [fig-3.1-core-use-case.png](file:///E:/Prelude/thesis-assets/evidence/diagrams/fig-3.1-core-use-case.png) | [fig-3.1-core-use-case.mmd](file:///E:/Prelude/thesis-assets/evidence/diagrams/fig-3.1-core-use-case.mmd) | 可刷新 | 需在 2.11C 依据当前代码设计进行事实核对。 |
| **图3.2** | 数据库 E-R 图 | Mermaid 导出图 | [fig-3.2-database-er.png](file:///E:/Prelude/thesis-assets/evidence/diagrams/fig-3.2-database-er.png) | [fig-3.2-database-er.mmd](file:///E:/Prelude/thesis-assets/evidence/diagrams/fig-3.2-database-er.mmd) | 可刷新 | 需在 2.11C 依据当前数据库 DDL 和 MyBatis-Plus 实体类进行事实核对。 |
| **图3.3** | 系统整体架构图 | Mermaid 导出图 | [fig-3.3-system-architecture.png](file:///E:/Prelude/thesis-assets/evidence/diagrams/fig-3.3-system-architecture.png) | [fig-3.3-system-architecture.mmd](file:///E:/Prelude/thesis-assets/evidence/diagrams/fig-3.3-system-architecture.mmd) | 待决策 | 需依据项目模块物理结构及配置进行事实核对。RabbitMQ 已接入报告生成异步任务队列；当前图 3.3 暂不加入 RabbitMQ，后续如修改必须同步图源、导出图、登记表和第三章正文。 |
| **表5.1** | 测试环境表 | Markdown 表格 | [env-2026-06.md](file:///E:/Prelude/thesis-assets/evidence/test-data/env-2026-06.md) 第1节 | 本地全栈开发测试环境版本采集 | 可进入正文候选 | 进入正文前结合章节位置确认。 |
| **表5.2** | 功能测试用例表 | Markdown 表格 | [functional-cases-2026-06.md](file:///E:/Prelude/thesis-assets/evidence/test-data/functional-cases-2026-06.md) | 本地 TC-01 ~ TC-09 实际联调测试记录 | 可进入正文候选 | 进入正文前结合章节位置确认，对“待实测”项进行严格限缩描述。 |
| **表5.3** | Demo 验证结果表 | Markdown 表格 | [demo-2026-06.md](file:///E:/Prelude/thesis-assets/evidence/test-data/demo-2026-06.md) | 本地回环数据隔离与重置机制实际测试指标 | 可进入正文候选 | 进入正文前结合章节位置确认。 |
| **表5.4** | 测试边界说明表 | Markdown 表格 | [test-evidence-matrix-2026-06.md](file:///E:/Prelude/thesis-assets/evidence/test-data/test-evidence-matrix-2026-06.md) 第4, 5节 | 当前证据核对中确立的性能与边界受限描述 | 可进入正文候选 | 进入正文前结合章节位置确认。 |
| **UI-01** | 登录页截图候选 | 界面截图 | [login.png](file:///E:/Prelude/docs/images/login.png) | 真实系统运行界面 | 候选 | 需在 2.11D 重新采集并登记。 |
| **UI-02** | 注册页截图候选 | 界面截图 | [register.png](file:///E:/Prelude/docs/images/register.png) | 真实系统运行界面 | 候选 | 需在 2.11D 重新采集并登记。 |
| **UI-03** | 工作台 / 首页截图候选 | 界面截图 | [interview-empty.png](file:///E:/Prelude/docs/images/interview-empty.png) | 真实系统运行界面 | 候选 | 需在 2.11D 重新采集并登记。 |
| **UI-04** | 简历管理页截图候选 | 界面截图 | [resumes.png](file:///E:/Prelude/docs/images/resumes.png) | 真实系统运行界面 | 候选 | 需在 2.11D 重新采集并登记。 |
| **UI-05** | 模拟面试对话页截图候选 | 界面截图 | [interview-chat.png](file:///E:/Prelude/docs/images/interview-chat.png) | 真实系统运行界面 | 候选 | 需在 2.11D 重新采集并登记。 |
| **UI-06** | 面试报告页截图候选 | 界面截图 | [interview-report.png](file:///E:/Prelude/docs/images/interview-report.png) | 真实系统运行界面 | 候选 | 需在 2.11D 重新采集并登记。 |
| **UI-07** | 数据分析页截图候选 | 界面截图 | [analytics.png](file:///E:/Prelude/docs/images/analytics.png) | 真实系统运行界面 | 候选 | 需在 2.11D 重新采集并登记。 |

*注：本准入说明不冻结最终图号；各 UI 截图统一采用 UI-xx 独立代号，在进入正文排版前，严禁将其直接重命名为图4.x。*

## 4. 禁止可视化内容

本毕业设计在开发与测试中未获取以下领域的实测压力、并发和对比量化指标，因此**绝对禁止在正文或答辩中进行任何图表化 / 可视化表达**：

| 禁止内容 | 原因 | 后续处理 |
| :--- | :--- | :--- |
| **高并发压测结果图** | 系统并未完成大批量并发流量的压力测试，无对应压测数据日志。 | 正文只进行功能及代码层单用户联调性能的阐述，不展现任何并发性能图表。 |
| **限流触发率曲线** | Redis 滑动窗口限流虽有代码实现，但在本地调试中并未进行高流量涌入限流触发的监控统计。 | 仅在第四章描述高可用拦截拦截算法的伪代码或流程，不画限流效果曲线。 |
| **熔断切换效果曲线** | Resilience4j 熔断保护在本地未模拟大面积网络延迟雪崩触发，无真实熔断时延曲线数据。 | 仅在第四章描述熔断器参数与降级机制，第五章不出现熔断切换统计图。 |
| **SSE 长连接并发稳定性图** | 无真实的高并发长连接断线、挂挂和重连丢包率监测日志。 | 仅从前端 `requestAnimationFrame` 渲染节流和客户端指数退避机制描述稳定性设计，不画稳定性图。 |
| **ASR 语音识别准确率图** | 语音录音采集与 WebSocket 端点检测属于待实测内容，无真实公网 ASR 的大样本识别率统计。 | 降调处理，在论文中用文字描述方案设计，不展现识别准确率图表。 |
| **AI 评分公平无偏图** | 系统未开展任何关于评分偏差、盲测对照和多行业样本覆盖的算法公平性量化实验。 | 在第五章降调说明 AI 评估属于结构化反馈，不证明 AI 评分绝对无偏。 |
| **招聘效果优于人工图** | 系统属于辅助训练工具，未投入真实的招聘转化率比对或企业 HR 录用成功率统计。 | 严禁宣称该系统提高了企业招聘效率，不提供任何招聘转化图表。 |
| **生产环境性能图** | 本项目所有有效数据均在本地回环（Demo 隔离）及单人单账号真实 API 功能链路下产生，并非真实生产部署环境。 | 明确指出测试在本地 Windows/Windows Server 单机环境完成，禁止包装为分布式云端生产性能。 |

## 5. nature-figure 使用边界

* nature-figure 不是强制流程，仅作为可选的数据可视化规范化与科研式排版增强工具；
* nature-figure 不得替代任何代码事实判断，架构拓扑仍以源码和原生 Mermaid 文件为最高准则；
* nature-figure 不得替代实际测试报告，不能通过精美的图表掩盖测试数据的缺失；
* nature-figure 不得生成任何商务推广、系统营销风格的宣传图；
* nature-figure 不得生成未实测的系统并发性、压力稳定性图；
* nature-figure 只能将已存在的、经过复核的真实测试数据转化为更符合出版规范的图表；
* nature-figure 输出的所有图表必须先进入 [figure-table-register.md](file:///E:/Prelude/thesis-assets/evidence/figure-table-register.md) 进行编号、类型、数据源及对应章节的规范化登记；
* nature-figure 输出必须经过用户与审查官的共同人工复核后，才可被引入论文正文。

## 6. 图表进入论文的准入条件

所有图表在被引入 chapters/*.md 之前，必须同时满足以下条件：
1. **真实可信**：具备底层的真实数据源（如 test-data 中的日志或 json）或真实代码设计事实；
2. **章节对齐**：具备明确的章节引用位置规划，不引入与正文叙述无关的孤立图表；
3. **注册登记**：已经在 [figure-table-register.md](file:///E:/Prelude/thesis-assets/evidence/figure-table-register.md) 中完成登记，图题与文件路径无误；
4. **风险说明**：在注册表和正文上下文中标注了合理的局限性，如“本机隔离环境”、“单用户公网测试时延”；
5. **双重复核**：经用户和审查官最终人工复核通过，状态变更为“已复核”；
6. **零夸大**：不包含任何未实测的性能、压力或多模态对比指标；
7. **机制宣称**：对于代码实现但未测试的保护机制，只允许进行工程逻辑或流程图式的原理解析，不包装为通过测试；
8. **图号松耦合**：在正文排版完成之前，不冻结图表的最终物理顺序图号，以防增删章节导致图号断裂。

## 7. 后续处理边界

* 核心图核对：对三张核心用例与架构图进行事实刷新与刷新后核对（[fig-3.1-core-use-case.mmd](file:///E:/Prelude/thesis-assets/evidence/diagrams/fig-3.1-core-use-case.mmd)、[fig-3.2-database-er.mmd](file:///E:/Prelude/thesis-assets/evidence/diagrams/fig-3.2-database-er.mmd)、[fig-3.3-system-architecture.mmd](file:///E:/Prelude/thesis-assets/evidence/diagrams/fig-3.3-system-architecture.mmd)）；
* 界面截图采集：启动系统本地运行并重新采集前述 7 张真实界面截图，存入指定证据区；
* 登记表同步：补齐这 7 张截图在 [figure-table-register.md](file:///E:/Prelude/thesis-assets/evidence/figure-table-register.md) 中的登记，核对图题；
* 正文引用确认：根据 chapters/*.md 的单章学术降噪改写进度，按章节叙述的实际承载位置，决定图表最终的物理图号和正文引用。
