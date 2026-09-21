import AxeBuilder from '@axe-core/playwright'
import { expect, test, type Locator, type Page } from '@playwright/test'
import { installAnonymousSession } from './auth-bootstrap'
import { sampleReport } from '../src/app/lab/samples'

const schemes = ['light', 'dark'] as const
type Scheme = (typeof schemes)[number]

const deepSeekCapability = (model = 'deepseek-v4-pro') => ({
  provider: 'deepseek',
  model,
  reasoning: true,
  structuredOutput: true,
  toolCalling: true,
  streaming: true,
  vision: false,
  multilingual: true,
  longContext: true,
  embedding: false,
  nativeRealtimeVoice: false,
  supportedReasoningLevels: ['AUTO', 'LOW', 'HIGH', 'MAX'],
})

const providers = [
  {
    providerKey: 'deepseek',
    displayName: 'DeepSeek',
    customEndpoint: false,
    models: [deepSeekCapability(), deepSeekCapability('deepseek-v4-flash')],
  },
  {
    providerKey: 'openai-responses',
    displayName: 'OpenAI Responses',
    customEndpoint: true,
    models: [],
  },
  {
    providerKey: 'openai-chat-completions',
    displayName: 'OpenAI Chat Completions',
    customEndpoint: true,
    models: [],
  },
  {
    providerKey: 'anthropic-messages',
    displayName: 'Anthropic Messages',
    customEndpoint: true,
    models: [],
  },
]

/* The dark preference is written here and nowhere else. Product surfaces used to each repeat
   this line, and five of them dropped the `scheme` parameter instead — their "dark" baselines
   came out byte-identical to the light ones and gated nothing. */
async function preferScheme(page: Page, scheme: Scheme) {
  if (scheme === 'dark') {
    await page.addInitScript(() => localStorage.setItem('prelude-theme-preference', 'dark'))
  }
}

/* Asserts what the page rendered, not what storage holds: the theme class is applied by an
   effect after the first paint, so a one-shot read would race it. */
async function expectScheme(page: Page, scheme: Scheme) {
  const root = page.locator('html')
  if (scheme === 'dark') await expect(root).toHaveClass(/\bdark\b/)
  else await expect(root).not.toHaveClass(/\bdark\b/)
}

/* Resolved values, not declarations: whether the dark block wins is a cascade question, and
   that is exactly what a theme gate has to observe. */
function readPalette(page: Page) {
  return page.evaluate(() => {
    const style = getComputedStyle(document.documentElement)
    return ['--color-bg', '--color-text-primary', '--color-border'].map((name) =>
      style.getPropertyValue(name).trim(),
    )
  })
}

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
        provider: 'deepseek',
        model: 'deepseek-v4-pro',
        customEndpointUrl: null,
        hasApiKey: false,
        apiKeyMasked: null,
        reasoningLevel: 'AUTO',
        maxOutputTokens: 4096,
        fallbackModels: [],
        capability: deepSeekCapability(),
      }
    else if (path === '/api/analytics/radar')
      data = { technical: 8, expression: 7, logic: 9, sessionCount: 1 }
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
  await expect(page.getByText('最近 1 场均分')).toHaveCount(3)
})

test('@byok exposes only the four governed provider protocols', async ({ page }) => {
  await installApi(page)
  await page.goto('/interview')
  await page.getByRole('button', { name: '设置' }).click()
  await page.getByRole('button', { name: '模型管理' }).click()
  const select = page.getByLabel('接入方式')
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
  await preferScheme(page, 'dark')
  /* Records which happened first: the `dark` class landing, or React painting into #root.
     `initializeTheme()` runs before `createRoot().render()` in `app/main.tsx`, so the theme
     must win — that ordering is the whole reason the title says "before rendering". */
  await page.addInitScript(() => {
    const order: { dark: number; painted: number } = { dark: 0, painted: 0 }
    let tick = 0
    ;(window as unknown as Record<string, unknown>).__themeOrder = order
    /* Observe `document`, not `document.documentElement`: an init script runs before the
       parser has produced any element, so the root does not exist yet at this point. */
    new MutationObserver(() => {
      if (document.documentElement?.classList.contains('dark') && !order.dark) order.dark = ++tick
    }).observe(document, { attributes: true, subtree: true, attributeFilter: ['class'] })
    new MutationObserver(() => {
      if (document.getElementById('root')?.childElementCount && !order.painted) {
        order.painted = ++tick
      }
    }).observe(document, { childList: true, subtree: true })
  })
  await page.goto('/interview')
  await expectScheme(page, 'dark')
  await expect
    .poll(() =>
      page.evaluate(
        () => (window as unknown as Record<string, { dark: number; painted: number }>).__themeOrder,
      ),
    )
    .toEqual({ dark: 1, painted: 2 })
})

/* The stored preference stays at its `system` default here, so the OS media query decides the
   scheme and one page can produce both halves of the comparison — registering a second
   `preferScheme` would leave the first init script in place and fight it on reload. */
