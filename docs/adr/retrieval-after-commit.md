# Retrieval After-Commit

## Context

检索索引与 embedding 是派生状态，不应成为开面或删除事务的提交条件；但事务提交前读取源数据会看到不完整会话，回滚后也可能留下幽灵索引。

## Decision

- MySQL 中的会话、简历、JD 与附件元数据是检索源真源；内存索引与 `retrieval_chunk` 是可重建派生状态。
- `StartInterview` 在事务内构造不可变文档列表，通过 `RetrievalTransactions.afterCommit` 注册动作，再由 executor 异步 index。
- `DeleteInterviewSession` 在业务删除提交后才 invalidate。
- 无事务同步时 `afterCommit` 立即执行；有事务时只注册 `afterCommit` 回调。
- 失效先在 `retrieval_scope_tombstone` 写入持久标记，再删除 chunk；失败时 scope 加入有界 pending 集合并对搜索不可见，scheduler 每轮最多重试 100 个 scope，防止删除后的数据通过重建回滚可见。
- 缓存或持久 chunk 缺失时，从已提交源数据重建；embedding 不可用时退回关键词评分。

## Consequences

开面事务不等待远程 embedding，也不会为回滚请求留下索引。提交后到索引完成之间存在短暂窗口，此时依赖源重建而非强一致索引。删除后的不可见性优先于立即可重建性；invalidation 重试是可用性责任，不是静默降级。

## Validation

`StartInterviewTest` 验证提交前不 index、提交后 index、回滚不留索引及无事务时立即执行；`DeleteInterviewSessionTest` 验证提交后 invalidate 与回滚不失效；`InterviewRetrievalSourceAdapterTest` 验证统一源文档；`InMemoryRetrievalAdapterTest` 覆盖索引、持久/源重建、失效、持久 tombstone 重启与 embedding 回退；`MybatisRetrievalChunkStoreTest` 验证 tombstone 拒绝迟到 replace/load。
