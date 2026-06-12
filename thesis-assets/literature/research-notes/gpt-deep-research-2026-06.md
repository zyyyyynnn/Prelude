# GPT Deep Research：LLM 驱动的模拟面试、简历诊断与教育训练系统研究综述

## 执行摘要

本次检索的核心结论是：**“LLM 驱动的模拟面试”本身还是一个新兴而分散的研究议题，但它已经能够从四类较成熟文献中获得稳固支撑：一是 AI 人才甄选与异步视频面试研究，二是简历—岗位语义匹配与候选人画像研究，三是教育聊天机器人、智能辅导系统与形成性反馈研究，四是 AI 公平性、偏差、可解释性与隐私治理研究。** 按本次检索结果看，正式学术文献里直接把“PDF 简历解析、岗位匹配、阶段化模拟面试、多轮对话、实时语音、评估报告、可视化分析”整合为一体化平台的论文并不多，现有研究更多分散在“AI 招聘/AI 面试”“教育聊天机器人/ITS”“自动反馈/教育评价”“岗位推荐/人岗匹配”等相邻子领域，这意味着 Prelude 的论文不宜把自身表述为“已有成熟范式的简单复现”，而更适合表述为“跨领域能力整合型系统设计与实现”。 citeturn33view0turn29search3turn32search3turn19search2turn32search2turn43search12turn1search0turn1search6turn0search8

对 Prelude 而言，最强的理论支点并不是单一的“面试系统文献”，而是一个组合框架：在“教育训练”层面，可以用教育聊天机器人系统综述、GenAI 教育实证综述、ITS 元分析来论证多轮交互、即时反馈、诊断性评价和个性化训练的合理性；在“求职与甄选”层面，可以用 AI 招聘综述、AVI 研究、机器学习在人员选拔中的效度研究来论证模拟面试、岗位匹配、能力评估和申请者反应研究的现实基础；在“技术实现”层面，可以用 Spring SSE、Resilience4j、PDFBox 以及 OpenAI/Anthropic/DeepSeek 官方文档为流式通信、熔断降级、文本抽取、语音交互和多模型适配提供工程依据。Prelude 仓库公开 README 也明确显示，该项目已经把 PDF 简历解析、岗位模板匹配、阶段化模拟面试、Markdown 评估报告、SSE 指数退避重连、Resilience4j 熔断降级、Voice-to-Voice 语音对话、用户级多 Provider 配置、AES-256-GCM 密钥加密、能力雷达图与 Demo Twin 等能力集成在同一体系中。 citeturn41view0turn41view1turn41view2turn41view3turn43search0turn1search0turn1search1turn1search6turn39search0turn37search3turn40search2turn37search0turn38search0turn37search2

从论文写作价值看，Prelude 的第一章可以通过“AI 招聘变革”“GenAI 赋能教育训练”“人岗匹配数字化”来建立研究背景与现实意义；第二章可以围绕“国内外研究现状”系统梳理 AI 面试、教育聊天机器人、简历匹配、自动评估与公平治理；第四章可以把多轮对话、流式反馈、语音低延迟、多模型路由、熔断降级、安全隔离作为系统设计创新点；第六章则应重点讨论 AI 反馈的可信度、评分偏差、幻觉、可解释性、隐私安全与工程边界。需要特别强调的是：Prelude 的创新点很明显，但风险也同样明显，包括报告幻觉、评估维度漂移、求职者属性偏差、语音识别误差、模型输出不一致、密钥与求职数据泄露风险，以及“训练系统”与“招聘决策系统”边界模糊的问题。因此，论文论证宜把系统定位为**“AI 辅助训练与诊断平台”**，而不是“自动招聘裁决系统”。 citeturn29search3turn36search4turn1search11turn43search12turn38search2turn38search11turn38search21turn37search22turn37search10

## 检索策略与研究现状

**研究问题与检索策略**

| 维度 | 本次设置 |
| -- | -- |
| 研究问题 | 聚焦十个子问题：LLM 在模拟面试、简历诊断、人岗匹配、教育训练、多轮对话、自动评估可信度、语音实时反馈、隐私安全、多模型高可用、相对规则式系统的创新与风险 |
| 先验项目核对 | 先核对 Prelude 公开仓库 README 与 Demo Twin 文档，确认系统实际能力边界，再回到学术检索做需求映射。 citeturn41view0turn41view1turn41view2turn41view5turn42view0 |
| 中文关键词 | “人工智能 面试 甄选”“AI招聘 公平”“简历 岗位 匹配”“生成式人工智能 教育”“智能辅导系统”“教育评价 人工智能”“职业教育 人工智能”“多轮对话 教育”“语音交互 训练系统” |
| 英文关键词 | “AI interview training”“asynchronous video interview”“automated video interview personality assessment”“resume-job matching”“candidate-job matching”“educational chatbots systematic review”“intelligent tutoring systems meta-analysis”“generative AI in education review”“AI bias in hiring” |
| 主要站点/数据库 | GitHub 公共仓库页、CNKI 文章页/期刊门户、高校期刊官方页、Airiti、ACM DL、SpringerLink、Wiley、ERIC、官方技术文档站点 |
| 时间范围 | 研究现状以 2021–2026 为主；教育聊天机器人、ITS、简历匹配等基础文献向前回溯到 2013–2018，用于提供理论基底 |
| 纳入标准 | 与“模拟面试/AI 面试/人才甄选”“简历解析或人岗匹配”“教育训练/智能辅导/自动反馈”“AI 偏差/公平/隐私治理”高度相关；正式出版、元数据可核验；官方技术文档仅作技术依据 |
| 排除标准 | 普通博客、营销软文、维基、问答帖、视频教程、与主题偏离的泛 AI 科普；元数据残缺、作者年份无法核验的来源；C 类来源不进入正式参考文献候选 |
| 语言比例策略 | 目标控制为中文 10–14、英文 10–14、官方技术文档 4–6；第一章、第二章的研究现状以学术文献为主，第四章再引入技术文档 |

