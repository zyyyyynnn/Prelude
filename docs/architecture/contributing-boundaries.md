# 架构贡献边界（后重写基线）

本文件约束**新代码与增量改动**，防止模块化单体回潮为上帝类或跨域直连。
与 `overview.md` 配套使用；违反时以架构测试 / sentrux 门禁为准。

---

## 1. 依赖方向

```text
api  →  application  →  domain
              ↑
        infrastructure
platform 可被 application 经 Port 调用；domain 禁止依赖 Spring / 他域 infrastructure
```

| 规则 | 说明 |
| --- | --- |
| domain 无 Spring | 禁止 `org.springframework`；现有 persistence-shaped 实体上的 MyBatis 注解属于存量例外，禁止扩散到策略和值对象 |
| application 不碰他域 infrastructure | 跨域只经 `*Port` |
| api 不碰 persistence mapper | controller 只调 application usecase |
| bootstrap 不进核心 application | fixture 经 FixturePort 注入 |

**过渡例外（已有代码可暂留，禁止扩大）：**

- `identity` / `catalog` application 内直连本域 Mapper。
- application 直接依赖本域 `api` DTO（应逐步改为 Command/Result，由 controller 映射）。

---

## 2. Port 放置

| 类型 | 推荐位置 |
| --- | --- |
| 本域仓储 / 出站 | `{domain}.application.port` |
| 跨域只读契约 | 提供方的 `{domain}.application.port`，**调用方只依赖接口** |
| 平台能力 | `platform.{llm,retrieval,job,realtime}` |

禁止在 controller 中 new 基础设施；禁止 application import 他域 `*.infrastructure.*`。

**存量例外：** `resume.api.port`、`catalog.api.port`、`interview.api.port` 暂不为移动而移动；
新增 Port 不再放入 `api.port`，触及既有 Port 时随用例改造迁入 `application.port`。

---

## 3. 用例形状

- 一个用户意图优先一个入口类（如 `StartInterview`、`RunInterviewTurn`、`FinishInterview`）。
- 文字 / 语音共享同一 turn 用例；transport 适配只做 IO。
- 新逻辑不要塞回“大 ServiceImpl”；support 类不对外暴露给 api。

**新增用例推荐签名（目标，非强制立刻全量改造）：**

```text
Result execute(Command cmd)
```

由 api 层完成 HTTP DTO ↔ Command/Result 映射。

---

## 4. 前端

| 规则 | 说明 |
| --- | --- |
| 新页面 / 组件 | 放在 `features/<domain>/` |
| 跨 feature | 禁止深 import 内部实现；经 api client 或显式共享模块 |
| 共享 UI | `components/ui`（DESIGN 体系） |
| 根 `api/*`、`composables/*` re-export | 仅兼容；新代码优先 `features/*` 与 `@/api/http`、`@/api/contracts` |
| UI 红线 | `DESIGN.md` + `verify:ui` / `tokens` / `a11y` |

---

## 5. 数据与异步

- 简历真源：`document_json`；面试只读投影文本。
- 报告派生分 / 薄弱点不变式见产品主链路文档，禁止 LLM 草稿覆盖派生字段。
- 异步副作用走 `JobSchedulerPort` + `async_job`，禁止在 usecase 内直接 `RabbitTemplate`（新代码）。

---

## 6. 改动检查清单

合并前自检：

- [ ] 未新增 controller → mapper 依赖
- [ ] 未新增 interview → resume/catalog infrastructure 依赖
- [ ] domain 文件无 Spring
- [ ] 新前端代码落在正确 feature
- [ ] `mvn test` 与相关 frontend verify 通过
- [ ] 若触及风险点，更新 `docs/quality/risk-register.md`

---

## 7. 下一轮优化方向（不在本文件执行）

1. application 与 api DTO 解耦（Command/Result）
2. insight domain 与 interview domain 实体解耦
3. 前端去掉长期 re-export、拆分超大组件
4. `LlmRouter` 按职责拆分

完成一项后更新 `overview.md` 与本文件中的「过渡例外」列表。
