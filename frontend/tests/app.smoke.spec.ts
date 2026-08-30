import AxeBuilder from '@axe-core/playwright'
import { expect, test, type Page } from '@playwright/test'
import { installAnonymousSession } from './auth-bootstrap'

const providers = [
  {
    providerKey: 'deepseek',
    displayName: 'DeepSeek',
    availableModels: ['deepseek-v4-pro', 'deepseek-v4-flash'],
    enabled: 1,
  },
  {
    providerKey: 'openai-responses',
    displayName: 'OpenAI Responses',
    availableModels: ['gpt-5.4'],
    enabled: 1,
  },
  {
    providerKey: 'openai-chat-completions',
    displayName: 'OpenAI Chat Completions',
    availableModels: ['gpt-4.1'],
    enabled: 1,
  },
  {
    providerKey: 'anthropic-messages',
    displayName: 'Anthropic Messages',
    availableModels: ['claude-sonnet-4-6'],
    enabled: 1,
  },
]

async function installApi(page: Page) {
  await page.route(/^https?:\/\/[^/]+\/api\//, async (route) => {
    const path = new URL(route.request().url()).pathname
    let data: unknown = null
    if (path === '/api/interview/sessions')
      data = [
        {
          sessionId: 7,
          targetPosition: 'Java 后端工程师',
          status: 'ongoing',
          currentStage: 'warmup',
          llmProvider: 'deepseek',
          llmModel: 'deepseek-v4-pro',
        },
      ]
    else if (path === '/api/position/list') data = [{ id: 1, name: 'Java 后端工程师' }]
    else if (path === '/api/resume/list')
      data = [{ id: 1, fileName: '候选人简历.pdf', sessionCount: 2, inUse: false }]
    else if (path === '/api/auth/me') data = { accountId: 1, username: 'prelude' }
    else if (path === '/api/user/profile')
      data = {
        accountId: 1,
        username: 'prelude',
        email: 'prelude@example.com',
        themePreference: 'system',
        revision: 0,
      }
    else if (path === '/api/llm/providers') data = providers
    else if (path === '/api/llm/config')
      data = {
        providerKey: 'deepseek',
        baseUrl: null,
        model: 'deepseek-v4-pro',
        hasApiKey: false,
        apiKeyMasked: null,
        maxTokens: null,
        thinkingDepth: null,
      }
    else if (path === '/api/analytics/radar')
      data = { technical: 8, expression: 7.5, logic: 8.5, sessionCount: 5 }
    else if (path === '/api/analytics/trend')
      data = [
        {
          sessionId: 1,
          createdAt: '2026-08-28T08:00:00Z',
          technical: 8,
          expression: 7,
          logic: 9,
        },
      ]
    else if (path === '/api/analytics/weaknesses')
      data = [
        {
          category: '系统设计',
          count: 2,
          descriptions: ['容量估算需要更具体'],
        },
      ]
    else if (path === '/api/interview/start') data = { sessionId: 7, currentStage: 'warmup' }
    else if (path === '/api/interview/7/messages')
      data = {
        sessionId: 7,
        targetPosition: 'Java 后端工程师',
        status: 'ongoing',
        currentStage: 'warmup',
        summaryReport: null,
        stages: [],
        messages: [{ id: 1, role: 'assistant', content: '请先介绍一下你自己。' }],
        resumeId: 1,
        positionId: 1,
        llmThinkingDepth: null,
        attachments: [],
      }
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({ code: 200, message: 'ok', data }),
    })
  })
}

test('@smoke renders the React authentication entry', async ({ page }) => {
  const runtimeErrors: string[] = []
  page.on('pageerror', (error) => runtimeErrors.push(error.message))
  await installAnonymousSession(page)
  await page.goto('/')
  await expect(page).toHaveURL(/\/login$/)
  await expect(page.getByRole('heading', { level: 1, name: '进入面试工作台' })).toBeVisible()
  await expect(page.locator('#root')).toHaveCount(1)
  expect(runtimeErrors).toEqual([])
})

