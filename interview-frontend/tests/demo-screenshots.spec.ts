import fs from 'node:fs/promises'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { test, expect, type APIRequestContext, type Locator, type Page } from '@playwright/test'

const __filename = fileURLToPath(import.meta.url)
const __dirname = path.dirname(__filename)

const outputDir = path.resolve(__dirname, '../../output/demo/screenshots')
const manifestPath = path.resolve(__dirname, '../../output/demo/manifest.md')
const demoResetUrl = 'http://127.0.0.1:8081/api/demo/reset'
type ManifestItem = {
  file: string
  page: string
  state: string
}

const manifestItems: ManifestItem[] = []

async function ensureOutputDir() {
  await fs.mkdir(outputDir, { recursive: true })
}

async function ensureScreenshotMode(page: Page) {
  await page.evaluate(() => {
    const styleId = 'demo-capture-style'
    document.documentElement.setAttribute('data-demo-capture', 'true')

    if (!document.getElementById(styleId)) {
      const style = document.createElement('style')
      style.id = styleId
      style.textContent = `
        html[data-demo-capture='true'] body {
          background-attachment: scroll !important;
        }

        html[data-demo-capture='true'] .app-shell__header {
          position: static !important;
          top: auto !important;
        }
      `
      document.head.appendChild(style)
    }
  })
}

async function appendManifest(item: ManifestItem) {
  manifestItems.push(item)
  const lines = [
    '# Demo Screenshot Manifest',
    '',
    `生成时间：${new Date().toLocaleString('zh-CN', { hour12: false })}`,
    '',
    ...manifestItems
      .slice()
      .sort((left, right) => left.file.localeCompare(right.file, 'en'))
      .map((entry) => `- \`${entry.file}\` | ${entry.page} | ${entry.state}`),
    '',
  ]
  await fs.writeFile(manifestPath, lines.join('\n'), 'utf8')
}

async function waitForTransientUiToClear(page: Page) {
  await page.waitForFunction(() => !document.querySelector('.el-message.page-notice'), undefined, {
    timeout: 5000,
  }).catch(() => null)
  await page.waitForFunction(
    () => !document.querySelector('.el-loading-mask:not(.is-hidden)'),
    undefined,
    { timeout: 5000 },
  ).catch(() => null)
  await page.waitForTimeout(200)
}

async function capture(page: Page, file: string, pageName: string, state: string, readyLocator?: Locator) {
  await page.waitForLoadState('domcontentloaded')
  if (readyLocator) {
    await expect(readyLocator).toBeVisible()
  }
  await ensureScreenshotMode(page)
  await waitForTransientUiToClear(page)
  await page.screenshot({
    path: path.join(outputDir, file),
    fullPage: true,
  })
  await appendManifest({ file, page: pageName, state })
}

async function resetDemo(request: APIRequestContext) {
  const response = await request.post(demoResetUrl)
  expect(response.ok()).toBeTruthy()
}

async function login(page: Page) {
  await page.goto('/login')
  await page.getByPlaceholder('请输入用户名').fill('demo')
  await page.getByPlaceholder('请输入密码').fill('123456')
  await page.getByRole('button', { name: '登录' }).first().click()
  await page.waitForURL('**/interview')
}

async function sendAnswer(page: Page, content: string) {
  await page.getByPlaceholder('输入回答后发送').fill(content)
  await page.getByRole('button', { name: '发送回答' }).click()
  await expect(page.getByText(content)).toBeVisible()
  await waitForTransientUiToClear(page)
}

