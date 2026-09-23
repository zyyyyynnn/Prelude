import { expect, test, type Page } from '@playwright/test'
import { createDemoState, DEMO_VIEWPORT, installDemoHarness, type DemoState } from './demo-harness'

let state: DemoState

test.use({ viewport: DEMO_VIEWPORT, deviceScaleFactor: 1 })

test.beforeEach(async ({ page }) => {
  state = createDemoState()
  await installDemoHarness(page, state)
  await page.emulateMedia({ reducedMotion: 'reduce' })
  await page.goto('/login')
  await page.getByLabel('用户名').fill('demo')
  await page.getByLabel('密码', { exact: true }).fill('123456')
  await page.locator('form').getByRole('button', { name: '登录', exact: true }).click()
  await expect(page).toHaveURL(/\/interview$/)
})

async function openSettings(page: Page, section: string) {
  await page.getByRole('button', { name: '设置' }).click()
  const dialog = page.getByRole('dialog', { name: '全局设置' })
  await expect(dialog).toBeVisible()
  await dialog.getByRole('button', { name: section }).click()
  return dialog
}

function toast(page: Page, message: string) {
  return page.locator('[data-sonner-toast]').filter({ hasText: message })
}

function requested(method: string, path: string | RegExp) {
  return state.requests.filter(
    (item) =>
      item.method === method &&
      (typeof path === 'string' ? item.path === path : path.test(item.path)),
  )
}

/* `requested()` snapshots a list the page keeps appending to, so reading a positive count
   straight after a click is a race the local machine usually wins and CI loses — the avatar
   upload failed exactly that way. Await the count first, then read what was sent. Assertions
   that a request was *not* made stay synchronous: polling one would make them vacuous. */
async function sentRequests(method: string, path: string | RegExp, count: number) {
  await expect.poll(() => requested(method, path)).toHaveLength(count)
  return requested(method, path)
}

test('@smoke creates a custom position and lists it immediately', async ({ page }) => {
  const dialog = await openSettings(page, '岗位管理')
  await dialog.getByLabel('岗位名称').fill('可靠性工程师')
  await dialog.getByLabel('面试侧重点').fill('重点考察故障演练与容量规划')
  await dialog.getByRole('button', { name: '创建岗位' }).click()

  await expect(dialog.getByText('可靠性工程师')).toBeVisible()
  expect(requested('POST', '/api/position')).toHaveLength(1)
})

test('@smoke blocks an incomplete position before any request', async ({ page }) => {
  const dialog = await openSettings(page, '岗位管理')
  await dialog.getByLabel('岗位名称').fill('只填了名称')
  await dialog.getByRole('button', { name: '创建岗位' }).click()

  await expect(toast(page, '请填写岗位名称和面试侧重点')).toBeAttached()
  expect(requested('POST', '/api/position')).toHaveLength(0)
})

test('@smoke surfaces a duplicate position name from the backend', async ({ page }) => {
  const dialog = await openSettings(page, '岗位管理')
  await dialog.getByLabel('岗位名称').fill('平台工程师')
  await dialog.getByLabel('面试侧重点').fill('重复名称')
  await dialog.getByRole('button', { name: '创建岗位' }).click()

  await expect(toast(page, '同名岗位已存在')).toBeAttached()
})

test('@smoke keeps built-in positions read-only', async ({ page }) => {
  const dialog = await openSettings(page, '岗位管理')

  await expect(dialog.getByRole('button', { name: '编辑 Java 后端工程师' })).toHaveCount(0)
  await expect(dialog.getByRole('button', { name: '编辑 平台工程师' })).toHaveCount(1)
})

test('@smoke edits an existing custom position through the same form', async ({ page }) => {
  const dialog = await openSettings(page, '岗位管理')
  await dialog.getByRole('button', { name: '编辑 平台工程师' }).click()
  await expect(dialog.getByRole('heading', { name: '编辑岗位' })).toBeVisible()
  await dialog.getByLabel('岗位名称').fill('平台与效能工程师')
  await dialog.getByRole('button', { name: '保存岗位' }).click()

  await expect(dialog.getByText('平台与效能工程师')).toBeVisible()
  expect(requested('PUT', /^\/api\/position\/2$/)).toHaveLength(1)
})

test('@smoke deletes a custom position only after confirmation', async ({ page }) => {
  const dialog = await openSettings(page, '岗位管理')
  await dialog.getByRole('button', { name: '编辑 平台工程师' }).click()
  await dialog.getByRole('button', { name: '删除岗位' }).click()
  await page.getByRole('button', { name: '删除', exact: true }).click()

  await expect(dialog.getByText('平台工程师')).toHaveCount(0)
  expect(requested('DELETE', /^\/api\/position\/2$/)).toHaveLength(1)
})