test('@smoke keeps the complete product routes operational', async ({ page }) => {
  await installApi(page)
  await page.goto('/interview')
  await expect(page.getByRole('heading', { name: '准备开始一场沉浸式模拟面试' })).toBeVisible()
  await selectContext(page, '选择简历', '候选人简历.pdf')
  await selectContext(page, '选择岗位', 'Java 后端工程师')
  await page.getByRole('button', { name: '开始面试' }).click()
  await expect(page).toHaveURL(/session=7/)
  await expect(page.getByText('请先介绍一下你自己。')).toBeVisible()
  await page.getByRole('button', { name: '设置' }).click()
  await page.getByRole('button', { name: '简历管理' }).click()
  await expect(page.getByRole('heading', { name: '候选人简历.pdf' })).toBeVisible()
  await page.keyboard.press('Escape')
  await expect(page.getByRole('dialog', { name: '全局设置' })).toBeHidden()
  await page.getByRole('link', { name: '数据看板' }).click()
  await expect(page.getByRole('heading', { name: '分数趋势' })).toBeVisible()
})

test('@byok exposes only the four governed provider protocols', async ({ page }) => {
  await installApi(page)
  await page.goto('/interview')
  await page.getByRole('button', { name: '设置' }).click()
  await page.getByRole('button', { name: '模型管理' }).click()
  const select = page.getByLabel('服务协议')
  await select.click()
  const options = page.getByRole('option')
  await expect(options).toHaveCount(4)
  await expect(options).toHaveText([
    'DeepSeek',
    'OpenAI Responses',
    'OpenAI Chat Completions',
    'Anthropic Messages',
  ])
})

test('@dark restores the governed dark theme before rendering', async ({ page }) => {
  await installApi(page)
  await page.addInitScript(() => localStorage.setItem('prelude-theme-preference', 'dark'))
  await page.goto('/interview')
  await expect(page.locator('html')).toHaveClass(/dark/)
  const colors = await page.evaluate(() => {
    const style = getComputedStyle(document.documentElement)
    return [style.getPropertyValue('--color-bg'), style.getPropertyValue('--color-text-primary')]
  })
  expect(colors.every((value) => value.trim().length > 0)).toBe(true)
})

test('@visual keeps no-data pages lightweight and typographically consistent', async ({ page }) => {
  await installApi(page)
  await page.route('**/api/analytics/radar', async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        code: 200,
        message: 'ok',
        data: { technical: 0, expression: 0, logic: 0, sessionCount: 0 },
      }),
    })
  })
  await page.goto('/analytics')
  const emptyState = page.locator('.workspace-page__content > .empty-state')
  await expect(emptyState).toBeVisible()
  await expect(emptyState.locator('svg')).toHaveCount(0)
  const presentation = await emptyState.evaluate((element) => {
    const style = getComputedStyle(element)
    return {
      usesSerif: style.fontFamily.includes('Lora'),
      borderStyle: style.borderStyle,
      boxShadow: style.boxShadow,
      backgroundColor: style.backgroundColor,
    }
  })
  expect(presentation).toEqual({
    usesSerif: true,
    borderStyle: 'none',
    boxShadow: 'none',
    backgroundColor: 'rgba(0, 0, 0, 0)',
  })
})

test('@a11y keeps the primary authenticated surface accessible', async ({ page }) => {
  await installApi(page)
  await page.goto('/interview')
  const results = await new AxeBuilder({ page })
    .withTags(['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa'])
    .analyze()
  const critical = results.violations.filter((violation) => violation.impact === 'critical')
  expect(critical).toEqual([])
})

