# dev-notes.md 记录模板

## Bug 1

**问题**：流式接口偶发性在收到约一半内容后停止推送

**现象**：Network 面板显示 SSE 连接正常建立，data 事件在
收到部分内容后停止触发，没有 error 事件，前端打字机渲染中断

**排查过程**：在 Service 层加入日志后发现，DeepSeek 流式
响应的最后一个 chunk 内容是 [DONE]，代码未做判断直接尝试
解析 choices[0].delta.content，该字段为 null，
导致 NullPointerException，异步线程崩溃退出，
SseEmitter 未调用 complete()

**解决方案**：在解析每个 chunk 前判断内容是否为 [DONE]，
是则调用 emitter.complete() 正常关闭连接

**教训**：OpenAI 协议的流式响应以 data: [DONE] 作为终止标志，
这是必须处理的边界条件，不处理会导致前端连接异常挂起

---

## Bug 2

**问题**：[在此填写你真实遇到的第二个 Bug]

**现象**：

**排查过程**：

**解决方案**：

**教训**：
