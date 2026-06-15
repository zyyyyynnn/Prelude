# 运行模式说明

项目提供三种入口。推荐使用 `start-dev.bat` 用于日常开发、人工验收和答辩演示（支持 Vite HMR 热重载）。

## 1. start-dev.bat（推荐）

适用：日常开发调试、功能演示、答辩。

特点：利用 Docker 管理底层中间件（MySQL、Redis、RabbitMQ），而应用程序（Spring Boot 后端和 Vite 前端）直接在本机原生运行。修改前端代码（Vue/CSS）可以即时通过 HMR 看到效果。

入口：`.\start-dev.bat`

底层执行流程：
1. 启动中间件：`docker compose up -d mysql redis rabbitmq`
2. 启动本机后端：`mvn spring-boot:run`
3. 启动本机前端：`npm run dev`

## 2. start-docker.bat（可选）

适用：部署验证、容器化交付验证。

特点：全栈容器化，开箱即用，无需本机安装 Java / Maven / Node。**注意：前端在容器内是以 build 后的 nginx 静态产物运行的，修改前端代码需要 rebuild/restart 才能生效，不支持 HMR 热重载。**

入口：`.\start-docker.bat`

底层执行（等价手动命令）：
```powershell
docker compose --profile app up -d --build
```

## 3. Dev scripts（源码级调试）

适用：需要利用 `.ps1` 脚本进行极细粒度的阶段启动与日志拦截。

入口：
- `scripts/dev/start-dev.ps1`

详见 [scripts/dev/README.md](../scripts/dev/README.md)。

---

## 端口与中间件隔离

中间件由 Docker Compose 统一管理。为避免与本机原生端口冲突，宿主机暴露的默认端口已刻意避开默认值：

| 中间件   | 宿主机端口（可在 `.env` 覆盖） |
| :------ | :------------------------------ |
| MySQL   | 13306                           |
| Redis   | 16379                           |
| RabbitMQ | 5672 / 15672                    |



## 不推荐的方式

- 不建议同时运行本机 MySQL84 / Redis / RabbitMQ 系统服务，以免端口冲突。
- 不要占用本机 `3306` / `6379` / `5672`，以免引发端口冲突。Docker runtime 已自带所需中间件，dev mode 也会默认连接 Docker 暴露的中间件端口。


## Docker Desktop 视图提示

- `start-dev.bat` 下，Docker Desktop 只会显示 mysql、redis 和 rabbitmq 等中间件容器。
- `start-docker.bat` 下，才会显示 backend / frontend 应用容器。