test('@visual keeps the authentication hierarchy and primary action stable', async ({ page }) => {
  await installAnonymousSession(page)
  await page.goto('/login')
  const heading = page.getByRole('heading', { level: 1, name: '进入面试工作台' })
  await expect(heading).toBeVisible()
  await expect(page.locator('.login-card__header .eyebrow')).toHaveCount(0)
  await expect(page.getByLabel('邮箱')).toBeHidden()
  const loginGeometry = await page.locator('.login-card__form-panel').evaluate((panel) => {
    const headingElement = panel.querySelector<HTMLElement>('.page__title')!
    const form = panel.querySelector<HTMLElement>('.auth-form')!
    const button = panel.querySelector<HTMLElement>('.login-card__submit')!
    const password = panel.querySelector<HTMLElement>('#auth-password')!
    const emailPlaceholder = panel.querySelector<HTMLElement>('.auth-email-field')!
    return {
      headingFont: getComputedStyle(headingElement).fontFamily,
      bodyFont: getComputedStyle(document.body).fontFamily,
      buttonWidthRatio: button.getBoundingClientRect().width / form.getBoundingClientRect().width,
      buttonHeight: button.getBoundingClientRect().height,
      buttonTop: button.getBoundingClientRect().top,
      buttonGap: button.getBoundingClientRect().top - password.getBoundingClientRect().bottom,
      emailPlaceholderHeight: emailPlaceholder.getBoundingClientRect().height,
    }
  })
  expect(loginGeometry.headingFont).not.toBe(loginGeometry.bodyFont)
  expect(loginGeometry.buttonWidthRatio).toBeGreaterThanOrEqual(0.98)
  expect(loginGeometry.buttonHeight).toBeGreaterThanOrEqual(34)
  expect(loginGeometry.emailPlaceholderHeight).toBeGreaterThanOrEqual(50)
  expect(loginGeometry.buttonGap).toBeGreaterThan(loginGeometry.emailPlaceholderHeight + 32)
  await page.screenshot({ path: test.info().outputPath('login-desktop.png'), fullPage: true })

  await page.getByRole('button', { name: '注册', exact: true }).click()
  await expect(page.getByRole('heading', { level: 1, name: '创建工作台账号' })).toBeVisible()
  await expect(page.getByLabel('用户名')).toBeVisible()
  await expect(page.locator('#auth-password')).toBeVisible()
  await expect(page.getByLabel('邮箱')).toBeVisible()
  const registerSubmit = page.getByRole('button', { name: '完成注册' })
  await expect(registerSubmit).toBeVisible()
  const registerGeometry = await page.locator('.login-card__form-panel').evaluate((panel) => {
    const email = panel.querySelector<HTMLElement>('#auth-email')!
    const button = panel.querySelector<HTMLElement>('.login-card__submit')!
    const buttonRect = button.getBoundingClientRect()
    return {
      buttonTop: buttonRect.top,
      buttonGap: buttonRect.top - email.getBoundingClientRect().bottom,
    }
  })
  expect(registerGeometry.buttonGap).toBeGreaterThanOrEqual(28)
  expect(registerGeometry.buttonGap).toBeLessThanOrEqual(40)
  expect(Math.abs(registerGeometry.buttonTop - loginGeometry.buttonTop)).toBeLessThan(1)
  await page.evaluate(
    () => new Promise<void>((resolve) => requestAnimationFrame(() => requestAnimationFrame(() => resolve()))),
  )
  await page.screenshot({ path: test.info().outputPath('register-desktop.png'), fullPage: true })
})

