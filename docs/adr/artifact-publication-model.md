# Artifact 发布模型

## Context

[#36](https://github.com/zyyyyynnn/Prelude/issues/36) 已冻结正式成果与 Asset 的边界，但当前报告直接存入 InterviewSession，Artifact 发布 API 没有生产消费者。无消费者不等于契约无效，直接删除也会丢失已承诺的不可变版本模型。

## Decision

- 保留 `artifact`、`artifact_version`、`ArtifactCommandApi`、`ArtifactQueryApi` 与 `ArtifactPublishService`，明确标记为预留能力。
- 正式成果只能经 `publishVersion(accountId, kind, assetId, provenanceJson)` 发布；其他模块不得直接写表。
- Artifact 按 `(accountId, kind)` 定位；每次发布创建递增不可变版本，数据库唯一约束处理并发，冲突有限重试。
- 可选 `assetId` 必须属于同一账户且为 READY；provenance 使用业务 ID/revision，不以 SHA 代替。
- 出现真实消费者时接入 Named Interface/API，并验证跨模块账户隔离、版本不可变、Asset 生命周期与 provenance。
- 仅在后续决策明确替代 #36，且无消费者、无已发布数据时移除；有数据时必须先完成迁移与保留审批，再同次删除表、API、类型与旧测试。

## Consequences

当前保留少量未接入代码与数据库表，换取稳定公共契约和未来发布入口。它不是报告生成链路的前置依赖，也不得为了“有消费者”而把现有报告存储重复写入 Artifact。接入时必须定义报告/导出内容与 Asset 的所有权，不能形成双真源。

## Validation

`ArtifactPublishServiceTest` 验证递增不可变版本、账户与 READY Asset 校验；Flyway 验证表与约束存在；源码检索确认当前无生产调用方。接入或移除时以对应消费者集成测试和迁移验证作为门禁。
