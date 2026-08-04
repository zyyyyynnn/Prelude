import { expect, test, type Locator, type Page } from '@playwright/test'
import { installMockApi, STRUCTURED_REPORT } from '../_helpers/mock-api'

type StyleSnapshot = {
  borderColor: string
  borderWidth: string
  backgroundColor: string
  color: string
  boxShadow: string
  outlineStyle: string
  outlineWidth: string
  textDecorationLine: string
  transform: string
  rect: { width: number; height: number }
}

async function styleOf(locator: Locator): Promise<StyleSnapshot> {
  return locator.evaluate((element) => {
    const style = getComputedStyle(element)
    const rect = element.getBoundingClientRect()
    return {
      borderColor: style.borderTopColor,
      borderWidth: style.borderTopWidth,
      backgroundColor: style.backgroundColor,
      color: style.color,
      boxShadow: style.boxShadow,
      outlineStyle: style.outlineStyle,
      outlineWidth: style.outlineWidth,
      textDecorationLine: style.textDecorationLine,
      transform: style.transform,
      rect: { width: rect.width, height: rect.height },
    }
  })
}

async function tabTo(page: Page, target: Locator, limit = 80) {
  await expect(target).toBeVisible()
  for (let index = 0; index < limit; index += 1) {
    await page.keyboard.press('Tab')
    if (await target.evaluate((element) => element === document.activeElement)) return
  }
  throw new Error(`Unable to reach target with Tab after ${limit} steps`)
}

async function blurWithPointer(page: Page) {
  await page.locator('body').click({ position: { x: 2, y: 2 } })
  await page.mouse.move(0, 0)
}

