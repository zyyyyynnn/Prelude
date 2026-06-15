# Dev local runtime（仅开发调试，非默认入口）

> 默认运行方式是 Docker Compose 全栈：根目录 `start-real.bat` / `start-demo.bat`。
> 本目录面向**后端/前端源码级调试**，需要本机 JDK 21 / Maven / Node。

## 何时用 dev mode

- 需要在 IDE 里断点调试 Spring Boot 后端
- 需要 Vite HMR 实时改前端
- 需要跳过 Docker 镜像重建，快速迭代

## 中间件：复用 Docker 暴露的端口

dev mode **不单独安装本机 MySQL/Redis/RabbitMQ**。先让 Docker 中间件跑起来，再让本机后端连这些暴露端口：

```powershell
# 仅起中间件（不起容器化 backend/frontend）
docker compose --profile real up -d mysql redis rabbitmq
```

dev mode 连接约定（与 application-local.yml 默认值一致）：

| 中间件   | dev 访问地址        |
| :------ | :------------------ |
| MySQL   | 127.0.0.1:13306     |
| Redis   | 127.0.0.1:16379     |
| RabbitMQ| 127.0.0.1:5672      |

## dev 入口脚本

`scripts/real/start-real.ps1`、`scripts/demo/start-demo.ps1` 是 dev mode 的本机启动器（mvn spring-boot:run + vite dev server）。

使用前：

1. 复制 `backend/src/main/resources/application-local.example.yml` 为 `application-local.yml`，按 dev 端口（13306/16379/5672）填写。
2. 真实版：
   ```powershell
   docker compose --profile real up -d mysql redis rabbitmq
   .\scripts\real\start-real.ps1
   ```
3. Demo 版：
   ```powershell
   docker compose --profile demo up -d mysql redis rabbitmq
   .\scripts\demo\start-demo.ps1
   ```

> 这些 `.ps1` 脚本不调用 Docker Compose 起应用层，只启动本机 Maven backend + Vite frontend。
