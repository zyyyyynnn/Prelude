# 面试领域逻辑重构证据（2026-06-18）

## 证据范围

本证据记录一次结构质量重构：文本面试链路和语音面试链路复用同一组领域服务，前端页面将底层流式通信、报告监听、语音 WebSocket 和语音播放队列下沉到 composable。重构不改变 API 契约、演示数据 seed、前端视觉样式或 UI token。

## 后端源码索引

- `backend/src/main/java/com/interview/service/impl/InterviewStageManager.java`
  - 统一维护阶段顺序、阶段提示词、初始阶段创建、当前阶段查询、阶段推进和阶段关闭。
- `backend/src/main/java/com/interview/service/impl/InterviewContextService.java`
  - 统一构造文本与语音共用的 LLM 上下文，包含系统提示词、RAG 片段、滑动摘要和最近对话。
- `backend/src/main/java/com/interview/service/impl/InterviewJudgeService.java`
  - 统一处理即时评分、Redis 评分锁、JSON 解析、分数裁剪和消息评分持久化。
- `backend/src/main/java/com/interview/service/impl/InterviewSummaryService.java`
  - 统一处理滑动摘要异步刷新。
- `backend/src/main/java/com/interview/service/impl/InterviewServiceImpl.java`
  - 保留文本面试 start/list/get/updateStage/chat/finish/listen 编排职责。
- `backend/src/main/java/com/interview/config/VoiceWebSocketHandler.java`
  - 保留 WebSocket session 管理、音频 buffer、消息收发和语音链路编排职责。

## 前端源码索引

- `frontend/src/composables/useInterviewTextStream.ts`
  - 负责普通文本 SSE stream、chunk buffer、离线 snapshot、watchdog 和发送流程。
- `frontend/src/composables/useReportListener.ts`
  - 负责 `/interview/{id}/listen` SSE、报告完成、fallback 和错误事件处理。
- `frontend/src/composables/useVoiceInterviewSocket.ts`
  - 负责语音 WebSocket 初始化、消息解析、语音状态和音频 chunk 入口。
- `frontend/src/composables/useComposerVoice.ts`
  - 负责麦克风录音、波形绘制、音频播放队列和 object URL 生命周期。
- `frontend/src/views/InterviewView.vue`
  - 保留页面状态编排和模板渲染。
- `frontend/src/components/workspace/InterviewComposer.vue`
  - 保留 send 框模板、事件绑定和视觉样式。

## 核心片段定位

后端共享阶段逻辑入口：

```java
public Optional<InterviewStage> advanceStage(Long sessionId, boolean insertSystemPrompt)
```

后端共享上下文入口：

```java
public List<Map<String, String>> buildContextMessages(Long sessionId)
```

后端共享评分入口：

```java
public Optional<JudgeResult> judgeAndPersist(InterviewSession session, InterviewMessage userMsg, boolean voiceMode)
```

前端文本流入口：

```ts
const { streamReply, cleanupTextStream } = useInterviewTextStream({ ... })
```

前端语音播放入口：

```ts
const { startRecording, stopRecording } = useComposerVoice({ ... })
```

## 验证记录

本证据检查点使用以下命令验证：

```powershell
cd backend; mvn test
cd frontend; npm run build
cd frontend; npm run verify:byok
cd frontend; npm run verify:dark
sentrux check E:\Prelude
```
