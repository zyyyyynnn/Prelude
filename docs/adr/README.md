# 架构决策记录

每篇记录当前有效决策、后果与可复现验证。模块边界总表见 [后端架构](../backend/architecture.md)。

| 决策                                                        | 范围                             |
| ----------------------------------------------------------- | -------------------------------- |
| [Modulith 与 Named Interface](modulith-named-interfaces.md) | 16 模块拓扑、根包例外与窄 Port   |
| [Interview 显式阶段状态](interview-stage-state.md)          | 会话、阶段、待答与评估状态       |
| [Voice generation](voice-generation.md)                     | STT、流式回答与按句 TTS          |
| [Asset 耐久删除](asset-durable-deletion.md)                 | 删除意图、租约与对象/元数据顺序  |
| [Retrieval after-commit](retrieval-after-commit.md)         | 提交后索引、失效与可重建派生状态 |
| [Evidence 内容哈希](evidence-content-hash.md)               | 前端输入修订与截图字节完整性     |
| [Vite+ 与 Oxc 工具链](vite-plus-oxc-toolchain.md)           | 统一开发、检查、构建与验证       |
| [路线图占位模块](roadmap-modules.md)                        | 四个开放 Issue 对应的显式契约    |
| [Artifact 发布模型](artifact-publication-model.md)          | 无消费者的正式成果预留能力       |
