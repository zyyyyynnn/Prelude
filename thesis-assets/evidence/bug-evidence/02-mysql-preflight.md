# Bug 2 证据：MySQL 未就绪导致后端启动失败

## 主讲定位

该问题适合作为论文或答辩主讲 Bug，用于说明后端启动失败不一定来自业务代码，数据库服务、端口、账号密码和本地密钥配置也必须纳入启动链路校验。

## 原始现象

后端启动到 Hikari 数据源初始化阶段失败，日志包含：

```text
com.mysql.cj.jdbc.exceptions.CommunicationsException: Communications link failure
Caused by: java.net.ConnectException: Connection refused: getsockopt
```

Spring Boot 随后取消上下文初始化并退出。

## 根因

异常发生在 `DataSourceScriptDatabaseInitializer` 获取 JDBC 连接阶段，说明问题位于数据库连接前置条件，而不是 Controller、Service 或 Mapper 的业务逻辑。常见原因包括 MySQL 服务未启动、端口未监听、本地配置指向错误地址或账号密码与实际环境不一致。

## 修复证据

启动脚本准备阶段已加入数据库连接与关键密钥配置校验。当前准备阶段结果：

```text
pwsh -ExecutionPolicy Bypass -File .\scripts\demo\start-demo.ps1 -PrepareOnly
Demo runtime preparation complete.
```

准备阶段通过后，后端健康检查可返回：

```text
GET http://127.0.0.1:8081/api/health
{"status":"ok"}
```

## 论文可用结论

该问题体现了一键启动脚本的工程价值：脚本不只是串联命令，还应承担环境前置校验职责。提前检查 MySQL、JWT 密钥和 AES 密钥后，可以把长堆栈错误转化为更清晰的环境错误提示，提高本地运行和答辩演示稳定性。