**国内研究现状**

中文研究里，**最贴近 Prelude“模拟面试—简历—评估”链条的不是教育技术论文，而是 AI 人才甄选与 AI 招聘公平文献**。贺伟等从管理学视角综述了 AI 人才甄选的研究进展，明确把 AI 面试、应聘者反应、公平感知、组织创新性判断和未来的隐私安全议题纳入同一框架；韩敏、陈晓曦则直接讨论了组织选拔情境中 AI 招聘的公平威胁及其提升策略；陈瑞青的硕士论文进一步把“非同步视讯面试中的 AI 评鉴功能”与求职者科技信任联系起来。就“模拟面试”主题本身而言，中文正式文献目前更成熟的切入口仍是**AI 甄选、AVI 与应聘者感知**，而不是“面向训练者的沉浸式练习平台”。这是 Prelude 在论文中需要如实说明的一个研究空白。 citeturn33view0turn29search3turn32search3

在教育训练与智能辅导方向，中文研究已为 Prelude 提供了更强的理论基础。肖建利等对智慧教育中的大语言模型进行了综述；何梁等讨论了 ChatGPT 在教育中的“教学能力诊断”；刘邦奇等从技术框架、能力特征与应用趋势角度讨论生成式人工智能如何重塑未来教育形态；柯清超等总结了生成式 AI 在基础教育中的机遇、风险与对策；郑永和等则提出 AI 赋能教育评价能够把评价从“结果展示”推进到“诊断—反馈—改进”的连续链条。把这些研究并置来看，Prelude 的“阶段化问答—即时追问—Markdown 评估报告—雷达图复盘”并非孤立设计，而是可以被置于**智能辅导、形成性评价、学习分析可视化**的连续研究脉络中。 citeturn4search1turn28search13turn30search0turn29search0turn34search2

在职业教育、能力训练与个性化支持方面，中文研究的价值主要体现在两点。第一，汪琼、李文超指出 AI 助力因材施教虽有潜力，但会面临实施误区与教育理解不足的问题，这对 Prelude 的“个性化提问与反馈”形成重要方法论约束；第二，沈苑、汪琼系统讨论了 AI 教育应用中的设计偏见、数据偏见与算法偏见治理；王洋、顾建军以及李东海等则把 AI 与职业教育、岗位能力训练、产教融合联系起来，说明“岗位情境化训练”具有现实需求。由此可见，Prelude 若把自己定位为**面向求职能力训练的 AI 辅助系统**，在中文文献中是能找到相当充分的价值论证的；但如果把自己定位为“自动评判求职者优劣”的系统，则会立即落入公平性与治理风险争议。 citeturn31search14turn36search4turn35search8turn31search4

综合本次中文检索，可以作出一个谨慎判断：**国内研究已经提供了“教育训练合理性”“AI 招聘风险意识”“职业能力训练需求”三块扎实地基，但对“LLM 驱动的模拟面试训练平台”的直接实证研究仍然偏少。** 因而 Prelude 论文更适合采用“交叉综述 + 系统实现 + 工程评估”的写法，而不宜假设已有大量同类平台可直接对标。这一判断基于本次检索覆盖到的 AI 甄选综述、教育评价、职业教育和 AI 偏差治理文献所形成的整体图景。 citeturn33view0turn29search3turn34search2turn36search4turn31search4

**国外研究现状**

国外研究的成熟度明显更高，但同样呈现“分散在邻近领域”的特点。在教育与训练方向，Kasneci 等讨论了 LLM 在教育中的机会与挑战；Zhang 等对 48 篇 GenAI 教育实证研究做了系统综述，指出其主要价值表现为学习支持、教学辅助和多样化反馈，同时也伴随准确性、可靠性与伦理问题；Kuhail 等和 Huang 等分别系统梳理了教育聊天机器人与语言学习聊天机器人的效果与局限；Steenbergen-Hu 与 Cooper 的 ITS 元分析则为“智能辅导能够在一定条件下产生学习收益”提供了较强的经典证据。对 Prelude 而言，这组文献共同支撑了一个关键论点：**多轮对话、即时反馈与个性化支架不是噱头，而是已有教育技术研究长期关注的有效机制。** citeturn1search23turn43search12turn1search0turn1search1turn1search18turn1search6

