# 混合检索有限容量实验（2026-07-15）

## 实验口径

| 项目 | 值 |
| --- | --- |
| 代码入口 | `HybridRetrievalCapacityTest` |
| 命令 | `mvn -f backend/pom.xml "-Dtest=HybridRetrievalCapacityTest" "-Dprelude.benchmark=true" "-Djacoco.skip=true" test` |
| 系统 | Windows 11，Java 21.0.4，16 logical processors |
| 数据 | 5,000 个单 chunk 合成文档，64 维确定性 hash embedding |
| 查询 | 50 次预热；300 次计时查询；`topK = 5` |
| 质量断言 | 每个查询包含唯一目标标记，检查目标是否进入 Top 5 |

## 实测结果

```text
RETRIEVAL_CAPACITY documents=5000 queries=300 dimensions=64 index_ms=183.324 search_p50_ms=1.714 search_p95_ms=2.614 recall_at_5=1.0000 java=21.0.4 os=Windows 11 processors=16
```

| 指标 | 结果 |
| --- | ---: |
| 建索引耗时 | 183.324 ms |
| 单次搜索 P50 | 1.714 ms |
| 单次搜索 P95 | 2.614 ms |
| Recall@5 | 1.0000 |

## 可支持结论

- 当前线性融合实现可在本机单进程内完成 5,000 chunk、300 次查询的有限规模实验。
- 强关键词目标未被向量分数预筛丢弃；该断言与关键词/向量全候选融合实现一致。
- 测试时延不进入 CI 阈值，只保留 Recall@5 行为断言，避免不同 runner 的时延波动制造假失败。

## 不可外推范围

- 合成唯一标记不等于真实简历语义相关性，不证明通用 RAG 准确率。
- 实验不包含 MySQL、网络 embedding、并发用户、多实例、长连接或生产硬件。
- P50/P95 只代表本次机器和 JVM 运行，不作为生产 SLO。
