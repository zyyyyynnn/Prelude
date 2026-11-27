# Vite+ 与 Oxc 工具链

## Context

前端同时需要开发服务、格式、type-aware lint、类型检查、构建、测试与独立声明验证。多套工具链会漂移配置，并使 CI 与本地命令不一致。

## Decision

- `vite-plus` 是统一 CLI 与配置入口：`vp dev/check/build/preview/test`。
- Oxfmt 负责格式，Oxlint 负责 type-aware lint 与 React/shadcn 规则，tsrs 负责类型检查。
- Rolldown 与 Oxc 负责生产 transform、minify 与 isolated declarations；构建目标为 `es2023`。
- `@tailwindcss/vite` 与 `@vitejs/plugin-react` 通过 Vite+ `lazyPlugins` 装配。
- CI 使用 `voidzero-dev/setup-vp`、Node 24 与 `npm ci`，缓存 Vite task cache。
- `verify:oxc` 在真实 `main.tsx`、报告类型与 turn-buffer 上验证 parser/resolver/transform/declarations/minify。

## Consequences

本地与 CI 共享同一配置和 lockfile，工具升级作为一次依赖变更处理。生产声明生成要求导出函数显式标注返回类型；Oxc resolver 必须保持 tsconfig alias 可解析。格式或 lint 修复不能绕过类型与架构门禁。

## Validation

`npm --prefix frontend run check`、`npm --prefix frontend run verify:oxc`、`npm --prefix frontend run build`；CI 在 supply-chain 成功后按同一顺序执行前端检查。