在招聘、面试与人员选拔方向，国外研究更直接贴近 Prelude 的“模拟面试”场景。Hickman 等关于自动视频面试人格评估的研究聚焦效度、信度与可泛化性；另一篇同作者团队论文则探讨了如何通过语言型机器学习算法从视频面试中推断申请者人格；Koenig 等把机器学习用于人员选拔的测量与预测问题；Dunlop 等则从综述角度系统梳理了异步视频面试的设计要素、应聘者体验与实施注意点。把这些文献综合起来可以看到：**AI 面试研究已经从“能不能做”进入“怎样设计才有效、可接受、可信”的阶段**，这正好为 Prelude 的提问策略、评分维度、反馈呈现和训练边界提供了直接参考。 citeturn19search2turn20search13turn18search8turn32search2

在简历解析与人岗匹配方向，正式英文文献目前更多依赖 NLP、深度表示学习和图模型，而不是纯粹的端到端 LLM 方案。Maheshwary 等较早就用深度孪生网络处理半结构化简历与岗位匹配；Frazzetto 等则在 2025 年提出了图神经网络候选人—岗位匹配方法，并显式说明其节点属性来自 LLM 抽取；Frissen 等关于招聘广告中歧视性语言识别的工作，则提醒人们即便是在招聘流程的前端文本阶段，算法也可能放大既有偏见。由此看，Prelude 的“PDF 简历解析 + 岗位模板匹配”有充分的国际研究背景，但如果论文想强调“LLM 的必要性”，就要诚实指出：**当前成熟文献仍以 BERT、Siamese、图模型、ML 排名为主，LLM 更像是在新一代系统中承担语义抽取、特征规整与对话接口层的角色。** citeturn0search8turn0search6turn0search19

因此，国外研究对 Prelude 的启示很明确：其一，多轮对话与反馈诊断可以借力教育聊天机器人和 ITS 传统；其二，模拟面试的题目组织、话轮推进和反馈方式，应参考 AVI 与 AI 选拔研究，而不是简单照搬客服机器人模式；其三，简历诊断和岗位匹配要避免“黑箱打分”，需要把算法结果放回证据与解释链中；其四，公平性、可信度、应聘者心理感受和组织责任，在招聘场景里不是附属问题，而是核心问题。 citeturn1search0turn1search18turn19search2turn32search2turn0search19

## Prelude 功能映射与论文支撑

Prelude 公开仓库显示，该项目已经具备 PDF 简历解析、岗位模板匹配、阶段化模拟面试、Markdown 评估报告、SSE 指数退避重连、Resilience4j 熔断降级、Voice-to-Voice 流式低延迟语音对话、用户级多 Provider 配置、AES-256-GCM API Key 加密、能力雷达图与 Demo Twin 隔离演示等能力。技术层面则明确使用了 Spring `SseEmitter`、Resilience4j、PDFBox、Redis、WebSocket、OpenAI/Anthropic/DeepSeek 等组件。 citeturn41view0turn41view1turn41view2turn41view3turn41view4turn41view5

基于上述能力边界与本次文献检索，Prelude 功能模块与研究支撑的对应关系如下。

