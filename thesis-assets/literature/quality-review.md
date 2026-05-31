# 文献质量评估表

> 用途：记录本轮论文文献补充的筛选依据。  
> 结论：正式论文优先采用 A/B+ 文献；C 类仅作背景或补充，不作为核心理论依据；字段不完整的文献不强行写入正式参考文献。

| 编号 | 文献 | 来源类型 | 可核验性 | 主题相关度 | 质量等级 | 是否进入本轮 DOCX | 使用位置 | 备注 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| CN-01 | 刘泽垣等. 大语言模型的幻觉问题研究综述 | 软件学报综述 | DOI 与期刊页可核验 | 高 | A | 是 | 2.1、3.1、6.2 | 支撑幻觉、可信性和系统边界 |
| CN-02 | 黄河燕等. 大语言模型安全性：分类、评估、归因、缓解、展望 | 智能系统学报综述 | DOI 与期刊页可核验 | 高 | A- | 是 | 2.1、6.2 | 支撑安全性、风险控制 |
| CN-03 | 罗文, 王厚峰. 大语言模型评测综述 | 中文信息学报综述 | 来源可查，但卷期页码待人工核验 | 中高 | A- | 否 | 5.1 可选 | 元数据不完整，暂不进入正式参考文献 |
| CN-04 | 曹荣荣等. 融合知识图谱的大语言模型研究综述 | 计算机应用研究综述 | DOI 与期刊页可核验 | 中高 | A-/B+ | 是 | 6.2 | 支撑知识图谱、事实核验和幻觉缓解 |
| CN-05 | 徐磊等. 针对大语言模型的偏见性研究综述 | 计算机应用研究综述 | DOI 与期刊页可核验 | 中高 | B+ | 是 | 2.1、3.1、6.2 | 支撑偏见与公平性风险 |
| CN-06 | 肖建力等. 智慧教育中的大语言模型综述 | 智能系统学报综述 | DOI 与期刊页可核验 | 中 | B+ | 是 | 1.1、6.2 | 支撑训练辅助和多模态教育场景 |
| CN-07 | 陆苏于等. GEMINI+互感评估工作流设计 | 普通期刊 / 系统设计 | 公开页面可核验 | 高 | B-/C+ | 已存在并保留 | 1.2、6.2 | 与大学生面试训练场景直接相关，但期刊层级一般 |
| CN-08 | 段雪艳等. 基于BERT与LightGBM的人岗匹配模型 | 普通期刊 | DOI 可核验 | 中 | C+ | 否 | 1.2 可选 | 质量一般，当前用既有简历/岗位匹配文献替代 |
| EN-01 | Nofal et al. AI-enhanced interview simulation in the metaverse | Computers and Education: Artificial Intelligence | DOI 与出版社页可核验 | 高 | A | 是 | 1.2、4.6、6.2 | 支撑 AI 面试训练、多模态反馈 |
| EN-02 | AI-driven semantic similarity-based job matching framework for recruitment systems | Information Sciences | DOI 可查但作者字段未核验 | 高 | A | 否 | 1.1 可选 | 作者字段未完成核验，暂不写入正式参考文献 |
| EN-03 | Laban et al. LLMs Get Lost In Multi-Turn Conversation | arXiv / ICLR 2026 | arXiv 与机构页可核验 | 高 | A | 是 | 4.5、6.2 | 支撑多轮对话失稳与上下文截断 |
| EN-04 | Li et al. Beyond Single-Turn | arXiv 综述 | arXiv 可核验 | 高 | A-/B+ | 已存在并保留 | 1.2、2.1、4.5、6.2 | 支撑多轮 LLM 交互综述 |
| EN-05 | Yan et al. ContextCache | PVLDB | DOI 与出版社页可核验 | 中高 | A | 已存在并保留 | 6.2 | 支撑上下文缓存和效率优化 |
| EN-06 | Uppalapati et al. AI-driven mock interview assessment | Springer 期刊 | DOI 可核验 | 高 | B+ | 是 | 1.2、4.6 | 支撑自动化面试评价 |
| EN-07 | Skondras et al. Zero-Shot Resume-Job Matching | Electronics | DOI 可核验 | 中高 | B-/C+ | 是 | 1.1、1.2、4.3 | 支撑结构化提示和简历岗位匹配 |
| DOC-01 | MDN EventSource | 官方技术文档 | 官方页面可核验 | 中 | A | 是 | 2.2 | 只作技术依据 |
| DOC-02 | Spring Framework Asynchronous Requests | 官方技术文档 | 官方页面可核验 | 中 | A | 是 | 2.4 | 只作技术依据 |
| DOC-03 | Apache PDFBox PDFTextStripper | 官方 API 文档 | 官方页面可核验 | 中 | A | 是 | 2.5、4.3 | 只作技术依据 |
| DOC-04 | DeepSeek API Docs | 官方 API 文档 | 官方页面可核验 | 中 | A | 是 | 2.1、4.5 | 只作技术依据 |

## 本轮筛选结论

- 正式 DOCX 共保留并使用 21 条参考文献。
- 新增重点集中在：LLM 幻觉、安全、偏见、智慧教育、AI 面试训练、多轮对话失稳、自动评价、简历岗位匹配、SSE/Spring/PDFBox/DeepSeek 官方技术依据。
- `大语言模型评测综述` 与 `AI-driven semantic similarity-based job matching framework` 暂未进入 DOCX 正式参考文献，原因是当前可用信息中存在字段待核验。
