# Demo Twin 操作手册

## 演示账号

默认演示账号：
```text
demo / 123456
```

## 重置演示数据

重置演示数据：
```powershell
pwsh -ExecutionPolicy Bypass -File .\scripts\demo\reset-demo.ps1
```

`/api/demo/reset` 会重建演示账号、默认 LLM 配置、演示简历、进行中会话、已完成会话、报告、评分历史和薄弱点数据。默认会话包含 1 场 `Java 后端工程师` 进行中会话，以及 `Java 后端工程师`、`前端工程师`、`算法工程师` 各 1 场已完成会话，避免演示清单只出现单一岗位。

## 生成截图

生成 Demo 截图：
```powershell
pwsh -ExecutionPolicy Bypass -File .\scripts\demo\capture-demo.ps1
```

截图输出目录：
```text
output\demo\screenshots
```

截图清单输出到：
```text
output\demo\manifest.md
```

## 答辩演示流程

1. 启动 Demo Twin：`.\start-demo.bat`
2. 登录演示账号：`demo / 123456`
3. 进入 `/interview` 查看进行中面试和多岗位历史会话
4. 进行对话并生成报告
5. 查看 `/analytics` 能力分析