test('@dark resolves a different palette than the light scheme', async ({ page }) => {
  await installApi(page)
  await page.emulateMedia({ colorScheme: 'light' })
  await page.goto('/interview')
  await expectScheme(page, 'light')
  const light = await readPalette(page)

  await page.emulateMedia({ colorScheme: 'dark' })
  await page.reload()
  await expectScheme(page, 'dark')
  /* Non-empty is not a judgement: `--color-bg` and `--color-text-primary` are defined in the
     light scheme too, so the assertion this replaces stayed green with the dark override
     deleted outright. */
  expect(await readPalette(page)).not.toEqual(light)
})

test('@dark suppresses transitions while applying theme changes', async ({ page }) => {
  await installApi(page)
  await page.goto('/interview')
  await page.getByRole('button', { name: '设置' }).click()
  await page.getByRole('button', { name: '主题' }).click()
  await page.evaluate(() => {
    window.addEventListener(
      'prelude-theme-change',
      () => {
        document.documentElement.dataset.themeGuardObserved = String(
          document.documentElement.classList.contains('is-theme-transitioning'),
        )
      },
      { once: true },
    )
  })
  await page.getByRole('radio', { name: /暗色/ }).click()
  await expect(page.locator('html')).toHaveClass(/dark/)
  await expect(page.locator('html')).toHaveAttribute('data-theme-guard-observed', 'true')
  await page.evaluate(
    () =>
      new Promise<void>((resolve) =>
        requestAnimationFrame(() =>
          requestAnimationFrame(() => requestAnimationFrame(() => resolve())),
        ),
      ),
  )
  await expect(page.locator('html')).not.toHaveClass(/is-theme-transitioning/)
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
  /* `toHaveCSS` re-resolves the locator on each retry. A one-shot `evaluate` holds a handle
     that React detaches while the dashboard's other queries land, and a detached element
     answers `getComputedStyle` with an empty style — which reads back as "no border, no
     shadow, not serif" and only failed on the slower browser. */
  await expect(emptyState).toHaveCSS('font-family', /Lora/)
  await expect(emptyState).toHaveCSS('border-style', 'none')
  await expect(emptyState).toHaveCSS('box-shadow', 'none')
  await expect(emptyState).toHaveCSS('background-color', 'rgba(0, 0, 0, 0)')
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
    const headingElement = panel.querySelector<HTMLElement>('#auth-title')!
    const form = panel.querySelector<HTMLElement>('[data-slot="auth-form"]')!
    const button = panel.querySelector<HTMLElement>('button[type="submit"]')!
    const password = panel.querySelector<HTMLElement>('#auth-password')!
    const emailPlaceholder = panel.querySelector<HTMLElement>('.auth-email-field')!
    /* A trailing field action has to be contained by the control it belongs to: the
       wrapper is what gives it a box, and the control's trailing padding is what keeps
       the text out from under it. Both vanish if the layout utility stops being emitted. */
    const action = panel.querySelector<HTMLElement>('[data-slot="field-actions"] button')!
    const actionBox = action.getBoundingClientRect()
    const passwordBox = password.getBoundingClientRect()
    return {
      headingFont: getComputedStyle(headingElement).fontFamily,
      bodyFont: getComputedStyle(document.body).fontFamily,
      buttonWidthRatio: button.getBoundingClientRect().width / form.getBoundingClientRect().width,
      buttonHeight: button.getBoundingClientRect().height,
      controlHeight: Number.parseFloat(
        getComputedStyle(button).getPropertyValue('--ui-height-control'),
      ),
      buttonTop: button.getBoundingClientRect().top,
      buttonGap: button.getBoundingClientRect().top - password.getBoundingClientRect().bottom,
      emailPlaceholderHeight: emailPlaceholder.getBoundingClientRect().height,
      actionInsideField:
        actionBox.top >= passwordBox.top - 0.5 &&
        actionBox.bottom <= passwordBox.bottom + 0.5 &&
        actionBox.left >= passwordBox.left &&
        actionBox.right <= passwordBox.right + 1,
      actionCentredVertically:
        Math.abs(
          actionBox.top + actionBox.height / 2 - (passwordBox.top + passwordBox.height / 2),
        ) <= 0.5,
      textClearsAction:
        Number.parseFloat(getComputedStyle(password).paddingInlineEnd) >= actionBox.width,
    }
  })
  expect(loginGeometry.headingFont).not.toBe(loginGeometry.bodyFont)
  expect(loginGeometry.buttonWidthRatio).toBeGreaterThanOrEqual(0.98)
  expect(loginGeometry.buttonHeight).toBeCloseTo(loginGeometry.controlHeight, 0)
  expect(loginGeometry.emailPlaceholderHeight).toBeGreaterThanOrEqual(50)
  expect(loginGeometry.buttonGap).toBeGreaterThan(loginGeometry.emailPlaceholderHeight + 32)
  expect(loginGeometry.actionInsideField).toBe(true)
  expect(loginGeometry.actionCentredVertically).toBe(true)
  expect(loginGeometry.textClearsAction).toBe(true)
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
    const button = panel.querySelector<HTMLElement>('button[type="submit"]')!
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
    () =>
      new Promise<void>((resolve) =>
        requestAnimationFrame(() => requestAnimationFrame(() => resolve())),
      ),
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
    const input = element
      .querySelector('[data-slot="prompt-bar-input-area"]')
      ?.getBoundingClientRect()
    const controls = element
      .querySelector('[data-slot="prompt-bar-controls"]')
      ?.getBoundingClientRect()
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
  const promptBorder = await page
    .locator('[data-slot="prompt-bar-surface"]')
    .evaluate((element) => {
      const before = getComputedStyle(element).borderColor
      element.querySelector<HTMLElement>('[data-slot="prompt-bar-input"]')?.focus()
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
  const collapsingWidth = await page.locator('.app-sidebar').evaluate(async (sidebar) => {
    await new Promise<void>((resolve) => requestAnimationFrame(() => resolve()))
    await new Promise((resolve) => setTimeout(resolve, 40))
    return sidebar.getBoundingClientRect().width
  })
  await page.locator('.app-sidebar').evaluate(async (sidebar) => {
    await Promise.all(
      sidebar.getAnimations({ subtree: true }).map((animation) => animation.finished),
    )
  })
  /* Geometry closure: the collapsed rail is sized *by* the row it has to contain, so
     every number here is read from the tokens rather than written down. A container that
     stops matching its own row geometry fails here instead of in a screenshot review. */
  const collapsedRail = await page.locator('.app-sidebar').evaluate((sidebar) => {
    const token = (name: string) =>
      Number.parseFloat(getComputedStyle(sidebar).getPropertyValue(name))
    const action = sidebar.querySelector<HTMLElement>('.sidebar-action-primary')!
    const glyph = action.querySelector<SVGElement>('svg')!
    const toggle = sidebar.querySelector<HTMLElement>('.sidebar-toggle')!
    const frame = sidebar.querySelector<HTMLElement>('.sidebar-frame')!
    const actionBox = action.getBoundingClientRect()
    const glyphBox = glyph.getBoundingClientRect()
    const toggleBox = toggle.getBoundingClientRect()
    const labels = Array.from(sidebar.querySelectorAll<HTMLElement>('[data-sidebar-label]'))
    return {
      control: token('--ui-height-control'),
      glyphSize: token('--ui-glyph-md'),
      border: token('--border-width-default'),
      gutter: token('--spacing-sm'),
      frameWidth: frame.getBoundingClientRect().width,
      actionWidth: actionBox.width,
      actionHeight: actionBox.height,
      toggleWidth: toggleBox.width,
      toggleHeight: toggleBox.height,
      glyphLeft: glyphBox.x - actionBox.x,
      glyphRight: actionBox.x + actionBox.width - (glyphBox.x + glyphBox.width),
      iconsVisible: Array.from(sidebar.querySelectorAll<SVGElement>('.sidebar-action > svg')).every(
        (icon) => icon.getBoundingClientRect().width > 0 && icon.getBoundingClientRect().height > 0,
      ),
      labelsHidden: labels.every((label) => {
        const style = getComputedStyle(label)
        return style.visibility === 'hidden' && style.opacity === '0'
      }),
    }
  })
  expect(collapsingWidth).toBeLessThan(expandedSidebarWidth)
  expect(collapsingWidth).toBeGreaterThan(collapsedRail.frameWidth)
  expect(collapsedRail.frameWidth).toBeCloseTo(
    collapsedRail.control + collapsedRail.gutter * 2 + collapsedRail.border,
    0,
  )
  expect(collapsedRail.actionWidth).toBeCloseTo(collapsedRail.control, 0)
  expect(collapsedRail.actionHeight).toBeCloseTo(collapsedRail.control, 0)
  expect(collapsedRail.toggleWidth).toBeCloseTo(collapsedRail.control, 0)
  expect(collapsedRail.toggleHeight).toBeCloseTo(collapsedRail.control, 0)
  expect(collapsedRail.glyphSize).toBeGreaterThan(0)
  expect(collapsedRail.glyphLeft).toBeCloseTo(collapsedRail.glyphRight, 0)
  expect(collapsedRail.iconsVisible).toBe(true)
  expect(collapsedRail.labelsHidden).toBe(true)
  await page.getByRole('button', { name: '展开侧栏' }).click()
  await page.locator('.app-sidebar').evaluate(async (sidebar) => {
    await Promise.all(
      sidebar.getAnimations({ subtree: true }).map((animation) => animation.finished),
    )
  })
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
  const contextMenuGeometry = await page
    .locator('.prelude-menu')
    .first()
    .evaluate((menu) => {
      const items = Array.from(menu.querySelectorAll<HTMLElement>('.prelude-menu__item'))
      const iconLefts = items.map(
        (item) =>
          item.querySelector<HTMLElement>('.prelude-menu__icon')?.getBoundingClientRect().left,
      )
      const details = items
        .map((item) =>
          item.querySelector<HTMLElement>('.prelude-menu__detail')?.getBoundingClientRect(),
        )
        .filter((box): box is DOMRect => Boolean(box))
      const statusesShareRow = items
        .map((item) => {
          const label = item.querySelector<HTMLElement>('.prelude-menu__label')
          const detail = item.querySelector<HTMLElement>('.prelude-menu__detail')
          if (!label || !detail) return true
          const labelBox = label.getBoundingClientRect()
          const detailBox = detail.getBoundingClientRect()
          return (
            Math.abs(labelBox.top + labelBox.height / 2 - (detailBox.top + detailBox.height / 2)) <
            1
          )
        })
        .every(Boolean)
      return {
        iconColumnsAligned: iconLefts.every(
          (left) => left !== undefined && Math.abs(left - iconLefts[0]!) < 1,
        ),
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
  await expect(page.getByRole('menuitem', { name: /接入方式/ })).toHaveCount(0)
  await expect(page.getByRole('menuitem', { name: /^模型\s/ })).toBeVisible()
  await expect(page.getByRole('menuitem', { name: /思考深度/ })).toBeVisible()
  await expect(page.getByRole('menuitem', { name: '管理模型' })).toBeVisible()
  const modelMenuGeometry = await modelMenu.evaluate((menu) => {
    const rows = Array.from(
      menu.querySelectorAll<HTMLElement>(':scope > [role="group"] > .prelude-menu__item'),
    )
    const details = rows
      .map((row) =>
        row.querySelector<HTMLElement>('.prelude-menu__detail')?.getBoundingClientRect(),
      )
      .filter((box): box is DOMRect => Boolean(box))
    return {
      detailColumnsAligned: details.every((box) => Math.abs(box.right - details[0].right) < 1),
      optionRowsUseThreeColumnGrid: rows
        .slice(0, 2)
        .every((row) => getComputedStyle(row).gridTemplateColumns.split(' ').length === 3),
      decorativeIconCount: menu.querySelectorAll('.prelude-menu__icon').length,
      manageIconCount: menu.querySelectorAll('.prelude-menu__icon--leading').length,
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
    page.locator('[data-slot="panel-actions"]').getByRole('button', { name: '上传简历' }),
  ).toBeVisible()
  await expect(page.locator('[data-slot="resume-row-main"] > svg')).toHaveCount(0)
  const resumePadding = await page
    .locator('[data-slot="resume-row"]')
    .first()
    .evaluate((row) => {
      const style = getComputedStyle(row)
      return [style.paddingInlineStart, style.paddingInlineEnd]
    })
  expect(resumePadding[0]).toBe(resumePadding[1])
  await page.getByRole('button', { name: '岗位管理' }).click()
  await expect(
    page.locator('[data-slot="panel-actions"]').getByRole('button', { name: '创建岗位' }),
  ).toBeVisible()
  await expect(page.locator('[data-slot="position-catalog"] svg')).toHaveCount(0)
  const positionWorkspace = await page
    .locator('[data-slot="position-workspace"]')
    .evaluate((workspace) => {
      const [catalog, form] = Array.from(workspace.children)
      const catalogRect = catalog.getBoundingClientRect()
      const formRect = form.getBoundingClientRect()
      const catalogStyle = getComputedStyle(catalog)
      const formStyle = getComputedStyle(form)
      const catalogTitle = catalog.querySelector<HTMLElement>('[data-slot="panel-heading"]')!
      const firstItem = catalog.querySelector<HTMLElement>('[data-slot="position-item-name"]')!
      return {
        alignedTop: Math.abs(catalogRect.top - formRect.top) < 1,
        sideBySide: formRect.left > catalogRect.right,
        matchingPadding: catalogStyle.paddingInlineStart === formStyle.paddingInlineStart,
        matchingRadius: catalogStyle.borderRadius === formStyle.borderRadius,
        matchingSurface: catalogStyle.backgroundColor === formStyle.backgroundColor,
        contentAligned:
          Math.abs(
            catalogTitle.getBoundingClientRect().left - firstItem.getBoundingClientRect().left,
          ) < 1,
      }
    })
  expect(positionWorkspace).toEqual({
    alignedTop: true,
    sideBySide: true,
    matchingPadding: true,
    matchingRadius: true,
    matchingSurface: true,
    contentAligned: true,
  })
  await expect(page.locator('[data-slot="position-item-name"]').first()).toHaveCSS(
    'font-family',
    /Noto Serif SC/,
  )
  const positionFields = await page
    .locator('[data-slot="position-fields"]')
    .evaluate((container) => {
      const fields = Array.from(container.children).map((field) => field.getBoundingClientRect())
      return {
        sameWidth: Math.abs(fields[0].width - fields[1].width) < 1,
        stacked: fields[1].top > fields[0].bottom,
      }
    })
  expect(positionFields).toEqual({ sameWidth: true, stacked: true })
  await expect(page.getByRole('dialog', { name: '全局设置' })).toHaveScreenshot(
    'settings-position-dialog.png',
    { animations: 'disabled', maxDiffPixels: 50 },
  )
  await page.getByRole('button', { name: '模型管理' }).click()
  const modelSelect = page.getByLabel('模型', { exact: true })
  await expect(modelSelect).toHaveAttribute('role', 'combobox')
  const modelSelectionLayout = await page
    .locator('[data-slot="model-selection"]')
    .evaluate((container) => {
      const fields = Array.from(container.children).map((field) => field.getBoundingClientRect())
      return {
        sameRow: Math.abs(fields[0].top - fields[1].top) < 1,
        sameWidth: Math.abs(fields[0].width - fields[1].width) < 1,
      }
    })
  expect(modelSelectionLayout).toEqual({ sameRow: true, sameWidth: true })
  await modelSelect.click()
  const modelOptions = page.getByRole('option')
  await expect(modelOptions.first()).toBeVisible()
  await page.keyboard.press('Escape')
  const providerSelect = page.getByLabel('接入方式')
  await providerSelect.click()
  const providerOptions = page.getByRole('option')
  await expect(providerOptions).toHaveCount(4)
  await settleOverlay(page.locator('.prelude-select-popup[data-open]'))
  await page.screenshot({
    path: test.info().outputPath('settings-select.png'),
    fullPage: true,
  })
})

test('@visual keeps every hairline divider clear of the content it separates', async ({ page }) => {
  await installApi(page)
  await page.goto('/interview')
  await expect(page.getByRole('button', { name: '开始新面试' })).toBeVisible()
  await expect(dividerViolations(page)).resolves.toEqual([])

  await page.getByRole('button', { name: '设置' }).click()
  await expect(page.getByRole('heading', { name: '修改密码' })).toBeVisible()
  await expect(dividerViolations(page)).resolves.toEqual([])
  await page.getByRole('button', { name: '模型管理' }).click()
  await expect(page.getByRole('heading', { name: '高级设置' })).toBeVisible()
  await expect(dividerViolations(page)).resolves.toEqual([])
  await page.keyboard.press('Escape')

  await page.goto('/components-lab')
  await expect(page.getByRole('heading', { name: 'App rail' })).toBeVisible()
  await expect(dividerViolations(page)).resolves.toEqual([])
})

test('@visual keeps every composer control on one centre line', async ({ page }) => {
  await installApi(page)
  await page.goto('/components-lab')
  await expect(page.getByRole('heading', { name: 'Component Lab' })).toBeVisible()
  await expect(composerAlignmentViolations(page)).resolves.toEqual([])
})

/* Each gallery panel carries its own baseline. One full-page shot let a panel drift
   silently — the diff was a fraction of the frame and the first viewport only ever covered
   the top two panels. This list is the coverage contract: a panel added to the gallery
   without an entry fails the title assertion. */
const labPanels = [
  'Typography',
  'Panel',
  'Button',
  'Field',
  'SegmentedControl',
  'Prompt Bar',
  'Conversation',
  'App rail',
  'List & Navigation',
  'Report',
  'Empty & Error',
  'DropdownMenu',
  'Overlay & Feedback',
  'Brand',
] as const

const panelSlug = (title: string) =>
  title
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, '-')
    .replace(/^-|-$/g, '')

for (const scheme of schemes) {
  test(`@visual keeps every component lab panel pixel-stable in ${scheme}`, async ({ page }) => {
    await gotoComponentLab(page, scheme)
    const panels = page.locator('.workspace-page__content > section')
    const titles = await panels.evaluateAll((sections) =>
      sections.map((section) => section.querySelector('h2')?.textContent?.trim() ?? ''),
    )
    expect(titles).toEqual([...labPanels])

    for (const [index, title] of labPanels.entries()) {
      const panel = panels.nth(index)
      /* The panels live inside the page's own scroll container, and an element screenshot
         can only paint the pixels that container reveals — a panel taller than the window
         used to write a baseline whose lower half was blank. Grow the window by the
         measured deficit first, then gate on the panel actually fitting. */
      const geometry = await panel.evaluate((section) => ({
        height: Math.ceil(section.getBoundingClientRect().height),
        available: (section.parentElement as HTMLElement).clientHeight,
        viewport: innerHeight,
      }))
      if (geometry.height > geometry.available) {
        await page.setViewportSize({
          width: 1280,
          height: geometry.viewport + geometry.height - geometry.available,
        })
      }
      expect(
        await panel.evaluate(
          (section) =>
            (section.parentElement as HTMLElement).clientHeight >=
            Math.ceil(section.getBoundingClientRect().height),
        ),
      ).toBe(true)
      const mark = panel.locator('.brand-metaballs')
      const isWebgl = (await mark.count()) > 0
      await expect(panel).toHaveScreenshot(`component-lab-${panelSlug(title)}-${scheme}.png`, {
        animations: 'disabled',
        ...(isWebgl ? { mask: [mark] } : {}),
      })
      if (isWebgl) {
        // The shader is masked, so the mark's geometry carries the assertion instead. The
        // rail panel holds two marks, so the check runs on the first one.
        await expect
          .poll(() =>
            mark.first().evaluate((element) => {
              const box = element.getBoundingClientRect()
              const radius = Number.parseFloat(getComputedStyle(element).borderRadius)
              return Math.round(box.width) > 0 &&
                Math.round(box.width) === Math.round(box.height) &&
                radius >= box.width / 2
                ? 1
                : 0
            }),
          )
          .toBe(1)
      }
    }
  })
}

/* The gallery is gated panel by panel, but every product page was only ever checked by
   geometry — numbers pulled out of the DOM and compared. That catches a container losing its
   padding; it does not catch a shared owner rendering differently in the one place that ships
   it, which is exactly how the login caption's off-ladder line height went unnoticed. These
   are the surfaces that carry product content.

   Element screenshots can only paint what a scroll container reveals, so anything taller than
   the window is gated at its visible region on purpose — the full document behind it is the
   gallery Report panel's job.

   The loop below applies the scheme; `open` only navigates. Handing each surface the scheme to
   apply itself looked symmetric but let five of six signatures drop the parameter, and their
   dark baselines were copies of the light ones. */
const productSurfaces: {
  slug: string
  open: (page: Page) => Promise<Locator>
}[] = [
  {
    slug: 'login',
    async open(page: Page) {
      await installAnonymousSession(page)
      await page.goto('/login')
      await expect(page.getByRole('heading', { level: 1, name: '进入面试工作台' })).toBeVisible()
      return page.locator('.login-card')
    },
  },
  {
    slug: 'register',
    async open(page: Page) {
      await installAnonymousSession(page)
      await page.goto('/login')
      await page.getByRole('button', { name: '注册', exact: true }).click()
      await expect(page.getByRole('button', { name: '完成注册' })).toBeVisible()
      return page.locator('.login-card')
    },
  },
  {
    slug: 'interview-setup',
    async open(page: Page) {
      await installApi(page)
      await page.goto('/interview')
      await expect(page.getByRole('heading', { name: '准备开始一场沉浸式模拟面试' })).toBeVisible()
      return page.locator('[data-slot="interview-workspace"]')
    },
  },
  {
    slug: 'analytics',
    async open(page: Page) {
      await installApi(page)
      await page.goto('/analytics')
      await expect(page.getByRole('heading', { name: '分数趋势' })).toBeVisible()
      return page.locator('.workspace-page')
    },
  },
  {
    slug: 'report',
    async open(page: Page) {
      await installReportSession(page, JSON.stringify(sampleReport))
      await page.goto('/interview?session=9')
      const header = page.locator('.workspace-header')
      await header.getByRole('button', { name: '报告' }).click()
      const surface = page.locator('[data-slot="workspace-report"]')
      /* The hero slot only exists when the structured report rendered — a malformed body
         falls back to plain text and would silently produce a near-empty baseline. */
      await expect(surface.locator('[data-slot="report-hero"]')).toBeVisible()
      return surface
    },
  },
  {
    slug: 'settings-theme',
    async open(page: Page) {
      await installApi(page)
      await page.goto('/interview')
      await page.getByRole('button', { name: '设置' }).click()
      const dialog = page.getByRole('dialog', { name: '全局设置' })
      await dialog.getByRole('button', { name: '主题' }).click()
      await expect(dialog.getByRole('radiogroup', { name: '主题偏好' })).toBeVisible()
      return dialog
    },
  },
]

for (const scheme of schemes) {
  for (const surface of productSurfaces) {
    /* One test per surface, and an authenticated session stub that ships a report:
       `installApi` only serves an ongoing session, so the report toggle never appeared. */
    test(`@visual keeps the ${surface.slug} surface pixel-stable in ${scheme}`, async ({
      page,
    }) => {
      await page.setViewportSize({ width: 1280, height: 900 })
      await page.emulateMedia({ reducedMotion: 'reduce' })
      await preferScheme(page, scheme)
      const target = await surface.open(page)
      /* Without this the pair of baselines for a surface could be the same frame under two
         names and still both pass, which is the bug that shipped here once. */
      await expectScheme(page, scheme)
      /* Two of these paint through a canvas — the login card's shader orb and the dashboard's
         echarts — and a rasterised surface is not byte-stable across runs, so it is masked and
         the rest of the frame carries the assertion. Same treatment the gallery panels use. */
      const maskTargets = [
        ...(await target.locator('.brand-metaballs').all()),
        ...(await target.locator('canvas').all()),
      ]
      await expect(target).toHaveScreenshot(`product-${surface.slug}-${scheme}.png`, {
        animations: 'disabled',
        ...(maskTargets.length ? { mask: maskTargets } : {}),
      })
    })
  }
}

test('@visual keeps the not-found surface on the anonymous page shell', async ({ page }) => {
  await installApi(page)
  await page.emulateMedia({ reducedMotion: 'reduce' })
  await page.goto('/no-such-route')
  await expect(page.getByRole('heading', { name: '页面不存在' })).toBeVisible()
  // The brand mark is a WebGL surface, so it stays out of the pixel oracle; its geometry is
  // gated here and the animated shader is reviewed through the surface captures instead.
  const composition = await page.locator('[data-slot="not-found"]').evaluate((section) => {
    const mark = section.querySelector<HTMLElement>('.brand-metaballs')
    const back = section.querySelector<HTMLAnchorElement>('a[href="/"]')
    const box = mark?.getBoundingClientRect()
    const radius = mark ? Number.parseFloat(getComputedStyle(mark).borderRadius) : 0
    return {
      markIsCircularSquare: Boolean(
        box &&
        Math.round(box.width) === 72 &&
        Math.round(box.height) === 72 &&
        radius >= box.width / 2,
      ),
      markCenteredOnViewport: Boolean(
        box && Math.abs(box.left + box.width / 2 - innerWidth / 2) < 1,
      ),
      backUsesButtonSkin: Boolean(
        back &&
        back.tagName === 'A' &&
        back.classList.contains('prelude-button') &&
        back.getBoundingClientRect().width > 0,
      ),
    }
  })
  expect(composition).toEqual({
    markIsCircularSquare: true,
    markCenteredOnViewport: true,
    backUsesButtonSkin: true,
  })
  await expect(page.locator('[data-slot="not-found-body"]')).toHaveScreenshot('not-found.png', {
    animations: 'disabled',
  })
})

test('@visual keeps the workspace header flex allocation safe on narrow desktops', async ({
  page,
}) => {
  await page.setViewportSize({ width: 1200, height: 800 })
  await installReportSession(page, '{"summary":{}}')
  await page.goto('/interview?session=9')
  const header = page.locator('.workspace-header')
  await expect(header).toBeVisible()
  // Report state: both report controls must survive a long title.
  await header.getByRole('button', { name: '报告' }).click()
  await expect(header.getByRole('button', { name: '打印报告' })).toBeVisible()
  await expect(header.getByRole('button', { name: '面试' })).toBeVisible()
  await expect(header.locator('.status-badge')).toHaveCount(0)

  const geometry = await header.locator('.workspace-header__main').evaluate((main) => {
    const titleArea = main.querySelector<HTMLElement>('.workspace-header__title-area')!
    const right = main.querySelector<HTMLElement>('[data-slot="page-header-actions"]')!
    const title = main.querySelector<HTMLElement>('.workspace-header__title')!
    const titleBox = title.getBoundingClientRect()
    return {
      titleAreaRight: titleArea.getBoundingClientRect().right,
      rightLeft: right.getBoundingClientRect().left,
      titleRight: titleBox.right,
      truncated: title.scrollWidth > title.clientWidth,
      titleAreaTop: titleArea.getBoundingClientRect().top,
      rightTop: right.getBoundingClientRect().top,
    }
  })

  // The left group never overlaps the right controls; the title is the only
  // shrinkable element and stays inside its allocation (single header row).
  expect(geometry.titleAreaRight).toBeLessThanOrEqual(geometry.rightLeft + 1)
  expect(geometry.titleRight).toBeLessThanOrEqual(geometry.titleAreaRight + 1)
  expect(geometry.truncated).toBe(true)
  expect(Math.abs(geometry.titleAreaTop - geometry.rightTop)).toBeLessThanOrEqual(3)
})

async function gotoComponentLab(page: Page, scheme: Scheme) {
  await installApi(page)
  await preferScheme(page, scheme)
  await page.emulateMedia({ reducedMotion: 'reduce' })
  await page.goto('/components-lab')
  await expect(page.getByRole('heading', { name: 'Component Lab' })).toBeVisible()
  await expect(page.getByRole('heading', { name: 'Typography' })).toBeVisible()
  await expectScheme(page, scheme)
}

/** A session far enough along to change what the header offers. `status` decides which of
   the two waiting states the page shows, and `summaryReport` decides whether there is one:
   'generating' with no report renders the generating surface, 'finished' offers the toggle. `installApi` only serves an
 *  ongoing session, and which report body ships decides what the surface actually renders:
 *  `{"summary":{}}` falls back to plain text, while the gallery's structured sample renders
 *  the real report document. */
async function installReportSession(
  page: Page,
  summaryReport: string,
  title = longSessionTitle,
  status = 'finished',
) {
  await installApi(page)
  await page.route('**/api/interview/sessions', async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        code: 200,
        message: 'ok',
        data: [
          { sessionId: 9, targetPosition: title, status: 'finished', currentStage: 'closing' },
        ],
      }),
    })
  })
  await page.route('**/api/interview/9/messages', async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        code: 200,
        message: 'ok',
        data: {
          sessionId: 9,
          targetPosition: title,
          status,
          currentStage: 'closing',
          summaryReport,
          stages: [],
          messages: [{ id: 1, role: 'assistant', content: '请先介绍一下你自己。' }],
          resumeId: 1,
          positionId: 1,
          attachments: [],
        },
      }),
    })
  })
}

