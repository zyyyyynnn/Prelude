# UI 质量体系

`DESIGN.md` 是视觉与交互规范，`frontend/tokens/ui-tokens.json` 是 token 名称与分类索引，`frontend/src/shared/styles/index.css` 是 token 值和全局样式入口。

| 门禁 | 验证范围 |
| --- | --- |
| `npm run check` | TypeScript、ESLint、架构、UI 与 token 静态门禁 |
| `npm run verify:ui` | 颜色旁路、原生 Tooltip/Confirm 与交互动效禁用项 |
| `npm run verify:tokens` | token 声明完整性、锁定值与层级唯一性 |
| `npm run verify:production` | 生产产物不包含开发态组件检查面 |
| `npm run verify:byok` | 四种 provider DTO、参考数据与设置界面 |
| `npm run verify:dark` | 暗色偏好启动恢复 |
| `npm run verify:a11y` | 真实浏览器 Axe 检查 |
| `npm run verify:visual` | 桌面布局、Prompt Bar 多级菜单与 Tooltip 对比度 |

Tooltip 由 Base UI 提供交互行为，并使用高对比中性表面。页面和组件使用既有 Prelude token，不建立局部色板。