function rgb(value: string): [number, number, number] {
  const match = value.match(/rgba?\((\d+)[, ]+(\d+)[, ]+(\d+)/)
  if (!match) throw new Error(`Unsupported RGB value: ${value}`)
  return [Number(match[1]), Number(match[2]), Number(match[3])]
}

function luminance([red, green, blue]: [number, number, number]) {
  const convert = (channel: number) => {
    const value = channel / 255
    return value <= 0.04045 ? value / 12.92 : ((value + 0.055) / 1.055) ** 2.4
  }
  return 0.2126 * convert(red) + 0.7152 * convert(green) + 0.0722 * convert(blue)
}

function contrast(first: string, second: string) {
  const a = luminance(rgb(first))
  const b = luminance(rgb(second))
  return (Math.max(a, b) + 0.05) / (Math.min(a, b) + 0.05)
}

function expectStableGeometry(before: StyleSnapshot, after: StyleSnapshot) {
  expect(after.borderWidth).toBe('1px')
  expect(after.rect.width).toBeCloseTo(before.rect.width, 3)
  expect(after.rect.height).toBeCloseTo(before.rect.height, 3)
  expect(after.transform).toBe(before.transform)
  expect(after.boxShadow).toBe(before.boxShadow)
}

async function resolveTokenColor(page: Page, token: string) {
  return page.evaluate((name) => {
    const probe = document.createElement('span')
    probe.style.color = `var(${name})`
    document.body.appendChild(probe)
    const value = getComputedStyle(probe).color
    probe.remove()
    return value
  }, token)
}

async function setTheme(page: Page, theme: 'light' | 'dark') {
  await page.evaluate((value) => {
    document.documentElement.classList.toggle('dark', value === 'dark')
    document.documentElement.dataset.theme = value
  }, theme)
}

const ongoingSession = {
  sessionId: 101,
  status: 'ongoing',
  targetPosition: 'Java 后端工程师',
  currentStage: 'technical',
  summaryReport: '',
}

const finishedSession = {
  ...ongoingSession,
  status: 'finished',
  currentStage: 'closing',
  summaryReport: STRUCTURED_REPORT,
}

test('fields keep one stable boundary in pointer and keyboard paths for light and dark', async ({
  page,
}) => {
  await installMockApi(page)

  for (const theme of ['light', 'dark'] as const) {
    await page.goto('/components-lab')
    await setTheme(page, theme)
    const focusColor = await resolveTokenColor(page, '--color-focus-field')

    for (const field of [
      page.locator('#lab-input-default'),
      page.locator('#lab-textarea-default'),
    ]) {
      await blurWithPointer(page)
      const before = await styleOf(field)
      await field.click()
      await page.mouse.move(0, 0)
      await expect.poll(async () => (await styleOf(field)).borderColor).toBe(focusColor)
      const pointer = await styleOf(field)
      expect(pointer.backgroundColor).toBe(before.backgroundColor)
      expect(pointer.outlineStyle).toBe('none')
      expectStableGeometry(before, pointer)
      expect(contrast(pointer.borderColor, pointer.backgroundColor)).toBeGreaterThanOrEqual(3)

      await blurWithPointer(page)
      await tabTo(page, field)
      await expect.poll(async () => (await styleOf(field)).borderColor).toBe(focusColor)
      const keyboard = await styleOf(field)
      expect(keyboard.backgroundColor).toBe(before.backgroundColor)
      expectStableGeometry(before, keyboard)
    }

    const select = page.locator('[aria-label="实验室 Select"]')
    await blurWithPointer(page)
    const selectBefore = await styleOf(select)
    await select.click()
    await expect(select).toHaveAttribute('data-state', 'open')
    const pointerOpen = await styleOf(select)
    expect(pointerOpen.backgroundColor).toBe(selectBefore.backgroundColor)
    expectStableGeometry(selectBefore, pointerOpen)
    await page.keyboard.press('Escape')

    await blurWithPointer(page)
    await tabTo(page, select)
    await expect.poll(async () => (await styleOf(select)).borderColor).toBe(focusColor)
    const keyboard = await styleOf(select)
    expect(keyboard.backgroundColor).toBe(selectBefore.backgroundColor)
    expectStableGeometry(selectBefore, keyboard)
    await page.keyboard.press('Enter')
    await expect(select).toHaveAttribute('data-state', 'open')
    await page.keyboard.press('Escape')
  }
})

test('button variants preserve semantic surfaces and expose keyboard-only focus', async ({
  page,
}) => {
  await installMockApi(page)
  await page.goto('/components-lab')

  const buttonSection = page.locator('.lab-section').filter({ hasText: 'variant × size' })
  for (const variant of ['default', 'destructive', 'secondary', 'outline', 'ghost', 'link']) {
    const row = buttonSection.locator('.lab__row').filter({ hasText: variant }).first()
    const button = row.getByRole('button').first()
    await blurWithPointer(page)
    const before = await styleOf(button)

    await button.click()
    await page.mouse.move(0, 0)
    const pointer = await styleOf(button)
    expect(pointer.borderColor).toBe(before.borderColor)
    expect(pointer.backgroundColor).toBe(before.backgroundColor)
    expect(pointer.boxShadow).toBe(before.boxShadow)

    await blurWithPointer(page)
    await tabTo(page, button)
    if (['default', 'destructive', 'outline', 'secondary'].includes(variant)) {
      await expect
        .poll(async () => (await styleOf(button)).borderColor)
        .not.toBe(before.borderColor)
    } else if (variant === 'ghost') {
      await expect
        .poll(async () => (await styleOf(button)).backgroundColor)
        .not.toBe(before.backgroundColor)
    } else {
      await expect
        .poll(async () => (await styleOf(button)).textDecorationLine)
        .toContain('underline')
    }
    const keyboard = await styleOf(button)
    expectStableGeometry(before, keyboard)
    expect(keyboard.outlineStyle).toBe('none')

    if (variant === 'default' || variant === 'destructive') {
      expect(keyboard.backgroundColor).toBe(before.backgroundColor)
      expect(keyboard.color).toBe(before.color)
      expect(keyboard.borderColor).not.toBe(before.borderColor)
    } else if (variant === 'outline' || variant === 'secondary') {
      expect(keyboard.backgroundColor).toBe(before.backgroundColor)
      expect(keyboard.borderColor).not.toBe(before.borderColor)
    } else if (variant === 'ghost') {
      expect(keyboard.backgroundColor).not.toBe(before.backgroundColor)
    } else {
      expect(keyboard.textDecorationLine).toContain('underline')
      expect(keyboard.backgroundColor).toBe(before.backgroundColor)
    }
  }
})

test('pointer and F12 never leave a sidebar halo while keyboard focus remains visible', async ({
  page,
}) => {
  await installMockApi(page, {
    sessions: [ongoingSession],
    interviewDetail: { ...ongoingSession, messages: [], stages: [] },
  })
  await page.goto('/interview')

  const settings = page.getByRole('button', { name: '设置' })
  const baseline = await styleOf(settings)
  await settings.click()
  await expect(page.getByRole('dialog', { name: '全局设置' })).toBeVisible()
  await page.mouse.click(1400, 880)
  await expect(page.getByRole('dialog', { name: '全局设置' })).toHaveCount(0)
  await page.mouse.move(0, 0)
  await page.keyboard.press('F12')
  await expect(page.locator('html')).toHaveAttribute('data-input-intent', 'pointer')

  const afterF12 = await styleOf(settings)
  expect(afterF12.borderColor).toBe(baseline.borderColor)
  expect(afterF12.backgroundColor).toBe(baseline.backgroundColor)
  expect(afterF12.boxShadow).toBe(baseline.boxShadow)

  await blurWithPointer(page)
  await tabTo(page, settings)
  await expect(page.locator('html')).toHaveAttribute('data-input-intent', 'keyboard')
  const keyboard = await styleOf(settings)
  expect(keyboard.borderColor).not.toBe(baseline.borderColor)
  expect(keyboard.boxShadow).toBe(baseline.boxShadow)
  expectStableGeometry(baseline, keyboard)

  const pin = page.getByRole('button', { name: '置顶会话' })
  await tabTo(page, pin)
  const actions = pin.locator('xpath=..')
  await expect(actions).toHaveCSS('opacity', '1')
})

test('selected settings surface and report navigation keep independent keyboard semantics', async ({
  page,
}) => {
  await installMockApi(page, {
    sessions: [finishedSession],
    interviewDetail: { ...finishedSession, messages: [], stages: [] },
  })
  await page.goto('/interview')

  await page.getByRole('button', { name: '设置' }).click()
  const dialog = page.getByRole('dialog', { name: '全局设置' })
  await dialog.getByRole('button', { name: '主题' }).click()
  const selectedTheme = dialog.locator('.theme-option.is-active')
  await expect(selectedTheme).toBeEnabled()
  const selectedBefore = await styleOf(selectedTheme)
  await tabTo(page, selectedTheme)
  const selectedFocus = await styleOf(selectedTheme)
  expect(selectedFocus.borderWidth).toBe('1px')
  expect(selectedFocus.borderColor).not.toBe(selectedBefore.borderColor)
  expect(selectedFocus.boxShadow).toBe(selectedBefore.boxShadow)
  await page.keyboard.press('Escape')
  await expect(dialog).toHaveCount(0)

  await page.getByRole('button', { name: '打开已结束会话 Java 后端工程师' }).click()
  await page.getByRole('button', { name: '报告', exact: true }).click()
  const carousel = page.locator('.question-review-carousel')
  await expect(carousel).not.toHaveAttribute('tabindex', /.+/)
  const next = page.getByRole('button', { name: '下一题' })
  await blurWithPointer(page)
  await tabTo(page, next)
  await page.keyboard.press('Enter')
  await expect(page.locator('.question-review-carousel__counter')).toContainText('2 / 2')
})

test('forced colors restores a system outline without changing normal-theme geometry', async ({
  page,
}) => {
  await installMockApi(page)
  await page.emulateMedia({ forcedColors: 'active' })
  await page.goto('/components-lab')
  const button = page.getByRole('button', { name: '打开示例 Dialog' })
  await tabTo(page, button)
  const style = await styleOf(button)
  expect(style.outlineStyle).toBe('solid')
  expect(Number.parseFloat(style.outlineWidth)).toBeGreaterThanOrEqual(2)
})