const longSessionTitle =
  '资深全栈工程师（Java 后端 × React 前端 · 平台架构与高并发稳定性方向 · 负责人级）'

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

/* A hairline that separates two blocks has to breathe on both sides. Divider elements are
   the ones with a single 1px edge, no radius and no fill of their own — that combination is
   what distinguishes a rule from a card edge or a control border. The gap under a line comes
   from its container, so a zero here means a call site glued content onto the divider. */
/* A control that steps out of its row while it is held reads as broken alignment, not as
   feedback — the hold button's 2px press nudge did exactly that beside its neighbours.
   Every button in a composer's trailing cluster has to share one top and one bottom edge,
   in each state the gallery freezes. */
async function composerAlignmentViolations(page: Page) {
  return page.locator('[data-slot="prompt-bar-controls"]').evaluateAll((rows) =>
    rows.flatMap((row) => {
      const cluster = row.lastElementChild as HTMLElement
      const buttons = Array.from(cluster.querySelectorAll('button'))
      if (buttons.length < 2) return []
      const boxes = buttons.map((button) => button.getBoundingClientRect())
      const tops = [...new Set(boxes.map((box) => Math.round(box.top)))]
      const bottoms = [...new Set(boxes.map((box) => Math.round(box.bottom)))]
      if (tops.length === 1 && bottoms.length === 1) return []
      return [
        {
          buttons: buttons.map((button) => button.getAttribute('aria-label')?.trim()),
          tops,
          bottoms,
        },
      ]
    }),
  )
}

