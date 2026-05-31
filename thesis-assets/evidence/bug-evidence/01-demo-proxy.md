# Bug 1 证据：Demo 登录代理 `ECONNREFUSED`

## 主讲定位

该问题适合作为论文或答辩主讲 Bug，用于说明 Demo Twin 与真实模式必须在端口、代理目标和启动脚本校验上保持明确隔离。

## 原始现象

Demo 前端启动后，登录接口 `/api/auth/login` 在 Vite 代理阶段失败，控制台出现：

```text
AggregateError [ECONNREFUSED]
18:22:21 [vite] http proxy error: /api/auth/login
```

## 根因

Demo 后端监听 `8081`，但 Demo 前端缺少专用环境变量时，Vite 会回退到默认代理目标 `http://localhost:8080`，导致 Demo 登录请求打向真实模式端口。

## 修复证据

Demo 前端环境文件固定为：

```text
VITE_PORT=5174
VITE_PROXY_TARGET=http://127.0.0.1:8081
```

当前准备阶段校验结果：

```text
pwsh -ExecutionPolicy Bypass -File .\scripts\demo\start-demo.ps1 -PrepareOnly
Demo runtime preparation complete.
```

当前 Demo 后端健康检查：

```text
GET http://127.0.0.1:8081/api/health
{"status":"ok"}
```

## 论文可用结论

该问题说明环境配置不是附属细节，而是系统可运行性的组成部分。通过把 `.env.demo` 纳入启动前校验，系统能够在准备阶段提前暴露代理目标错误，避免进入“页面可访问但登录失败”的半可用状态。