| Prelude 功能 | 可支撑研究方向 | 推荐文献 | 可支撑论文章节 | 注意事项 |
| ---------- | ------- | ---- | ------- | ---- |
| PDF 简历解析 | 半结构化简历文本抽取、教育/经历字段识别、候选人属性结构化 | G-EN-11、G-EN-12、G-TECH-03 | 第二章、第四章 | PDFBox 本质上是文本抽取工具，对扫描件简历通常仍需额外 OCR，这是基于 `PDFTextStripper` 机制的工程推论 |
| 岗位模板匹配 | 人岗匹配、语义排序、岗位能力画像 | G-CN-01、G-EN-11、G-EN-12 | 第二章、第四章、六章 | 匹配分数应定义为训练辅助信号，避免写成“自动录用判断” |
| 模拟面试 | AI 招聘、异步视频面试、应聘者反应、公平性 | G-CN-01、G-CN-02、G-CN-03、G-EN-06、G-EN-09 | 第一章、第二章、第四章、六章 | 训练平台与正式招聘选拔要明确边界 |
| 多轮对话 | 教育聊天机器人、智能辅导、追问与支架式训练 | G-EN-02、G-EN-03、G-EN-04、G-EN-05、G-CN-09 | 第二章、第四章、六章 | 多轮对话优势在于上下文连续，但也会带来话轮漂移、过度提示与状态管理复杂度 |
| 流式反馈 | 形成性评价、增量输出、实时响应体验 | G-CN-08、G-EN-02、G-TECH-01、G-TECH-04、G-TECH-05、G-TECH-06 | 第四章、六章 | SSE/流式输出要处理断流、重试、半结构化增量片段与前端一致性 |
| 语音交互 | 低延迟语音训练、沉浸式互动、口语表达训练 | G-CN-03、G-TECH-04、G-TECH-06 | 第四章、六章 | 语音识别错误、口音、网络抖动都会影响评分与体验 |
| 评估报告 | 诊断性评价、自动反馈、形成性改进建议 | G-CN-08、G-EN-02、G-EN-05 | 第二章、第四章、六章 | 报告必须标注为 AI 辅助结论，建议绑定评分依据与证据片段 |
| 能力雷达图 | 学习分析、能力维度建模、弱项发现 | G-CN-08、G-CN-09、G-CN-10、G-EN-05 | 第四章、六章 | 维度定义要稳定，避免“伪精确”可视化 |
| Demo Twin | 沙箱化演示、环境隔离、可重复展示 | G-TECH-02、G-TECH-01，加仓库文档支撑 | 第四章 | 学术支撑相对较弱，更偏工程可复现性与安全隔离实践 |
| 安全与隐私 | 求职数据安全、偏差治理、敏感信息保护 | G-CN-02、G-CN-10、G-TECH-04、G-TECH-05、G-TECH-06 | 第一章、第四章、六章 | 简历、语音、评分与 API Key 都属于高敏感资产，应最小化存储与暴露面 |
| 多模型 Provider | 模型路由、接口兼容、成本与可用性优化 | G-TECH-02、G-TECH-04、G-TECH-05、G-TECH-06 | 第四章、六章 | 兼容层不等于语义一致，提示词模板、输出格式和错误处理都要分别适配 |
| 熔断降级 | 高可用、灾备切换、失败隔离 | G-TECH-02、G-TECH-05、G-TECH-06 | 第四章、六章 | 降级后应在系统与论文中说明能力变化与结果可信度下降 |

对 Prelude 论文几个关键章节的直接支撑可以进一步压缩为下表。

| 章节 | 可直接建立的理论支撑 | 建议优先引用 |
| -- | -- | -- |
| 第一章 | AI 招聘与教育训练双重背景下，求职训练从规则式脚本演进为对话式、诊断式、反馈式系统；同时公平与隐私风险显著上升 | G-CN-01、G-CN-02、G-EN-01、G-EN-09、G-EN-10 |
| 第二章 | 国内外研究现状可分为 AI 甄选、教育聊天机器人/ITS、简历匹配、AI 偏差治理四条线索，再映射到 Prelude 的综合能力 | G-CN-01 至 G-CN-12；G-EN-01 至 G-EN-12 |
| 第四章 | 系统设计创新点可写为：多轮对话训练链路、SSE 流式反馈、低延迟语音、PDF 简历解析、多 Provider 网关、高可用保护、密钥加密与演示隔离 | G-TECH-01 至 G-TECH-06，加 Prelude 仓库文档 |
| 第六章 | 未来工作与局限应围绕评估可信度、幻觉、偏差、解释性、数据安全、扩展到更严格实验设计等展开 | G-CN-02、G-CN-10、G-EN-01、G-EN-02、G-EN-06、G-EN-10 |

## 候选参考文献质量表

以下为**正式参考文献候选**。本表仅保留 A、A-、B+、B- 条目；C 类来源已移至后文单列排除。为避免将官方技术文档误用为“研究现状核心证据”，技术文档统一放在表尾，并建议只用于第二章技术背景、第三章/第四章实现依据。表内共 **30 条**，其中中文 **12 条**、英文 **12 条**、官方技术文档 **6 条**。

