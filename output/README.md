# output 运行产物目录

本目录用于存放可复现的 Demo 输出、截图清单和阶段性运行产物。它不是源码目录，也不是论文主材料目录。

## 当前内容

| 路径 | 说明 | 维护规则 |
| --- | --- | --- |
| `demo/` | Demo Twin 截图和截图清单 | 保留已确认可用于 README、论文或答辩的演示输出 |
| `demo/manifest.md` | Demo 截图清单 | 重新生成截图后同步更新 |

## 已忽略的运行产物

以下路径或文件类型不应提交，已由 `.gitignore` 控制：

```text
output/runtime/
output/demo/.artifacts/
output/playwright/
output/*.txt
```

## 与其他目录的边界

| 目录 | 职责 |
| --- | --- |
| `output/` | 自动化截图、Demo 输出、运行结果清单 |
| `docs/images/` | README 展示用精选截图 |
| `thesis-assets/` | 论文正式采用的证据、测试记录和答辩材料 |

## 使用规则

- 自动化脚本生成的原始截图先进入 `output/demo/`。
- 确认适合长期展示的截图，再复制到 `docs/images/`。
- 确认进入论文或答辩证据链的截图，应登记到 `thesis-assets/final-evidence-lock.md`。
- 临时调试日志、浏览器缓存、Playwright 中间产物不要提交。

## 推荐流程

```powershell
.\start-demo.bat
pwsh -ExecutionPolicy Bypass -File .\scripts\demo\reset-demo.ps1
pwsh -ExecutionPolicy Bypass -File .\scripts\demo\capture-demo.ps1
```

生成后检查：

```text
output/demo/manifest.md
output/demo/*.png
```

如果截图内容变化，需要同步判断是否更新：

```text
docs/images/
thesis-assets/final-evidence-lock.md
README.md
```
