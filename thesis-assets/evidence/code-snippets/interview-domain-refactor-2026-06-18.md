# 面试领域逻辑重构证据（2026-06-18）

## 范围

本证据记录一次结构质量重构：将文本面试链路与语音面试链路中重复的领域逻辑抽出到共享服务中，同时保持 API 契约、演示数据 seed 口径和前端视觉样式不变。

涉及源码文件：

- `backend/src/main/java/com/interview/service/impl/InterviewStageManager.java`
- `backend/src/main/java/com/interview/service/impl/InterviewContextService.java`
- `backend/src/main/java/com/interview/service/impl/InterviewJudgeService.java`
- `backend/src/main/java/com/interview/service/impl/InterviewSummaryService.java`
- `backend/src/main/java/com/interview/service/impl/InterviewServiceImpl.java`
- `backend/src/main/java/com/interview/config/VoiceWebSocketHandler.java`
- `frontend/src/composables/useInterviewTextStream.ts`
- `frontend/src/composables/useReportListener.ts`
- `frontend/src/composables/useVoiceInterviewSocket.ts`
- `frontend/src/composables/useComposerVoice.ts`

## 后端领域服务

`InterviewStageManager` 统一维护面试阶段顺序、阶段提示词、初始阶段创建、阶段推进和当前阶段查询：

```java
@Service
@RequiredArgsConstructor
public class InterviewStageManager {
    public static final String STAGE_WARMUP = "warmup";
    public static final String STAGE_TECHNICAL = "technical";
    public static final String STAGE_DEEP_DIVE = "deep_dive";
    public static final String STAGE_CLOSING = "closing";

    public String currentStageName(Long sessionId) {
        return Optional.ofNullable(currentOrLatestStage(sessionId))
            .map(InterviewStage::getStageName)
            .orElse(STAGE_WARMUP);
    }

    public Optional<InterviewStage> advanceStage(Long sessionId, boolean insertSystemPrompt) {
        InterviewStage current = currentOrLatestStage(sessionId);
        if (current == null || current.getEndedAt() != null) {
            return Optional.empty();
        }
        closeStage(current);
        int currentIndex = STAGE_ORDER.indexOf(current.getStageName());
        if (currentIndex < 0 || currentIndex >= STAGE_ORDER.size() - 1) {
            return Optional.empty();
        }
        return Optional.of(insertNextStage(sessionId, STAGE_ORDER.get(currentIndex + 1), insertSystemPrompt));
    }
}
```

`InterviewContextService` 统一构造文本与语音两条链路共用的 LLM 上下文，内容包括 RAG 片段、滑动摘要和最近对话：

```java
public List<Map<String, String>> buildContextMessages(Long sessionId) {
    InterviewSession session = interviewSessionMapper.selectById(sessionId);
    if (session == null) {
        return List.of();
    }

    ArrayList<Map<String, String>> messages = new ArrayList<>();
    messages.add(Map.of("role", "system", "content", buildSystemPrompt(session)));

    for (String chunk : sessionRagService.searchTopChunks(sessionId, session.getTargetPosition(), 4)) {
        messages.add(Map.of("role", "system", "content", "相关简历/JD片段：" + chunk));
    }

    appendSummary(messages, session);
    appendRecentDialogs(messages, sessionId);
    return messages;
}
```

`InterviewJudgeService` 统一处理即时评分与提示生成、Redis 评分锁、JSON 解析、分数裁剪和持久化：

```java
public Optional<JudgeResult> judgeAndPersist(InterviewSession session, InterviewMessage userMsg, boolean voiceMode) {
    String lockKey = "interview:judge:" + userMsg.getId();
    Boolean locked = redisTemplate.opsForValue().setIfAbsent(lockKey, "1", Duration.ofMinutes(2));
    if (!Boolean.TRUE.equals(locked)) {
        return Optional.empty();
    }

    try {
        JudgeResult result = resolveJudgeResult(session, userMsg, voiceMode);
        userMsg.setScore(result.score());
        userMsg.setHint(result.hint());
        interviewMessageMapper.updateById(userMsg);
        return Optional.of(result);
    } finally {
        redisTemplate.delete(lockKey);
    }
}
```

`InterviewSummaryService` 统一处理异步滑动摘要刷新：

```java
public void triggerAsyncSummarizeIfNeeded(InterviewSession session, boolean voiceMode) {
    if (session == null || session.getId() == null) {
        return;
    }
    sseTaskExecutor.execute(() -> summarizeIfNeeded(session, voiceMode));
}
```

## 重构后的编排关系

`InterviewServiceImpl` 现在只保留文本面试编排职责，将阶段、上下文、评分和摘要等内部领域逻辑委托给共享服务：

```java
List<Map<String, String>> contextMessages = autoStart
    ? interviewContextService.buildAutoStartMessages(session)
    : interviewContextService.buildContextMessages(sessionId);

llmRouter.streamWithSnapshot(
    session.getLlmProvider(),
    session.getLlmModel(),
    contextMessages,
    delta -> { ... }
);

interviewJudgeService.judgeAndPersist(session, userMsg, false)
    .ifPresent(result -> sendJudgeEvent(emitter, result));
interviewSummaryService.triggerAsyncSummarizeIfNeeded(session, false);
```

`VoiceWebSocketHandler` 保留 WebSocket 会话管理和语音链路编排职责，并复用共享领域服务：

```java
List<Map<String, String>> contextMessages = interviewContextService.buildContextMessages(activeSessionId);
String aiText = llmRouter.chatWithSnapshot(
    interviewSession.getLlmProvider(),
    interviewSession.getLlmModel(),
    contextMessages
);

interviewStageManager.advanceStage(activeSessionId, false);
triggerVoiceJudge(interviewSession, userMsg, session);
interviewSummaryService.triggerAsyncSummarizeIfNeeded(interviewSession, true);
```

## 前端职责拆分

`InterviewView.vue` 将底层 SSE 流处理、报告监听和语音 WebSocket 管理委托给 composable：

```ts
const {
  appendMessage,
  ensureAssistantPlaceholder,
  appendAssistantDelta,
  streamReply,
  cleanupTextStream,
} = useInterviewTextStream({ ... })

const {
  startListeningReport,
  stopListeningReport,
} = useReportListener({ ... })

const {
  voiceStatus,
  incomingAudioChunk,
  closeVoiceSocket,
  handleAudioChunk,
  handleStartRecording,
  handleStopRecording,
  handlePlayStatus,
} = useVoiceInterviewSocket({ ... })
```

`InterviewComposer.vue` 保持模板和样式不变，将录音、波形绘制和音频播放队列委托给 `useComposerVoice`：

```ts
const {
  setCanvasRef,
  displayStatus,
  isRecording,
  startRecording,
  stopRecording,
} = useComposerVoice({
  incomingAudio: toRef(props, 'incomingAudio'),
  isVoiceMode: toRef(props, 'isVoiceMode'),
  voiceStatus: toRef(props, 'voiceStatus'),
  onAudioChunk: (chunk) => emit('voice-audio-chunk', chunk),
  onStartRecording: () => emit('voice-start-recording'),
  onStopRecording: () => emit('voice-stop-recording'),
  onPlayStatus: (status) => emit('voice-play-status', status),
})
```

## 验证记录

本次证据检查点使用的验证命令：

```powershell
cd backend; mvn test
cd frontend; npm run build
cd frontend; npm run verify:byok
cd frontend; npm run verify:dark
sentrux check E:\Prelude
```