| 编号 | 文献题名 | 语言 | 类型 | 年份 | 作者 | DOI / arXiv / 官方链接 | 质量等级 | 推荐用途 | 是否建议进入正式参考文献 |
| -- | ---- | -- | -- | -- | -- | ------------------ | ---- | ---- | ------------ |
| G-CN-01 | 基于人工智能的人才甄选：研究进展与未来展望 citeturn33view1turn32search12 | 中文 | 期刊论文 | 2024 | 贺伟、李亚莉、汪默 | 未找到 DOI，但提供《管理学季刊》官方发布页/首发 PDF 页 citeturn32search0turn33view1 | A- | 国内 AI 面试/甄选综述主文献 | 是 |
| G-CN-02 | 组织选拔情境中AI招聘的公平威胁及其提升策略 citeturn29search3 | 中文 | 期刊论文 | 2025 | 韩敏、陈晓曦 | DOI: 10.16471/j.cnki.11-2822/c.2025.9.001 citeturn29search3 | A | 公平性、偏差、治理 | 是 |
| G-CN-03 | 非同步視訊面試下人工智慧評鑑功能對求職者科技信任度之影響 citeturn32search3 | 中文 | 硕士论文 | 2022 | 陳瑞青 | DOI: 10.6345/NTNU202201326 citeturn32search3 | B+ | AI 面试信任与应聘者反应补充 | 是 |
| G-CN-04 | 智慧教育中的大语言模型综述 citeturn4search1 | 中文 | 期刊论文 | 2025 | 肖建利、马会苗、马会顺、马会康 | DOI: 10.11992/tis.202406015；卷期页码待人工核验 citeturn4search1 | A-（待人工核验页码） | 中文大模型教育综述 | 是 |
| G-CN-05 | 教育中的ChatGPT：教学能力诊断研究 citeturn28search13 | 中文 | 期刊论文 | 2023 | 何梁、应振宇、王英英、孙文琪 | DOI: 10.16382/j.cnki.1000-5560.2023.07.015 citeturn28search13 | A | 教学能力诊断、评价与反馈 | 是 |
| G-CN-06 | 生成式人工智能与未来教育形态重塑：技术框架、能力特征及应用趋势 citeturn30search0turn34search5 | 中文 | 期刊论文 | 2024 | 刘邦奇、聂小林、王士进等 | 未找到 DOI，但提供期刊官方页；《电化教育研究》45(01):13-20 citeturn30search0turn34search5 | A- | 教育场景总论、第一章背景 | 是 |
| G-CN-07 | 生成式人工智能在基础教育领域的应用：机遇、风险与对策 citeturn29search0turn29search1 | 中文 | 期刊论文 | 2024 | 柯清超、米桥伟、鲍婷婷 | DOI: 10.3969/j.issn.1009-8097.2024.09.001 citeturn29search0turn29search1 | A | 风险、对策、教育应用 | 是 |
| G-CN-08 | 人工智能赋能教育评价：价值、挑战与路径 citeturn34search2 | 中文 | 期刊论文 | 2024 | 郑永和、王一岩、杨淑豪 | DOI: 10.13966/j.cnki.kfjyyj.2024.04.001 citeturn34search2 | A | 评估报告、诊断—反馈链条 | 是 |
| G-CN-09 | 人工智能助力因材施教：实践误区与对策 citeturn31search14turn35search6 | 中文 | 期刊论文 | 2021 | 汪琼、李文超 | 未找到 DOI，但提供北大智能教育基地页/期刊数据库页 citeturn31search14turn35search6 | A- | 个性化训练限度、方法论约束 | 是 |
| G-CN-10 | 人工智能教育应用的偏见风险分析与治理 citeturn36search4turn36search1 | 中文 | 期刊论文 | 2021 | 沈苑、汪琼 | DOI: 10.13811/j.cnki.eer.2021.08.002 citeturn36search4turn36search1 | A | 教育场景偏差、可解释、治理 | 是 |
| G-CN-11 | 智能职业教育：人工智能时代职业教育的发展新路向 citeturn35search8turn34search3 | 中文 | 期刊论文 | 2022 | 王洋、顾建军 | DOI: 10.3969/j.issn.1001-8700.2022.01.009 citeturn35search8turn34search3 | A- | 职业能力训练、岗位训练场景 | 是 |
| G-CN-12 | 人工智能赋能职业教育高质量发展的价值、挑战与创新路径 citeturn31search4turn34search7 | 中文 | 期刊论文 | 2023 | 李东海、刘星、王鹏 | 未找到 DOI，但提供高校图书馆转引页；《教育与职业》2023(04):13-20 citeturn31search4turn34search7 | B+（待人工核验） | 职业教育与岗位训练补充 | 是（核验后） |
| G-EN-01 | *ChatGPT for Good? On Opportunities and Challenges of Large Language Models for Education* citeturn1search23turn1search11 | 英文 | 期刊论文 | 2023 | Kasneci et al. | DOI: 10.1016/j.lindif.2023.102274 citeturn1search23turn1search11 | A | LLM 教育应用机会与风险 | 是 |
| G-EN-02 | *A Systematic Literature Review of Empirical Research on Applying Generative Artificial Intelligence in Education* citeturn43search0turn43search1turn43search12 | 英文 | 期刊论文 | 2024 | Zhang et al. | DOI: 10.1007/s44366-024-0028-5 citeturn43search0turn43search1turn43search12 | A | GenAI 教育实证综述核心文献 | 是 |
| G-EN-03 | *Interacting with educational chatbots: A systematic review* citeturn1search0turn1search12 | 英文 | 期刊论文 | 2023 | Kuhail et al. | DOI: 10.1007/s10639-022-11177-3 citeturn1search0turn1search12 | A | 多轮对话、教育聊天机器人 | 是 |
| G-EN-04 | *Chatbots for language learning—Are they really useful? A systematic review of chatbot-supported language learning* citeturn1search1 | 英文 | 期刊论文 | 2022 | Huang, Hew, Fryer | DOI: 10.1111/jcal.12610 citeturn1search1 | A | 聊天机器人训练效果与局限 | 是 |
| G-EN-05 | *A meta-analysis of the effectiveness of intelligent tutoring systems on K-12 students' mathematical learning* citeturn1search18 | 英文 | 期刊论文 | 2013 | Steenbergen-Hu, Cooper | DOI: 10.1037/a0032447 citeturn1search18 | A | ITS 经典基础、训练系统理论基底 | 是 |
| G-EN-06 | *Automated Video Interview Personality Assessments: Reliability, Validity, and Generalizability Investigations* citeturn19search2 | 英文 | 期刊论文 | 2022 | Hickman et al. | DOI: 10.1037/apl0000925 citeturn19search2 | A | AI 面试评分效度与可靠性 | 是 |
| G-EN-07 | *Developing and evaluating language-based machine learning algorithms for inferring applicant personality in video interviews* citeturn20search13 | 英文 | 期刊论文 | 2024 | Hickman et al. | DOI: 10.1111/1748-8583.12585 citeturn20search13 | A | 面试语言建模、人格推断 | 是 |
| G-EN-08 | *Improving Measurement and Prediction in Personnel Selection Through the Application of Machine Learning* citeturn18search8 | 英文 | 期刊论文 | 2023 | Koenig et al. | DOI: 10.1111/peps.12608 citeturn18search8 | A | 机器学习在人员选拔的测量与预测 | 是 |
| G-EN-09 | *Asynchronous Video Interviews in Recruitment and Selection: Lights, Camera, Action!* citeturn32search2turn32search10 | 英文 | 期刊论文 | 2025 | Dunlop et al. | DOI: 10.1111/ijsa.70010 citeturn32search2turn32search10 | A | 异步视频面试设计与综述 | 是 |
| G-EN-10 | *A machine learning approach to recognize bias and discrimination in job advertisements* citeturn0search19 | 英文 | 期刊论文 | 2023 | Frissen et al. | DOI: 10.1007/s00146-022-01574-0 citeturn0search19 | A | 招聘文本偏差与公平 | 是 |
| G-EN-11 | *Matching Resumes to Jobs via Deep Siamese Network* citeturn0search8 | 英文 | 会议论文 | 2018 | Maheshwary et al. | DOI: 10.1145/3184558.3186942 citeturn0search8 | A- | 简历—岗位语义匹配经典方法 | 是 |
| G-EN-12 | *Graph Neural Networks for Candidate-Job Matching* citeturn0search6 | 英文 | 期刊论文 | 2025 | Frazzetto et al. | DOI: 10.1007/s41019-025-00293-y citeturn0search6 | A | 新一代候选人—岗位匹配方法 | 是 |
| G-TECH-01 | *Class SseEmitter* citeturn37search3turn37search21 | 英文 | 官方技术文档 | 现行 | Spring Framework | 官方 Javadoc 页（见引文） citeturn37search3turn37search21 | A | SSE 服务端实现依据 | 是 |
| G-TECH-02 | *CircuitBreaker* citeturn39search0turn39search12 | 英文 | 官方技术文档 | 现行 | Resilience4j | 官方文档页（见引文） citeturn39search0turn39search12 | A | 熔断、滑动窗口、高可用设计 | 是 |
| G-TECH-03 | *PDFTextStripper / Apache PDFBox® - A Java PDF Library* citeturn40search2turn40search6 | 英文 | 官方技术文档 | 现行 | Apache PDFBox | 官方 Javadoc/项目页（见引文） citeturn40search2turn40search6 | A | PDF 简历解析依据 | 是 |
| G-TECH-04 | *Realtime and audio | OpenAI API* citeturn37search0turn37search22 | 英文 | 官方技术文档 | 现行 | OpenAI | 官方文档页（见引文） citeturn37search0turn37search22 | A | 实时语音、低延迟交互、多模态 | 是 |
| G-TECH-05 | *Streaming messages - Claude API Docs* citeturn38search0turn38search12 | 英文 | 官方技术文档 | 现行 | Anthropic | 官方文档页（见引文） citeturn38search0turn38search12 | A | SSE 流式输出、SDK、重试与错误处理 | 是 |
| G-TECH-06 | *DeepSeek API Docs: Your First API Call* citeturn37search2turn37search20turn37search17 | 英文 | 官方技术文档 | 现行 | DeepSeek | 官方文档页（见引文） citeturn37search2turn37search20turn37search17 | A | OpenAI/Anthropic 生态兼容与流式接口 | 是 |