test('@smoke appends an uploaded pdf resume', async ({ page }) => {
  const dialog = await openSettings(page, '简历管理')
  await dialog.locator('input[type="file"]').setInputFiles({
    name: '数据工程师简历.pdf',
    mimeType: 'application/pdf',
    buffer: Buffer.from('%PDF-1.4\n% prelude test fixture\n'),
  })

  await expect(dialog.getByText('数据工程师简历.pdf')).toBeVisible()
  expect(requested('POST', '/api/resume/upload')).toHaveLength(1)
})

test('@smoke rejects a non-pdf resume locally without uploading it', async ({ page }) => {
  const dialog = await openSettings(page, '简历管理')
  await dialog.locator('input[type="file"]').setInputFiles({
    name: 'notes.txt',
    mimeType: 'text/plain',
    buffer: Buffer.from('not a pdf'),
  })

  await expect(toast(page, '仅支持 PDF 简历')).toBeAttached()
  expect(requested('POST', '/api/resume/upload')).toHaveLength(0)
  await expect(dialog.getByText('notes.txt')).toHaveCount(0)
})

test('@smoke surfaces a backend parse failure on an uploaded resume', async ({ page }) => {
  const dialog = await openSettings(page, '简历管理')
  await dialog.locator('input[type="file"]').setInputFiles({
    name: 'broken-scan.pdf',
    mimeType: 'application/pdf',
    buffer: Buffer.from('%PDF-1.4\n% scanned image without text layer\n'),
  })

  await expect(toast(page, 'PDF 文本提取失败，请检查文件格式')).toBeAttached()
  expect(requested('POST', '/api/resume/upload')).toHaveLength(1)
  await expect(dialog.getByText('broken-scan.pdf')).toHaveCount(0)
})

test('@smoke saves profile changes against the current revision', async ({ page }) => {
  const dialog = await openSettings(page, '账号资料')
  await dialog.getByLabel('用户名').fill('demo-renamed')
  await dialog.getByRole('button', { name: '保存设置' }).click()

  const [write] = await sentRequests('PUT', '/api/user/profile', 1)
  expect(write.body).toMatchObject({ username: 'demo-renamed', expectedRevision: 0 })
  await expect.poll(() => state.profile.revision).toBe(1)
})

test('@smoke persists the theme preference and applies it before the save lands', async ({
  page,
}) => {
  const dialog = await openSettings(page, '主题')
  const dark = dialog.getByRole('radio', { name: /暗色/ })
  await expect(dark).toHaveAttribute('aria-checked', 'false')

  await dark.click()
  await expect(dark).toHaveAttribute('aria-checked', 'true')
  await expect(page.locator('html')).toHaveClass(/\bdark\b/)

  await dialog.getByRole('button', { name: '保存主题' }).click()
  const [write] = await sentRequests('PUT', '/api/user/profile', 1)
  expect(write.body).toMatchObject({ themePreference: 'dark' })
  await expect(toast(page, '主题已保存')).toBeAttached()
})

test('@smoke reveals the password fields only while asked', async ({ page }) => {
  const dialog = await openSettings(page, '账号资料')
  const current = dialog.getByLabel('旧密码')
  const toggle = dialog.locator('[data-slot="field-actions"]:has(#oldPassword) button')
  await expect(current).toHaveAttribute('type', 'password')

  await toggle.click()
  await expect(current).toHaveAttribute('type', 'text')

  await toggle.click()
  await expect(current).toHaveAttribute('type', 'password')
})

test('@smoke uploads an avatar and keeps the returned profile', async ({ page }) => {
  const dialog = await openSettings(page, '账号资料')
  await dialog.locator('#avatar-upload').setInputFiles({
    name: '头像.png',
    mimeType: 'image/png',
    buffer: Buffer.from('89504e470d0a1a0a', 'hex'),
  })

  await sentRequests('POST', '/api/user/avatar', 1)
  await expect(toast(page, '头像已更新')).toBeAttached()
})

test('@smoke deletes a resume only behind the confirmation', async ({ page }) => {
  const dialog = await openSettings(page, '简历管理')
  const row = dialog.getByText('数据工程师简历.pdf')
  await expect(row).toBeVisible()

  await dialog.getByRole('button', { name: '删除 数据工程师简历.pdf' }).click()
  // The confirm sheet is modal, so the row is hidden behind it rather than gone: the thing
  // worth locking is that nothing has been deleted yet.
  expect(requested('DELETE', /^\/api\/resume\//)).toHaveLength(0)

  await page.getByRole('button', { name: '删除', exact: true }).click()
  await sentRequests('DELETE', '/api/resume/4', 1)
  await expect(toast(page, '简历已删除')).toBeAttached()
  await expect(row).toHaveCount(0)
})
