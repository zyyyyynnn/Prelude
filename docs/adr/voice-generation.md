# Voice Generation

## Context

语音回合需要低延迟反馈，但识别、文本回答、评分与语音合成失败语义不同。若把整段回答等待 TTS 完成后再显示，会放大首音频延迟；若让 TTS 失败阻断面试，则会丢失已接受回答。

## Decision

- `voice` 拥有 `VoicePort` 与 WebSocket 编排，通过 `interview` 集成 Port 驱动会话，不直接写面试表。
- 每个会话的回合串行处理；先 STT，再复用文字面试的流式回答链路。
- 助手文本按完整句子切分后提交独立 TTS 任务，音频以 Base64 事件返回；同一会话的 TTS 也串行。
- TTS 等待上限 30 秒；空识别、连接错误、TTS 失败或超时统一退回文字模式，已落库回答与评分继续。
- STT/TTS 使用部署配置的 OpenAI 兼容 endpoint 与系统密钥；transport connect/read/write timeout 均为 3 秒。
- 语音识别文本在前端只进入 composer 草稿，由候选人确认发送；音频不作为 Artifact 或持久 Asset。

## Consequences

首音频不必等待整段模型回答，文字链路始终是面试真源。并发任务与超时增加资源管理责任，必须释放麦克风、MediaRecorder、WebSocket 与音频播放。服务降级时保留文字交互，不承诺语音可用性。

## Validation

`VoiceInterviewTurnAdapterTest` 覆盖评分事件、TTS 失败退回文字与继续评分；`VoiceWebSocketHandlerTest` 覆盖 Session 撤销；前端 `installVoiceLane` demo harness 驱动真实 `useVoiceInterview` 状态机与 composer。