## 核心论点与低质量来源排除

**应进入论文的核心论点**

| 论点 | 可放入章节 | 支撑文献 | 风险 | 建议写法 |
| -- | ----- | ---- | -- | ---- |
| LLM 驱动的模拟面试平台目前更适合被界定为“训练与诊断系统”，而不是“自动招聘裁决系统” | 第一章、第二章、六章 | G-CN-01、G-CN-02、G-CN-03、G-EN-06、G-EN-09 | 容易把训练边界写成选拔边界 | 先强调“训练场景”，再说明其借鉴 AI 甄选与 AVI 研究，但不直接承担雇佣决策 |
| 多轮对话在面试训练中的价值，来自其能够连续追问、支架引导、形成性反馈，而非单轮问答本身 | 第二章、第四章 | G-EN-02、G-EN-03、G-EN-04、G-EN-05、G-CN-09 | 过度提示会削弱训练真实性 | 将多轮对话写成“支架式训练机制”，同时指出其局限是可能引发话轮漂移与提示依赖 |
| 简历解析与岗位匹配已有成熟 NLP/深度学习基础，LLM 的优势更偏向语义抽取、解释与交互接口 | 第二章、第四章 | G-EN-11、G-EN-12、G-CN-01、G-TECH-03 | 容易把“匹配”夸成“精准裁决” | 写成“在人岗匹配传统方法基础上，引入 LLM 提升语义理解与反馈可读性” |
| 评估报告的研究价值不在于“自动生成文本”，而在于能否形成“诊断—反馈—改进”的闭环 | 第二章、第四章、六章 | G-CN-08、G-EN-02、G-EN-05 | 若缺少评分依据，报告会显得虚假精确 | 建议把报告定位为“形成性反馈载体”，突出证据链、弱项定位与改进建议 |
| LLM 报告存在幻觉、偏差和解释不足，因此评分结果必须与明确维度、提示模板和证据片段绑定 | 第二章、六章 | G-CN-02、G-CN-10、G-EN-01、G-EN-10 | 若写得过满，会被质疑可靠性 | 先肯定自动评估效率，再明确写出“可信度依赖于规则约束、证据引用与人工复核” |
| 流式反馈与语音交互的意义，不只是“更炫”，而是降低等待感、维持沉浸感并强化训练连续性 | 第一章、第四章 | G-TECH-01、G-TECH-04、G-TECH-05、G-TECH-06 | 延迟与断流会反向伤害体验 | 建议用“交互连续性”和“低等待感”来表述，不要只写“实时” |
| 多 Provider 与熔断降级的意义，在于把 LLM 系统从“单点依赖”改造为“可运行的应用系统” | 第四章、六章 | G-TECH-02、G-TECH-04、G-TECH-05、G-TECH-06 | 过度工程化会削弱论文的研究味道 | 写成“面向真实部署场景的可靠性设计”，并与训练系统连续可用性相联系 |
| 用户级 API Key 加密、求职数据保护与 Demo Twin 隔离不是附属功能，而是求职/教育场景的基础要求 | 第一章、第四章、六章 | G-CN-02、G-CN-10、G-TECH-04、G-TECH-05、G-TECH-06，加仓库文档 | 如果只写实现不写必要性，会显得工程堆砌 | 先从简历、语音、评分与鉴权密钥的敏感性出发，再引出 AES-256-GCM 与演示隔离的必要性 |