async function dividerViolations(page: Page) {
  return page.evaluate(() => {
    const px = (value: string) => Number.parseFloat(value) || 0
    const edges = (style: CSSStyleDeclaration) =>
      [
        style.borderTopWidth,
        style.borderRightWidth,
        style.borderBottomWidth,
        style.borderLeftWidth,
      ].filter((width) => px(width) >= 1)
    const inFlow = (node: Element) => {
      const style = getComputedStyle(node)
      return (
        style.position !== 'absolute' &&
        style.position !== 'fixed' &&
        style.display !== 'none' &&
        style.visibility !== 'hidden'
      )
    }
    const contentTop = (el: Element): number => {
      const kids = Array.from(el.children).filter(inFlow)
      if (!kids.length) return el.getBoundingClientRect().top + px(getComputedStyle(el).paddingTop)
      return Math.min(...kids.map(contentTop))
    }
    const contentBottom = (el: Element): number => {
      const kids = Array.from(el.children).filter(inFlow)
      if (!kids.length)
        return el.getBoundingClientRect().bottom - px(getComputedStyle(el).paddingBottom)
      return Math.max(...kids.map(contentBottom))
    }
    const floor = px(getComputedStyle(document.documentElement).getPropertyValue('--spacing-sm'))
    const name = (el: Element) =>
      `${el.tagName.toLowerCase()}.${(el.getAttribute('class') ?? '').split(/\s+/).slice(0, 3).join('.')}`
    const bad: string[] = []
    for (const el of Array.from(document.body.querySelectorAll<HTMLElement>('*'))) {
      if (!inFlow(el) || el.closest('svg')) continue
      const style = getComputedStyle(el)
      if (edges(style).length !== 1 || px(style.borderRadius) > 0) continue
      if (style.backgroundColor !== 'rgba(0, 0, 0, 0)') continue
      const box = el.getBoundingClientRect()
      if (box.width < 8 || box.height < 2) continue
      const isTop = px(style.borderTopWidth) >= 1
      const lineY = isTop ? box.top : box.bottom
      const overlaps = (other: Element) => {
        const rect = other.getBoundingClientRect()
        return rect.right > box.left + 1 && rect.left < box.right - 1
      }
      const siblings = el.parentElement
        ? Array.from(el.parentElement.children).filter((s) => s !== el && inFlow(s) && overlaps(s))
        : []
      const next = siblings.find((s) => s.getBoundingClientRect().top >= box.top - 1)
      const previous = [...siblings]
        .reverse()
        .find((s) => s.getBoundingClientRect().bottom <= box.top + 1)
      const above = isTop
        ? previous
          ? lineY - contentBottom(previous)
          : floor
        : lineY - contentBottom(el)
      const below = isTop ? contentTop(el) - lineY : next ? contentTop(next) - lineY : floor
      if (above < floor - 0.6 || below < floor - 0.6) {
        bad.push(`${name(el)} — above ${Math.round(above)}px, below ${Math.round(below)}px`)
      }
    }
    return bad
  })
}
