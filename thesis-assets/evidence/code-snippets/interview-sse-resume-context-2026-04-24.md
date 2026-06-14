# 面试流式、简历解析与上下文控制证据 2026-04-24

## 证据 1：SSE 流式问答推送

来源文件：

- `backend/src/main/java/com/interview/service/impl/InterviewServiceImpl.java`
- `backend/src/main/java/com/interview/llm/AbstractOpenAiCompatibleProvider.java`

关键实现点：

- 后端使用 `SseEmitter` 建立服务端到前端的流式通道。
- 用户回答先落库为 `user` 消息，再按会话历史构造上下文。
- 模型输出增量内容时，后端逐段发送 `message` 事件，并同步拼接完整面试官回复。
- 流式结束后，完整 `assistant` 回复再次落库，保证回放与报告生成能够基于完整历史记录。
- OpenAI 兼容流式协议中的 `data: [DONE]` 被显式识别，避免终止标记被当作普通 JSON 解析。

可引用代码位置：

```text
InterviewServiceImpl.chat(...)
InterviewServiceImpl.streamAssistantReply(...)
InterviewServiceImpl.sendDelta(...)
AbstractOpenAiCompatibleProvider.stream(...)
```

论文可用表述：

系统在模拟面试问答中采用 Spring MVC 提供的 `SseEmitter` 实现服务端事件推送。用户提交回答后，服务端在异步线程中调用大语言模型流式接口，并将模型返回的增量文本实时推送给前端。流式输出结束后，系统再将完整面试官回复写入数据库，从而同时满足实时展示与历史回放的数据完整性要求。

## 证据 2：PDF 简历解析与结构化提取

来源文件：

- `backend/src/main/java/com/interview/service/impl/ResumeServiceImpl.java`

关键实现点：

- 上传入口先校验文件是否为空、扩展名是否为 `.pdf`、大小是否超过 10MB。
- 使用 PDFBox 的 `Loader.loadPDF` 加载 PDF 文件。
- 使用 `PDFTextStripper` 提取文本，并设置 `setSortByPosition(true)` 保持版面顺序。
- 若 PDF 无有效文本或解析失败，返回明确业务错误。
- 提取后的原始文本再交给 LLM 生成技能与项目经历 JSON，后端校验 JSON 合法性后落库。

可引用代码位置：

```text
ResumeServiceImpl.validateFile(...)
ResumeServiceImpl.extractPdfText(...)
ResumeServiceImpl.parseByLlm(...)
```

论文可用表述：

简历解析模块首先在后端完成 PDF 文件类型和大小校验，随后通过 PDFBox 提取文本内容。系统将提取到的原始文本作为大语言模型输入，要求模型返回固定 JSON 结构，再由后端进行反序列化校验并写入数据库。该设计将文件校验、文本抽取和智能结构化三步解耦，便于错误处理和后续扩展。

## 证据 3：多轮上下文截断

来源文件：

- `backend/src/main/java/com/interview/service/impl/InterviewServiceImpl.java`

关键实现点：

- `buildContextMessages` 按会话查询历史消息。
- 当历史消息数量超过 20 条时，保留首条系统消息，并截取最近 18 条消息参与模型上下文。
- 自动首问时使用 `limitText` 将简历原文限制在 1800 字以内。
- 该策略避免上下文无限增长，同时保留系统提示词和最近对话状态。

可引用代码位置：

```text
InterviewServiceImpl.buildContextMessages(...)
InterviewServiceImpl.buildAutoStartMessages(...)
InterviewServiceImpl.limitText(...)
```

论文可用表述：

为控制模型调用成本并降低长上下文带来的响应波动，系统在构造对话上下文时采用截断策略：当历史消息超过阈值时，保留第一条系统提示消息和最近若干轮对话。这样既能维持岗位设定约束，又能保证模型重点参考最近交互内容。