**不建议引用的低质量来源**

| 来源 | 排除原因 | 替代来源建议 |
| -- | ---- | ------ |
| 知乎《推荐算法分类：协同过滤推荐、基于内容推荐、基于知识推荐》 citeturn32search9 | 普通博客式内容，且与本课题核心问题不直接对应；不能替代正式算法或招聘匹配研究 | 用 G-EN-11、G-EN-12 讨论简历—岗位匹配 |
| Medium《Comprehensive Guide to Resilience4j and the Circuit Breaker Pattern》 citeturn39search8 | 技术博客，不能替代官方技术文档 | 用 G-TECH-02 |
| Tutorialspoint《PDFBox - Reading Text》 citeturn40search10 | 二手教程，不适合作论文技术依据 | 用 G-TECH-03 |
| 维基百科《基于规则的系统》或《WebSocket》条目 citeturn32search5turn40search12 | 可编辑来源，不宜作为正式论文核心文献 | 用 WHATWG SSE、RFC 6455、Spring 官方文档 |
| 各类第三方“OpenAI API Key 使用指南”营销页/博客页 citeturn36search3turn36search14 | 既非官方，也非同行评审；容易混入过时或不准确说法 | 用 OpenAI、Anthropic、DeepSeek 官方文档与仓库实现说明 |

## Reference-refresh 建议

如果现有参考文献仍以 2023 年初的 ChatGPT 评论性文章为主，那么下一轮刷新应优先把“泛讨论”替换为“实证研究与系统综述”。在教育训练方向，优先保留或新增 Zhang 等 2024 的 GenAI 教育实证系统综述、Kuhail 2023 的教育聊天机器人系统综述、Huang 2022 的语言学习聊天机器人综述，以及经典 ITS 元分析；在招聘与面试方向，优先保留或新增 贺伟等 2024、韩敏和陈晓曦 2025、Hickman 2022、Hickman 2024、Koenig 2023、Dunlop 2025。这类文献比单纯的“ChatGPT 对教育的冲击”式评论更适合支撑论文第一章和第二章的研究现状。 citeturn43search12turn1search0turn1search1turn1search18turn33view0turn29search3turn19search2turn20search13turn18search8turn32search2

官方技术文档应被严格控制在第二章的技术背景补充、第三章需求与实现说明、第四章系统设计与实现之中，而不应挤占第一章和第二章研究现状的核心位置。特别是 Spring `SseEmitter`、Resilience4j、PDFBox、OpenAI Realtime、Anthropic Streaming、DeepSeek API 文档，应作为**工程可实现性依据**，而不是“学术研究现状”。这一界限在写作时要说清楚。 citeturn37search3turn39search0turn40search2turn37search0turn38search0turn37search2