test('capture demo twin full-page screenshots', async ({ page, request }) => {
  await ensureOutputDir()

  await resetDemo(request)

  await page.goto('/login')
  await capture(page, '01-login.png', '登录页', '登录态空白', page.getByRole('button', { name: '登录' }).first())

  await page.getByRole('tab', { name: '注册' }).click()
  await capture(page, '02-register.png', '注册页', '注册表单', page.getByRole('button', { name: '完成注册' }))

  await page.getByRole('tab', { name: '登录' }).click()
  await login(page)

  const sessionBadge = page.locator('.panel--conversation .el-tag').filter({ hasText: /会话 #\d+/ }).first()
  await capture(page, '03-interview-workbench.png', '主工作台', '默认演示链路', sessionBadge)

  await page.goto('/resumes')
  await capture(page, '08-resumes-filled.png', '简历管理', '已有演示简历', page.getByText('demo-resume.pdf').first())

  await page.goto('/settings/llm')
  await capture(page, '09-settings-llm.png', 'LLM配置', '默认配置', page.getByRole('heading', { name: 'Provider 抽象层' }))

  await page.goto('/settings/profile')
  await capture(page, '10-settings-profile.png', '用户设置', '默认配置', page.getByRole('heading', { name: '用户设置' }))

  await page.goto('/interview')
  await expect(sessionBadge).toBeVisible()
  await expect(page.getByRole('button', { name: '进入深挖阶段' })).toBeVisible()
  await capture(page, '04-interview-stage-technical.png', '主工作台', '技术阶段', page.getByRole('button', { name: '进入深挖阶段' }))

  await sendAnswer(page, '这条链路我会先在 Controller 做登录态和参数校验，再进入 service 组装会话上下文。用户回答会先落一条 user 消息，随后通过 SSE 推送面试官回复，最后把 assistant 消息按序号落库，确保回放时顺序稳定。')

  await page.getByRole('button', { name: '进入深挖阶段' }).click()
  await expect(page.getByRole('button', { name: '进入收尾阶段' })).toBeVisible()
  await expect(page.getByText('SSE 输出过程中如果浏览器刷新了')).toBeVisible()
  await sendAnswer(page, '如果浏览器刷新，我会先让 emitter 的 timeout、error 和 completion 都走同一套清理逻辑，避免连接对象挂在内存里。已经生成但还没完整落库的内容，我会用会话状态和消息序号兜底，宁可重试生成，也不写半截消息。')
  await capture(page, '05-interview-stage-deep-dive.png', '主工作台', '深挖阶段', page.getByRole('button', { name: '进入收尾阶段' }))

  await page.getByRole('button', { name: '进入收尾阶段' }).click()
  await expect(page.getByRole('button', { name: '阶段已完成' })).toBeVisible()
  await expect(page.getByText('最后收个尾')).toBeVisible()
  await sendAnswer(page, '我会优先补评分解释，把每个扣分点关联到具体回答片段。这样用户不只是看到 7 分或 8 分，而是知道哪一句回答不够完整、下一次应该怎么改。')

  await page.getByRole('button', { name: '生成报告' }).click()
  await expect(page.getByRole('heading', { name: '面试评估报告' })).toBeVisible()
  await expect(page.getByText('技术能力：7/10')).toBeVisible()
  await capture(page, '06-interview-report.png', '主工作台', '报告已生成', page.getByRole('heading', { name: '面试评估报告' }))

  await page.getByRole('button', { name: '回放' }).first().click()
  await page.waitForURL('**/interview/replay/**')
  await expect(page.getByRole('heading', { name: '破冰', exact: true })).toBeVisible()
  await expect(page.getByRole('heading', { name: '技术', exact: true })).toBeVisible()
  await expect(page.getByRole('heading', { name: '深挖', exact: true })).toBeVisible()
  await expect(page.getByRole('heading', { name: '收尾', exact: true })).toBeVisible()
  await expect(page.locator('.replay-item .el-tag').filter({ hasText: '系统' }).first()).toBeVisible()
  await expect(page.locator('.replay-item .el-tag').filter({ hasText: '面试官' }).first()).toBeVisible()
  await expect(page.locator('.replay-item .el-tag').filter({ hasText: '我' }).first()).toBeVisible()
  await capture(page, '07-replay.png', '回放页', '完整回放', page.getByRole('heading', { name: '会话回放' }))

  await page.goto('/analytics')
  await expect(page.getByText('能力雷达')).toBeVisible()
  await capture(page, '11-analytics-filled.png', '数据看板', '已有演示数据', page.getByRole('heading', { name: '能力雷达' }))
})