test('@visual keeps the desktop layout stable and tooltip neutral', async ({ page }) => {
  await installApi(page)
  await page.goto('/interview')
  const promptBar = page.locator('[data-beautiful-ui="prompt-bar"]')
  await expect(promptBar).toBeVisible()
  const geometry = await promptBar.evaluate((element) => {
    const bar = element.getBoundingClientRect()
    const input = element.querySelector('.prompt-bar__input-area')?.getBoundingClientRect()
    const controls = element.querySelector('.prompt-bar__controls')?.getBoundingClientRect()
    return {
      width: bar.width,
      height: bar.height,
      inputAboveControls: Boolean(input && controls && input.bottom <= controls.top),
    }
  })
  expect(geometry.width).toBeGreaterThanOrEqual(720)
  expect(geometry.height).toBeLessThan(150)
  expect(geometry.inputAboveControls).toBe(true)
  await expect(promptBar).toHaveScreenshot('interview-prompt-bar.png', {
    animations: 'disabled',
  })
  const promptBorder = await page.locator('.prompt-bar__surface').evaluate((element) => {
    const before = getComputedStyle(element).borderColor
    element.querySelector<HTMLElement>('.prompt-bar__input')?.focus()
    return { before, after: getComputedStyle(element).borderColor }
  })
  expect(promptBorder.after).toBe(promptBorder.before)
  await page.getByRole('button', { name: '收起侧栏' }).hover()
  const tooltip = page.locator('.prelude-tooltip')
  await expect(tooltip).toBeVisible()
  await expect(tooltip).toHaveText('收起侧栏')
  expect((await tooltip.boundingBox())?.width ?? 0).toBeGreaterThan(48)
  const contrast = await tooltip.evaluate((element) => {
    const parse = (value: string) =>
      value
        .match(/[\d.]+/g)!
        .slice(0, 3)
        .map(Number)
    const luminance = (value: string) => {
      const [r, g, b] = parse(value).map((channel) => {
        const normalized = channel / 255
        return normalized <= 0.04045 ? normalized / 12.92 : ((normalized + 0.055) / 1.055) ** 2.4
      })
      return 0.2126 * r + 0.7152 * g + 0.0722 * b
    }
    const style = getComputedStyle(element)
    const light = Math.max(luminance(style.backgroundColor), luminance(style.color))
    const dark = Math.min(luminance(style.backgroundColor), luminance(style.color))
    return (light + 0.05) / (dark + 0.05)
  })
  expect(contrast).toBeGreaterThanOrEqual(7)
  const expandedSidebarWidth = await page
    .locator('.app-sidebar')
    .evaluate((sidebar) => sidebar.getBoundingClientRect().width)
  await page.getByRole('button', { name: '收起侧栏' }).click()
  await page.locator('.app-sidebar').evaluate(async (sidebar) => {
    await Promise.all(sidebar.getAnimations().map((animation) => animation.finished))
  })
  const collapsedSidebar = await page.locator('.app-sidebar').evaluate((sidebar) => ({
    width: Math.round(sidebar.getBoundingClientRect().width),
    iconsVisible: Array.from(sidebar.querySelectorAll<SVGElement>('.app-sidebar__btn > svg')).every(
      (icon) => icon.getBoundingClientRect().width > 0 && icon.getBoundingClientRect().height > 0,
    ),
    labelsHidden: Array.from(sidebar.querySelectorAll<HTMLElement>('.sidebar-label')).every(
      (label) => label.getBoundingClientRect().width === 0,
    ),
  }))
  expect(collapsedSidebar.width).toBeLessThan(expandedSidebarWidth)
  expect(collapsedSidebar.iconsVisible).toBe(true)
  expect(collapsedSidebar.labelsHidden).toBe(true)
  await page.getByRole('button', { name: '展开侧栏' }).click()
  await expectIconCentered(page.getByRole('button', { name: /模型：/ }))
  await page.screenshot({
    path: test.info().outputPath('interview-desktop.png'),
    fullPage: true,
  })
  await page.getByRole('button', { name: '添加面试上下文' }).click()
  const resumeMenuItem = page.getByRole('menuitem', { name: /选择简历/ })
  const positionMenuItem = page.getByRole('menuitem', { name: /选择岗位/ })
  const jdMenuItem = page.getByRole('menuitemcheckbox', { name: /JD 匹配/ })
  await expect(resumeMenuItem).toBeVisible()
  await expect(positionMenuItem).toBeVisible()
  await expect(jdMenuItem).toBeVisible()
  const contextMenuGeometry = await page.locator('.prelude-menu').first().evaluate((menu) => {
    const items = Array.from(menu.querySelectorAll<HTMLElement>('.prelude-menu__item'))
    const iconLefts = items.map((item) => item.querySelector<HTMLElement>('.prelude-menu__icon')?.getBoundingClientRect().left)
    const details = items
      .map((item) => item.querySelector<HTMLElement>('.prelude-menu__detail')?.getBoundingClientRect())
      .filter((box): box is DOMRect => Boolean(box))
    const statusesShareRow = items
      .map((item) => {
        const label = item.querySelector<HTMLElement>('.prelude-menu__label')
        const detail = item.querySelector<HTMLElement>('.prelude-menu__detail')
        if (!label || !detail) return true
        const labelBox = label.getBoundingClientRect()
        const detailBox = detail.getBoundingClientRect()
        return Math.abs(labelBox.top + labelBox.height / 2 - (detailBox.top + detailBox.height / 2)) < 1
      })
      .every(Boolean)
    return {
      iconColumnsAligned: iconLefts.every((left) => left !== undefined && Math.abs(left - iconLefts[0]!) < 1),
      statusColumnsAligned: details.every(
        (box) =>
          Math.abs(box.left - details[0].left) < 1 && Math.abs(box.right - details[0].right) < 1,
      ),
      statusesShareRow,
    }
  })
  expect(contextMenuGeometry).toEqual({
    iconColumnsAligned: true,
    statusColumnsAligned: true,
    statusesShareRow: true,
  })
  await settleOverlay(page.locator('.prelude-menu').first())
  await page.screenshot({
    path: test.info().outputPath('interview-context-menu.png'),
    fullPage: true,
  })
  await page.getByRole('menuitem', { name: /选择简历/ }).hover()
  const resumeOption = page.getByRole('menuitemradio', { name: '候选人简历.pdf' })
  await expect(resumeOption).toBeVisible()
  await expect(resumeOption.locator('.prelude-menu__indicator')).toHaveCount(0)
  const resumeOptionBox = await resumeOption.boundingBox()
  const resumeMenuItemBox = await resumeMenuItem.boundingBox()
  expect(resumeOptionBox).not.toBeNull()
  expect(resumeMenuItemBox).not.toBeNull()
  expect(Math.abs(resumeOptionBox!.height - resumeMenuItemBox!.height)).toBeLessThanOrEqual(1)
  await settleOverlay(page.locator('.prelude-menu').last())
  await page.screenshot({
    path: test.info().outputPath('interview-context-submenu.png'),
    fullPage: true,
  })
  await page.getByRole('menuitemradio', { name: '候选人简历.pdf' }).click()
  await expect(page.locator('.prelude-menu')).toHaveCount(0)
  await selectContext(page, '选择岗位', 'Java 后端工程师')
  const modelTrigger = page.getByRole('button', { name: /模型：/ })
  await expect(modelTrigger).toContainText('deepseek-v4-pro · 默认')
  await expect(modelTrigger).not.toContainText('DeepSeek')
  await expect(modelTrigger.locator('svg')).toHaveCount(1)
  await modelTrigger.screenshot({ path: test.info().outputPath('interview-model-trigger.png') })
  await modelTrigger.click()
  const modelMenu = page.locator('.prelude-menu--structured[data-open]')
  await expect(modelMenu).toBeVisible()
  await expect(page.getByRole('menuitem', { name: /服务协议/ })).toHaveCount(0)
  await expect(page.getByRole('menuitem', { name: /^模型\s/ })).toBeVisible()
  await expect(page.getByRole('menuitem', { name: /思考深度/ })).toBeVisible()
  await expect(page.getByRole('menuitem', { name: '管理模型' })).toBeVisible()
  const modelMenuGeometry = await modelMenu.evaluate((menu) => {
    const rows = Array.from(menu.querySelectorAll<HTMLElement>(':scope > [role="group"] > .prelude-menu__item'))
    const details = rows
      .map((row) => row.querySelector<HTMLElement>('.prelude-menu__detail')?.getBoundingClientRect())
      .filter((box): box is DOMRect => Boolean(box))
    return {
      detailColumnsAligned: details.every((box) => Math.abs(box.right - details[0].right) < 1),
      optionRowsUseThreeColumnGrid: rows.slice(0, 2).every(
        (row) => getComputedStyle(row).gridTemplateColumns.split(' ').length === 3,
      ),
      decorativeIconCount: menu.querySelectorAll('.prelude-menu__icon').length,
      manageIconCount: menu.querySelectorAll('.prelude-menu__manage-icon').length,
    }
  })
  expect(modelMenuGeometry).toEqual({
    detailColumnsAligned: true,
    optionRowsUseThreeColumnGrid: true,
    decorativeIconCount: 0,
    manageIconCount: 1,
  })
  await settleOverlay(modelMenu)
  await expect(modelMenu).toHaveScreenshot('interview-model-menu.png', {
    animations: 'disabled',
  })
  await page.screenshot({
    path: test.info().outputPath('interview-model-menu.png'),
    fullPage: true,
  })
  await page.keyboard.press('Escape')
  await page.getByRole('button', { name: '开始面试' }).click()
  await expect(page.getByLabel('面试回答')).toBeVisible()
  await expect(page.getByRole('button', { name: '切换到语音输入' })).toBeVisible()
  await expectIconCentered(page.getByRole('button', { name: '切换到语音输入' }), true)
  await expect(page.getByRole('button', { name: '发送' })).toBeDisabled()
  await page.screenshot({
    path: test.info().outputPath('interview-answer-desktop.png'),
    fullPage: true,
  })
})

