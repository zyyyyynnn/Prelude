# Dev Scripts (源码级调试)

> **日常开发推荐入口**：根目录 `start-real.bat` / `start-demo.bat` (Local App Runtime)。
> 本目录的脚本面向**更底层、更定制化**的后端/前端源码级调试，拦截启动步骤与日志分析。

## 何时用 dev mode 脚本 (`.ps1`)

- 需要完全掌控 Maven 和 Vite 启动参数
- 调试自动化启动脚本逻辑

## 中间件：复用 Docker 暴露的端口

dev mode **不单独安装本机 MySQL/Redis/RabbitMQ**。启动前请运行中间件：

```powershell
docker compose up -d mysql redis rabbitmq
```

dev mode 连接约定（与 `application-local.yml` 默认值一致）：

| 中间件   | dev 访问地址        |
| :------ | :------------------ |
| MySQL   | 127.0.0.1:13306     |
| Redis   | 127.0.0.1:16379     |
| RabbitMQ| 127.0.0.1:5672      |

## 入口脚本

`scripts/real/start-real.ps1`、`scripts/demo/start-demo.ps1` 是底层的 PowerShell 启动器。

使用前：

1. 确保 `backend/src/main/resources/application-local.yml` 正确配置，指向 Docker 暴露的端口。
2. 启动中间件并运行脚本：
   ```powershell
   docker compose up -d mysql redis rabbitmq
   .\scripts\real\start-real.ps1
   ```

> 注意：这些 `.ps1` 脚本是 `start-real.bat` 底层调用的更细粒度版本，推荐直接使用根目录的批处理文件。
