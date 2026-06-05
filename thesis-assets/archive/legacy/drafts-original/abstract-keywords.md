# 摘要与关键词

## 中文摘要

随着就业竞争加剧和高校毕业生规模持续扩大，传统依赖人工经验与静态题库的求职训练方式已难以满足个性化、连续化和即时化的训练需求。针对这一问题，本文设计并实现了一种基于大语言模型的沉浸式模拟面试与简历诊断系统。系统采用 Vue 3、TypeScript、Spring Boot 3 与 MySQL 构建前后端业务框架，并结合 DeepSeek API、Apache PDFBox 与 SSE 流式通信机制，实现了简历解析、岗位驱动的多轮问答、流式面试交互与评估报告生成等核心功能。

在具体实现中，系统首先通过 PDF 文本提取与大语言模型结构化输出，对用户简历中的技能和项目经历进行提炼；随后根据岗位模板构建面试会话上下文，驱动模型围绕用户履历生成更具针对性的面试问题；在交互阶段，系统采用 `SseEmitter` 与前端流式接收机制实现增量式内容展示，以提升反馈即时性；在总结阶段，系统结合会话历史生成包含技术匹配度、表达清晰度、优势总结与改进建议的结构化报告。从系统设计与实现链路看，该系统已经形成较为完整的“简历诊断—模拟面试—结果反馈”业务闭环，能够支撑后续真实测试与功能验证工作的展开。

本文工作表明，大语言模型在求职训练场景中具有较好的应用潜力，但系统在长程上下文管理、内容可靠性与多模态交互方面仍存在进一步优化空间。后续可围绕摘要压缩、事实核验和语音化扩展等方向展开改进，以增强系统的稳定性、真实性与沉浸体验。

**关键词：** 大语言模型；模拟面试；简历解析；流式响应；上下文管理

## English Abstract

With the intensifying competition in the job market and the continuous growth in the number of university graduates, traditional interview preparation methods based on manual guidance and static question banks can no longer fully satisfy the needs for personalized, continuous, and timely training. To address this problem, an immersive mock interview and resume diagnosis system based on large language models is designed and implemented in this study. The system is built with Vue 3, TypeScript, Spring Boot 3, and MySQL, while DeepSeek API, Apache PDFBox, and Server-Sent Events are integrated to support resume parsing, position-oriented multi-turn interviewing, streaming interaction, and automated report generation.

In the implementation process, resume text is first extracted from PDF files and then transformed into structured information, including skills and project experience, with the assistance of a large language model. On this basis, interview sessions are initialized with position templates so that subsequent questions can be generated according to both the target role and the user’s resume context. During the interview process, incremental model outputs are pushed to the front end through streaming communication, which improves immediacy and interaction continuity. After the interview is finished, the dialogue history is reorganized to produce a structured evaluation report covering technical matching, clarity of expression, strengths, and improvement suggestions. From the perspective of system design and implementation, the system has formed a relatively complete business loop covering resume diagnosis, mock interview interaction, and result feedback, which provides a basis for subsequent real-world testing and verification.

The study demonstrates that large language models have practical potential in job interview training scenarios. However, limitations still remain in long-context management, output reliability, and multimodal interaction. Future work may focus on summary-based context compression, fact verification, and the integration of speech and multimodal technologies to improve system robustness, credibility, and immersion.

**Keywords:** large language model; mock interview; resume parsing; streaming response; context management