test('@visual keeps settings navigation and select surfaces on the shared component contract', async ({
  page,
}) => {
  await installApi(page)
  await page.goto('/interview')
  await page.getByRole('button', { name: '设置' }).click()
  await expect(page.getByRole('heading', { name: '账号资料' })).toBeVisible()
  await expect(page.getByRole('heading', { name: '修改密码' })).toBeVisible()
  await expect(page.getByRole('button', { name: '保存设置' })).toBeVisible()
  await page.getByRole('button', { name: '简历管理' }).click()
  await expect(page.getByRole('heading', { name: '已上传简历' })).toBeVisible()
  await expect(
    page.locator('.settings-inline-actions--header').getByRole('button', { name: '上传简历' }),
  ).toBeVisible()
  await page.getByRole('button', { name: '岗位管理' }).click()
  await expect(
    page.locator('.settings-inline-actions--header').getByRole('button', { name: '创建岗位' }),
  ).toBeVisible()
  await expect(page.locator('.position-settings__item svg')).toHaveCount(0)
  const positionFields = await page.locator('.position-settings__fields').evaluate((container) => {
    const fields = Array.from(container.children).map((field) => field.getBoundingClientRect())
    return {
      sameWidth: Math.abs(fields[0].width - fields[1].width) < 1,
      stacked: fields[1].top > fields[0].bottom,
    }
  })
  expect(positionFields).toEqual({ sameWidth: true, stacked: true })
  await expect(page.getByRole('dialog', { name: '全局设置' })).toHaveScreenshot(
    'settings-position-dialog.png',
    { animations: 'disabled' },
  )
  await page.getByRole('button', { name: '模型管理' }).click()
  const modelSelect = page.getByLabel('模型', { exact: true })
  await expect(modelSelect).toHaveAttribute('role', 'combobox')
  await modelSelect.click()
  const modelOptions = page.getByRole('option')
  await expect(modelOptions.first()).toBeVisible()
  await page.keyboard.press('Escape')
  const providerSelect = page.getByLabel('服务协议')
  await providerSelect.click()
  const providerOptions = page.getByRole('option')
  await expect(providerOptions).toHaveCount(4)
  await settleOverlay(page.locator('.prelude-select-popup[data-open]'))
  await page.screenshot({
    path: test.info().outputPath('settings-select.png'),
    fullPage: true,
  })
})

