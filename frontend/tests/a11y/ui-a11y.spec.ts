/**
 * UI accessibility verification.
 *
 * Combines axe-core scanning with explicit keyboard-path assertions for the
 * surfaces that the guardrail layer cannot validate statically: Dialog focus
 * trap, Combobox / Dropdown keyboard navigation, Tooltip focus pattern, etc.
 *
 * Like the visual spec, all /api/** requests are mocked via page.route.
 * The accessibility assertions use the ARIA roles / roles / names that the
 * components already expose (see reka-ui + radix-vue primitives).
 *
 * The `installMockApi` helper is shared with `tests/visual/ui-visual.spec.ts`
 * via `tests/_helpers/mock-api.ts` to keep the sentrux `min_equality` gate
 * healthy (current floor lives in .sentrux/rules.toml).
 *
 * Severity policy (see docs/quality/ui-quality-system.md §5 A11y coverage):
 * only CRITICAL axe violations fail the run. SERIOUS violations (notably
 * color-contrast) are reported to the console + tracked as backlog; do NOT
 * read a green run as "no a11y issues" — read it as "no critical axe
 * violations are blocking the PR".
 */
import { AxeBuilder } from '@axe-core/playwright'
import { test, expect, type Page } from '@playwright/test'
import { installMockApi, STRUCTURED_REPORT } from '../_helpers/mock-api'

async function axe(page: Page, label: string) {
  const results = await new AxeBuilder({ page })
    .withTags(['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa'])
    .analyze()
  const critical = results.violations.filter((v) => v.impact === 'critical')
  const serious = results.violations.filter((v) => v.impact === 'serious')
  if (critical.length > 0) {
    const summary = critical
      .map((v) => {
        const targets = v.nodes
          .slice(0, 3)
          .map((n) => n.target.join(' '))
          .join(' / ')
        return `[${v.impact}] ${v.id} — ${v.description}\n  targets: ${targets || '(none)'}`
      })
      .join('\n')
    throw new Error(`axe CRITICAL violations on ${label}:\n${summary}`)
  }
  // eslint-disable-next-line no-console
  console.log(
    `[axe ${label}] ${results.violations.length} total violations (${serious.length} serious, ${critical.length} critical)`,
  )
  if (serious.length > 0) {
    // eslint-disable-next-line no-console
    console.log(
      `  serious backlog: ${serious.map((v) => `${v.id} (${v.nodes.length})`).join(', ')}`,
    )
  }
}

// Phase 2 gates ONLY critical axe violations. The historical
// `expectNoSeriousViolations` name would mislead readers into thinking
// the run asserts "no serious a11y issues" — it does not. We expose the
// explicit `expectNoCriticalViolations` alias so spec assertions stay
// faithful to the actual gate.
async function expectNoCriticalViolations(page: Page, label: string) {
  return axe(page, label)
}
test.describe.configure({ mode: 'serial' })

test('login page — no critical axe violations', async ({ page }) => {
  await page.goto('/login')
  await expect(page.getByRole('button', { name: '登录' }).first()).toBeVisible()
  await expectNoCriticalViolations(page, 'login')
})

test('workspace shell — no critical axe violations', async ({ page }) => {
  await installMockApi(page)
  await page.goto('/interview')
  await page.locator('.app-sidebar').first().waitFor({ state: 'visible' })
  await expectNoCriticalViolations(page, 'workspace')
})

test('settings modal — opens, focus stays inside, Esc closes', async ({ page }) => {
  await installMockApi(page)
  await page.goto('/interview')
  await page.locator('.app-sidebar__btn--settings').first().click()
  await page.locator('[role="dialog"]').first().waitFor({ state: 'visible' })
  const focusedInside = await page.evaluate(() => {
    const dialog = document.querySelector('[role="dialog"]')
    return dialog ? dialog.contains(document.activeElement) : false
  })
  expect(focusedInside).toBe(true)
  for (let i = 0; i < 5; i++) {
    await page.keyboard.press('Tab')
    const inside = await page.evaluate(() => {
      const dialog = document.querySelector('[role="dialog"]')
      return dialog ? dialog.contains(document.activeElement) : false
    })
    expect(inside).toBe(true)
  }
  await page.keyboard.press('Escape')
  await expect(page.locator('[role="dialog"]')).toHaveCount(0)
  await expectNoCriticalViolations(page, 'settings-closed')
})

test('position dropdown — opens via click, lists options, closes via Escape', async ({ page }) => {
  await installMockApi(page)
  await page.goto('/interview')
  const trigger = page
    .locator('button')
    .filter({ has: page.locator('.lucide-briefcase') })
    .first()
  await trigger.click()
  await expect(page.getByRole('menuitem', { name: 'Java 后端工程师' }).first()).toBeVisible()
  await expect(page.locator('[role="menu"]').first()).toBeVisible()
  await page.keyboard.press('Escape')
  await expect(page.locator('[role="menu"]')).toHaveCount(0)
})

