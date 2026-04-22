import fs from 'node:fs/promises'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { test, expect } from '@playwright/test'

const __filename = fileURLToPath(import.meta.url)
const __dirname = path.dirname(__filename)

const outputDir = path.resolve(__dirname, '../../output/playwright/demo')
const manifestPath = path.join(outputDir, 'manifest.md')
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

async function appendManifest(item: ManifestItem) {
  manifestItems.push(item)
  const lines = [
    '# Demo Screenshot Manifest',
    '',
    `生成时间：${new Date().toLocaleString('zh-CN', { hour12: false })}`,
    '',
    ...manifestItems.map((entry) => `- \`${entry.file}\` | ${entry.page} | ${entry.state}`),
    '',
  ]
  await fs.writeFile(manifestPath, lines.join('\n'), 'utf8')
}

async function capture(page: import('@playwright/test').Page, file: string, pageName: string, state: string) {
  await page.screenshot({
    path: path.join(outputDir, file),
    fullPage: true,
  })
  await appendManifest({ file, page: pageName, state })
}

async function resetDemo(request: import('@playwright/test').APIRequestContext) {
  const response = await request.post(demoResetUrl)
  expect(response.ok()).toBeTruthy()
}

async function createDummyPdf() {
  const pdfPath = path.join(outputDir, 'demo-resume.pdf')
  const content = `%PDF-1.4
1 0 obj
<< /Type /Catalog /Pages 2 0 R >>
endobj
2 0 obj
<< /Type /Pages /Count 1 /Kids [3 0 R] >>
endobj
3 0 obj
<< /Type /Page /Parent 2 0 R /MediaBox [0 0 300 144] /Contents 4 0 R /Resources << /Font << /F1 5 0 R >> >> >>
endobj
4 0 obj
<< /Length 63 >>
stream
BT
/F1 16 Tf
40 88 Td
(Demo resume placeholder) Tj
ET
endstream
endobj
5 0 obj
<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>
endobj
xref
0 6
0000000000 65535 f 
0000000010 00000 n 
0000000060 00000 n 
0000000117 00000 n 
0000000243 00000 n 
0000000357 00000 n 
trailer
<< /Root 1 0 R /Size 6 >>
startxref
430
%%EOF`
  await fs.writeFile(pdfPath, content, 'utf8')
  return pdfPath
}

async function login(page: import('@playwright/test').Page) {
  await page.goto('/login')
  await page.getByPlaceholder('请输入用户名').fill('demo')
  await page.getByPlaceholder('请输入密码').fill('123456')
  await page.getByRole('button', { name: '登录' }).first().click()
  await page.waitForURL('**/interview')
}

test('capture demo twin full-page screenshots', async ({ page, request }) => {
  await ensureOutputDir()

  await resetDemo(request)

  await page.goto('/login')
  await capture(page, '01-login.png', '登录页', '登录态空白')

  await page.getByRole('button', { name: '注册' }).click()
  await capture(page, '02-register.png', '注册页', '注册表单')

  await page.getByRole('button', { name: '登录' }).last().click()
  await login(page)

  await capture(page, '03-interview-empty.png', '主工作台', '空态')

  await page.goto('/resumes')
  await capture(page, '08-resumes-empty.png', '简历管理', '空态')

  await page.goto('/settings/llm')
  await capture(page, '10-settings-llm.png', 'LLM配置', '默认配置')

  await page.goto('/settings/profile')
  await capture(page, '11-settings-profile.png', '用户设置', '默认配置')

  await page.goto('/analytics')
  await capture(page, '12-analytics-empty.png', '数据看板', '空态')

  await page.goto('/interview')
  const pdfPath = await createDummyPdf()
  await page.locator('input[type="file"]').setInputFiles(pdfPath)
  await expect(page.getByText('demo-resume.pdf').first()).toBeVisible()
  await page.getByRole('button', { name: '创建面试' }).click()
  await expect(page.getByText(/会话 #\d+/)).toBeVisible()
  await capture(page, '04-interview-session-started.png', '主工作台', '已创建会话')

  await page.getByRole('button', { name: 'AI开始提问' }).click()
  await expect(page.getByText('面试官', { exact: true }).first()).toBeVisible()
  await page.getByPlaceholder('输入回答后发送').fill('我主要负责后端接口、流式问答和报告落库。')
  await page.getByRole('button', { name: '发送回答' }).click()
  await expect(page.getByText('我主要负责后端接口、流式问答和报告落库。')).toBeVisible()
  await page.getByRole('button', { name: '进入技术阶段' }).click()
  await expect(page.getByRole('button', { name: '进入深挖阶段' })).toBeVisible()
  await capture(page, '05-interview-stage-technical.png', '主工作台', '技术阶段')

  await page.getByRole('button', { name: '生成报告' }).click()
  await expect(page.getByRole('heading', { name: '面试评估报告' })).toBeVisible()
  await expect(page.getByText('技术能力：7/10')).toBeVisible()
  await capture(page, '06-interview-report.png', '主工作台', '报告已生成')

  await page.getByRole('button', { name: '回放' }).first().click()
  await page.waitForURL('**/interview/replay/**')
  await capture(page, '07-replay.png', '回放页', '完整回放')

  await page.goto('/resumes')
  await capture(page, '09-resumes-filled.png', '简历管理', '已有演示简历')

  await page.goto('/analytics')
  await expect(page.getByText('能力雷达')).toBeVisible()
  await capture(page, '13-analytics-filled.png', '数据看板', '已有演示数据')
})
