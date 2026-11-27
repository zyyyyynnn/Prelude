# Asset 耐久删除

## Context

Asset 引用删除与 S3 对象删除跨越数据库和对象存储。先删对象再提交数据库可能留下永久悬空引用；先提交引用删除而未保存删除意图，又可能永久泄漏对象。

## Decision

- 上传仅有 `PENDING_UPLOAD/READY`；每个 Asset 使用不可预测的新 objectKey，不覆盖旧对象。
- 删除业务引用时，在同一 MySQL 事务写入幂等 `asset_deletion_request`，保存 account、asset、objectKey 与 attempt。
- 有界 scheduler 以批量与租约领取 PENDING；过期 RUNNING 回收为 PENDING。
- worker 先校验账户与 objectKey 未变，再幂等删除对象，最后以当前 attempt fence 删除 Asset 元数据与请求。
- 存储失败保留元数据并指数退避；账户或 key 不一致进入 BLOCKED，不触碰对象存储。
- 不为单个 Asset 建立第二套 BackgroundJob runtime。

## Consequences

对象存储暂时领先于数据库是可接受状态，删除意图保证最终收敛。worker 必须容忍重复投递、租约失效和缺失对象；BLOCKED 需要运维介入，不能被静默清除。Asset 删除是异步完成，不承诺接口返回时物理对象已消失。

## Validation

`AssetDeletionWorkerTest` 验证顺序、重试与阻断；`AssetDeletionMySqlTest` 验证事务回滚、并发登记、重复删除、租约回收和存储失败恢复；`AttachmentScopeDeletionTest` 与 `AvatarAssetStorageTest` 验证各业务引用入口。
