# output 运行产物目录

本目录只存放可再生成产物，例如 Playwright 截图、manifest、临时运行记录和自动化输出。它不是源码事实来源，也不是论文正文事实来源。

## 当前约定

| 路径 | 说明 | 维护规则 |
| --- | --- | --- |
| `screenshots/dev/` | local/dev 截图和截图清单 | 由 `npm --prefix frontend run capture:local` 生成 |
| `runtime/` | 本机启动脚本日志 | 可删除、可再生成，不作为证据源 |

## 使用规则

- 自动化脚本生成的原始截图进入 `output/screenshots/dev/`。
- 确认适合长期展示的截图，再复制到 `docs/images/`。
- 确认进入论文或答辩证据链的截图，应登记到 `thesis-assets/meta/final-evidence-lock.md`。
- 临时调试日志、浏览器缓存、Playwright 中间产物不要提交。

## 推荐流程

```powershell
.\start-dev.bat
npm --prefix frontend run capture:local
Test-Path output/screenshots/dev/manifest.md
```