英文文献进入正式参考文献时，建议优先级分三层。第一层是**必须保留**：G-EN-02、G-EN-03、G-EN-06、G-EN-09、G-EN-11；第二层是**强烈建议保留**：G-EN-01、G-EN-04、G-EN-08、G-EN-10、G-EN-12；第三层是**方法补充**：G-EN-05、G-EN-07。这样做能同时覆盖教育训练、面试评估、偏差治理、人岗匹配和系统设计五个维度。中文文献比例则建议稳定在 11–12 条左右，避免出现“中文过多但直接贴题性不足”或“英文过多导致本土研究缺位”的问题。 citeturn43search12turn1search0turn19search2turn32search2turn0search8turn1search23turn1search1turn18search8turn0search19turn0search6

下一轮人工核验的重点应放在四类条目上。第一类是**元数据不完整**的中文期刊文献，如 G-CN-04、G-CN-06、G-CN-12，需要补齐页码、卷期或 DOI；第二类是**通过大学库页、数据库页或期刊聚合页拿到信息**的条目，如 G-CN-01、G-CN-09、G-CN-12，建议再回到期刊官网或 CNKI/WanFang 核验一次；第三类是**经典英文文献 DOI 未在当前官方页片段中直接展示**的条目，建议统一复核；第四类是**可能与实现密切相关但不宜做研究现状核心依据**的官方技术文档，写作时要在章节定位上严格区分。 citeturn33view1turn4search1turn30search0turn31search4turn31search14turn1search6

## 附录与来源清单

**实际使用的检索式示例**

| 来源类别 | 示例检索式 |
| -- | -- |
| GitHub 项目核对 | `site:github.com/zyyyyynnn/Prelude README`；直接核对 `zyyyyynnn/Prelude` 公开仓库 README 与 `docs/demo.md` |
| 中文教育综述 | `生成式 人工智能 教育 综述 期刊 DOI`；`大语言模型 教育 综述 中文 期刊 DOI` |
| 中文招聘/面试 | `就业 指导 智能 系统 面试 中文 论文`；`组织选拔情境中AI招聘的公平威胁及其提升策略`；`基于人工智能的人才甄选：研究进展与未来展望` |
| 中文职业教育 | `人工智能赋能职业教育高质量发展的价值、挑战与创新路径`；`智能职业教育：人工智能时代职业教育的发展新路向` |
| 外文教育聊天机器人 | `"Interacting with educational chatbots: A systematic review"`；`"Chatbots for language learning—Are they really useful? A systematic review of chatbot-supported language learning"` |
| 外文 ITS / GenAI 教育 | `"A meta-analysis of the effectiveness of intelligent tutoring systems"`；`"A Systematic Literature Review of Empirical Research on Applying Generative Artificial Intelligence in Education"` |
| 外文 AI 面试/招聘 | `Hickman 2022 interview personality algorithms`；`Koenig 2023 algorithm interview ability`；`Asynchronous Video Interviews in Recruitment and Selection` |
| 外文简历匹配 | `"Matching Resumes to Jobs via Deep Siamese Network"`；`candidate-job matching graph neural networks` |
| 官方技术文档 | `Spring Framework SseEmitter Javadoc`；`Resilience4j circuitbreaker official`；`Apache PDFBox PDFTextStripper official`；`OpenAI Realtime API docs`；`Anthropic streaming messages docs`；`DeepSeek API docs streaming` |

**实际使用的来源清单**

| 站点/数据库 | 用途 |
| -- | -- |
| GitHub 公开仓库页 | 核对 Prelude 实际功能边界、Demo Twin 说明、技术栈 |
| 期刊官方页与高校期刊门户 | 获取中文核心或高质量中文论文元数据 |
| CNKI 文章页 / 期刊门户页 | 中文论文题名、作者、卷期页、部分 DOI 核验 |
| Airiti / WorldCat | 中文硕博论文与台湾学位论文核验 |
| ACM Digital Library | 人岗匹配、简历匹配与招聘系统方法文献 |
| SpringerLink | 教育聊天机器人、GenAI 教育综述、招聘与匹配研究 |
| Wiley Online Library | AVI、人员选拔与聊天机器人综述 |
| ERIC / Duke Scholars | 教育技术与 ITS 元分析辅助核验 |
| OpenAI / Anthropic / DeepSeek 官方文档 | 语音实时、流式输出、多 Provider 接口形态 |
| Spring / Resilience4j / Apache PDFBox 官方文档 | SSE、熔断与 PDF 解析的工程实施依据 |
| WHATWG / IETF | SSE 与 WebSocket 协议层说明（本次主要作技术背景，不列入核心学术候选） |

**补充说明**

本次报告已经尽量满足“中文为主、英文原题保留、提供 DOI 或官方页、排除低质量来源、技术文档不挤占研究现状核心位置”的约束。仍需人工复核的主要是部分中文条目的卷期页码或 DOI 完整性，相关位置已在候选表中明确标注“待人工核验”。