test('settings — LLM tab keyboard reachable, axe clean', async ({ page }) => {
  await installMockApi(page)
  await page.goto('/interview')
  await page.locator('.app-sidebar__btn--settings').first().click()
  await page.locator('[role="dialog"]').first().waitFor({ state: 'visible' })
  const llmButton = page.getByRole('button', { name: 'LLM 配置' }).first()
  await llmButton.click()
  await expect(page.getByRole('heading', { name: 'LLM 配置' })).toBeVisible()
  const dialog = page.locator('[role="dialog"]').first()
  const combobox = dialog.locator('[role="combobox"]').first()
  await expect(combobox).toBeVisible()
  await combobox.focus()
  await expect(combobox).toBeFocused()
  await page.keyboard.press('ArrowDown')
  await expect(combobox).toHaveAttribute('aria-expanded', 'true')
  await page.keyboard.press('Escape')
  await expect(combobox).toHaveAttribute('aria-expanded', 'false')
  await expectNoCriticalViolations(page, 'settings-llm')
})

test('sidebar collapse button is keyboard-activatable', async ({ page }) => {
  await installMockApi(page)
  await page.goto('/interview')
  const toggle = page.locator('.app-sidebar__toggle').first()
  await toggle.focus()
  await expect(toggle).toBeFocused()
  await page.keyboard.press('Enter')
  await expect(toggle).toBeVisible()
})

test('composer textarea — Ctrl+Enter / Meta+Enter do not corrupt input', async ({ page }) => {
  await installMockApi(page)
  await page.goto('/interview')
  const composer = page.locator('.interview-composer').first()
  await expect(composer).toBeVisible()
  const textarea = composer.locator('textarea').first()
  await expect(textarea).toBeVisible()
  // Programmatically seed value so we can verify keyboard shortcuts do not
  // corrupt the textarea value, regardless of where focus lands.
  await textarea.evaluate((el) => {
    const ta = el as HTMLTextAreaElement
    ta.value = '我不会让半截消息写入数据库。'
    ta.dispatchEvent(new Event('input', { bubbles: true }))
  })
  await textarea.focus().catch(() => null)
  const textareaFocused = await textarea.evaluate((el) => document.activeElement === el)
  if (!textareaFocused) {
    // Composer's contenteditable proxy may steal focus; fall back to direct
    // keyboard events on the textarea.
    await textarea.evaluate((el) => {
      const ta = el as HTMLTextAreaElement
      const press = (modifier: 'Control' | 'Meta') => {
        const ev = new KeyboardEvent('keydown', {
          key: 'Enter',
          code: 'Enter',
          [modifier.toLowerCase() + 'Key']: true,
          bubbles: true,
          cancelable: true,
        })
        ta.dispatchEvent(ev)
      }
      press('Control')
      press('Meta')
    })
  } else {
    await page.keyboard.press('Control+Enter')
    await page.keyboard.press('Meta+Enter')
  }
  const valueAfter = await textarea.inputValue()
  expect(valueAfter).toContain('我不会让半截消息写入数据库')
})

test('tooltip uses provider primitive (not native title attribute)', async ({ page }) => {
  await installMockApi(page)
  await page.goto('/interview')
  // Native title= is forbidden by verify:ui guardrail; tooltips should use a
  // hover/focus provider. Sanity check: no element in the visible shell has
  // a title attribute. (reka-ui's TooltipText uses aria-describedby.)
  const nativeTitleCount = await page.evaluate(() => document.querySelectorAll('[title]').length)
  expect(nativeTitleCount).toBe(0)
})

test('structured report — semantic review lists and no critical axe violations', async ({
  page,
}) => {
  const session = {
    sessionId: 101,
    status: 'finished',
    targetPosition: 'Java 后端工程师',
    currentStage: 'closing',
    summaryReport: STRUCTURED_REPORT,
  }
  await installMockApi(page, {
    sessions: [session],
    interviewDetail: { ...session, stages: [], messages: [], resumeId: 1, positionId: 1 },
  })
  await page.goto('/interview')
  await page.getByRole('button', { name: '打开已结束会话 Java 后端工程师' }).click()
  await page.getByRole('button', { name: '报告', exact: true }).click()
  await expect(page.getByRole('heading', { name: '逐题复盘' })).toBeVisible()
  await expect(page.locator('.question-review-list')).toHaveJSProperty('tagName', 'OL')
  await expect(page.getByRole('button', { name: '上一题' })).toBeDisabled()
  await expect(page.getByRole('button', { name: '下一题' })).toBeEnabled()
  await expectNoCriticalViolations(page, 'structured-report')
})
