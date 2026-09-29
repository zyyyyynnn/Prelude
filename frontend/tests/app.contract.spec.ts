import { expect, test } from '@playwright/test'
import { createDemoState, DEMO_VIEWPORT, installDemoHarness } from './demo-harness'

test.use({ viewport: DEMO_VIEWPORT })

/**
 * The contract the resume workspace and the gallery owe each other: one trace per assistant
 * message, the file it wrote carried as a measured diff chip rather than repeated in the row
 * label, a failed step inside a run that still closed, and no context pickers that belong to
 * the interview composer only.
 */
test('@check lab tool catalog and resume composer', async ({ page }) => {
  const state = createDemoState()
  await installDemoHarness(page, state)
  await page.emulateMedia({ colorScheme: 'light', reducedMotion: 'reduce' })

  await page.goto('/components-lab')
  await expect(page.getByRole('heading', { name: 'Component Lab' })).toBeVisible()
  await expect(page.getByText('Tool Trace')).toBeVisible()
  await expect(
    page.locator('.tool-trace__text').filter({ hasText: '搜索网络' }).first(),
  ).toBeVisible()
  await expect(page.getByText('查找', { exact: true }).first()).toBeVisible()
  await expect(page.locator('.tool-trace__chip').filter({ hasText: '*.md' }).first()).toBeVisible()
  await expect(page.getByText('更新任务 1 项新建任务').first()).toBeVisible()
  await expect(page.getByRole('group', { name: '工具轨迹状态' })).toHaveCount(0)
  /* A finished run with a failed step discloses the count on the closing line — the
     aggregate copy never reports a bare 已完成 over a step that blew up. */
  await expect(page.getByText('已完成 · 8s · 1 项失败').first()).toBeVisible()
  const traces = page.locator('.tool-trace')
  await expect(traces).toHaveCount(2)
  const runningTrace = page.locator(
    '.tool-trace:has(.tool-trace__status[data-running="true"]:has-text("正在处理 · 37s"))',
  )
  await expect(runningTrace).toHaveCount(1)
  await expect(runningTrace.locator('.tool-trace__status')).toHaveAttribute('role', 'status')
  await expect(runningTrace.locator('.tool-trace__status .loading-pixel')).toHaveCount(9)
  const finishedTrace = page.locator(
    '.tool-trace:has(.tool-trace__status[data-running="false"]:has-text("已完成 · 8s"))',
  )
  await expect(finishedTrace).toHaveCount(1)
  await expect(finishedTrace.locator('.tool-trace__status .loading-pixel')).toHaveCount(0)
  const generatingCard = page.locator('.generating-card')
  await expect(generatingCard.locator('.rose-three-loader')).toHaveCount(1)
  await expect(generatingCard.locator('.generating-progress-track')).toHaveCount(0)

  await page.goto('/login')
  await page.getByLabel('用户名').fill('demo')
  await page.getByLabel('密码', { exact: true }).fill('123456')
  await page.locator('form').getByRole('button', { name: '登录', exact: true }).click()
  await expect(page).toHaveURL(/\/interview$/)
  await page.getByRole('button', { name: '简历', exact: true }).click()
  await expect(page).toHaveURL(/\/resume$/)
  /* The resume composer takes no interview context: no 简历 picker and no JD toggle. A control
     that cannot act on this workspace is worse than a missing one. */
  /* The resume composer carries a real file-upload affordance, not the interview
     resume/JD pickers. */
  await expect(page.getByRole('button', { name: '上传文件' })).toBeVisible()
  await expect(page.getByRole('button', { name: '添加简历上下文' })).toHaveCount(0)
  await expect(page.getByText('JD 匹配')).toHaveCount(0)
  await expect(page.getByText('deepseek-v4-pro · 默认')).toBeVisible()
  await expect(
    page.locator('[data-slot="prompt-bar-surface"] .prompt-bar-control-text svg'),
  ).toHaveCount(1)
  await expect(page.getByRole('button', { name: '切换到语音输入' })).toBeVisible()
  await expect(page.getByRole('button', { name: '发送' })).toBeVisible()
  await expect(page.getByPlaceholder('描述你希望如何修改这份简历…')).toBeVisible()

  await page.getByLabel('简历制作指令').fill('把工作经历改成量化导向，突出接口性能结果。')
  await page.getByRole('button', { name: '发送' }).click()
  await expect(page.getByText('简历制作助手')).toBeVisible()
  await expect(page.getByRole('button', { name: /思考 2轮/ })).toBeVisible()
  for (const tool of [
    '思考了 3s',
    '搜索网络',
    '查找',
    '改写工作经历',
    '更新任务 2 项新建任务',
    '写入',
    '执行',
  ]) {
    await expect(page.getByText(tool, { exact: true }).first()).toBeVisible()
  }
  /* Targets ride in the trailing column, on one left edge, never folded into the verb. */
  for (const target of ['接口性能指标写法', '*.md', 'resume-context', 'npm run check']) {
    await expect(page.locator('.tool-trace__chip').filter({ hasText: target })).toHaveCount(1)
  }

  // The write names its file once, in the chip, with the measured counts.
  await expect(page.locator('.tool-trace__file')).toHaveCount(1)
  await expect(page.locator('.tool-trace__file-name')).toHaveText('resume.md')
  await expect(page.locator('.tool-trace__file-add')).toHaveText('+22')
  await expect(page.locator('.tool-trace__file-del')).toHaveText('−10')
  /* A failed step stays on its own row and the run still closes — the group has no error state,
     so a red badge beside the closing line is the whole contract. */
  await expect(page.locator('.tool-trace__item[data-state="error"]')).toHaveCount(1)
  await expect(page.locator('.tool-trace__badge[data-tone="error"]')).toHaveText('退出码 1')
  /* The closing line carries the run's own duration, measured from the turn, and discloses
     the failed step — the row's words stay in their normal colour, the badge carries the red. */
  await expect(page.locator('.tool-trace__status')).toHaveText('已完成 · 8s · 1 项失败')
  await expect(page.locator('.tool-trace__status-failure')).toHaveCount(1)
  /* A finished run carries no loading mark — the running one does. */
  await expect(page.locator('.tool-trace__status svg')).toHaveCount(0)
  await expect(page.locator('.tool-trace__status .loading-pixel')).toHaveCount(0)
  /* Instruction and assistant message sit on inset cards; the tool group does not. */
  await expect(page.locator('.inset-card')).toHaveCount(2)
  await expect(page.locator('.tool-trace')).toHaveCount(1)
})