async function selectContext(page: Page, menuLabel: string, option: string) {
  await page.getByRole('button', { name: '添加面试上下文' }).click()
  await page.getByRole('menuitem', { name: new RegExp(menuLabel) }).hover()
  await page.getByRole('menuitemradio', { name: option }).click()
}

async function expectIconCentered(
  button: ReturnType<Page['getByRole']>,
  centeredHorizontally = false,
) {
  const geometry = await button.evaluate((element) => {
    const box = element.getBoundingClientRect()
    const icon = element.querySelector('svg')?.getBoundingClientRect()
    return {
      iconVisible: Boolean(icon?.width && icon.height),
      centerXDelta: icon ? Math.abs(icon.left + icon.width / 2 - (box.left + box.width / 2)) : 99,
      centerYDelta: icon ? Math.abs(icon.top + icon.height / 2 - (box.top + box.height / 2)) : 99,
    }
  })
  expect(geometry.iconVisible).toBe(true)
  expect(geometry.centerYDelta).toBeLessThanOrEqual(1)
  if (centeredHorizontally) expect(geometry.centerXDelta).toBeLessThanOrEqual(1)
}

async function settleOverlay(overlay: ReturnType<Page['locator']>) {
  await overlay.evaluate(async (element) => {
    await Promise.all(element.getAnimations().map((animation) => animation.finished))
  })
}
