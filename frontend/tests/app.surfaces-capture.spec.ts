import { mkdir, writeFile } from 'node:fs/promises'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { execFileSync } from 'node:child_process'
import { expect, test, type Page } from '@playwright/test'
import {
  createDemoState,
  DEMO_VIEWPORT,
  installDemoHarness,
  installVoiceLane,
  pushVoiceFrame,
  releaseVoiceAudio,
} from './demo-harness'

const surfaceDirectory = path.resolve(
  path.dirname(fileURLToPath(import.meta.url)),
  '../../docs/screenshots/surfaces',
)

const captured: string[] = []

test.use({
  viewport: DEMO_VIEWPORT,
  deviceScaleFactor: 1,
  launchOptions: { args: ['--use-fake-device-for-media-stream', '--use-fake-ui-for-media-stream'] },
})

async function start(page: Page, colorScheme: 'light' | 'dark', voiceLane = false) {
  const state = createDemoState()
  await installDemoHarness(page, state)
  if (voiceLane) await installVoiceLane(page)
  await page.emulateMedia({ colorScheme, reducedMotion: 'reduce' })
  await page.context().grantPermissions(['microphone'])
  await mkdir(surfaceDirectory, { recursive: true })
  await page.goto('/login')
  await expect(page.getByRole('heading', { level: 1, name: '进入面试工作台' })).toBeVisible()
  return state
}

async function logIn(page: Page) {
  await page.getByLabel('用户名').fill('demo')
  await page.getByLabel('密码', { exact: true }).fill('123456')
  await page.locator('form').getByRole('button', { name: '登录', exact: true }).click()
  await expect(page).toHaveURL(/\/interview$/)
}

async function capture(page: Page, name: string) {
  await settle(page)
  const fileName = `${name}.png`
  await page.screenshot({ path: path.join(surfaceDirectory, fileName), fullPage: false })
  captured.push(fileName)
}

async function settle(page: Page) {
  await page.evaluate(async () => {
    await document.fonts.ready
    await new Promise<void>((resolve) =>
      requestAnimationFrame(() => requestAnimationFrame(() => resolve())),
    )
    await Promise.all(
      Array.from(document.getAnimations())
        .filter((animation) => animation.playState !== 'finished')
        .map((animation) => animation.finished.catch(() => undefined)),
    )
  })
}

/* The gallery is one screen tall only if the frame is: measure its own height and grow
   the viewport to it, so the shot is never a cropped column and the number does not have
   to be re-tuned every time the gallery gains a panel. */
async function captureLab(page: Page, name: string) {
  await page.setViewportSize({ width: DEMO_VIEWPORT.width, height: DEMO_VIEWPORT.height })
  await page.goto('/components-lab')
  await expect(page.getByRole('heading', { name: 'Component Lab' })).toBeVisible()
  await expect(page.getByRole('heading', { name: 'Brand' })).toBeVisible()
  const fullHeight = await page.evaluate(() => {
    const scroller = document.querySelector('.workspace-page__content')
    const header = document.querySelector('.workspace-header')
    if (!scroller || !header) throw new Error('Component Lab shell is missing')
    return Math.ceil(scroller.scrollHeight + header.getBoundingClientRect().height)
  })
  await page.setViewportSize({ width: DEMO_VIEWPORT.width, height: fullHeight })
  await expect
    .poll(() =>
      page
        .locator('.workspace-page__content')
        .evaluate((frame) => frame.scrollHeight - frame.clientHeight),
    )
    .toBeLessThanOrEqual(0)
  await capture(page, name)
  await page.setViewportSize(DEMO_VIEWPORT)
}

async function hoverContextMenuItem(page: Page, menuLabel: string) {
  const item = page.getByRole('menuitem', { name: new RegExp(menuLabel) })
  await expect(item).toBeVisible()
  await item.hover()
  await settle(page)
}

async function openContextMenu(page: Page, menuLabel: string) {
  await page.getByRole('button', { name: '添加面试上下文' }).click()
  await settle(page)
  await hoverContextMenuItem(page, menuLabel)
}

async function selectContext(page: Page, menuLabel: string, option: string) {
  await openContextMenu(page, menuLabel)
  await page.getByRole('menuitemradio', { name: option }).click()
  await settle(page)
}

test.describe('@capture authenticated surface reference set', () => {
  test('anonymous and workspace surfaces', async ({ page }) => {
    await start(page, 'light', true)
    await capture(page, '01-login-light')

    await logIn(page)
    await capture(page, '05-interview-empty')

    await openContextMenu(page, '选择简历')
    await capture(page, '06-resume-picker-open')
    await page.getByRole('menuitemradio', { name: 'Java 后端工程师简历.pdf' }).click()
    await settle(page)
    await selectContext(page, '选择岗位', 'Java 后端工程师')
    await page.getByRole('button', { name: '开始面试' }).click()
    await expect(page).toHaveURL(/session=62/)
    await expect(page.locator('[data-slot="message-thread"]')).toBeVisible()

    /* Captured with a live session so the sidebar shows its active row, which the
       post-login frame does not. */
    await capture(page, '03-sidebar-expanded')

    await page.getByLabel('面试回答').fill('先用本地消息表把出站消息和订单状态写进同一个事务。')
    await capture(page, '08-composer-text-mode')

    await page.getByLabel('收起侧栏').click()
    await capture(page, '04-sidebar-collapsed')
    await page.getByLabel('展开侧栏').click()

    /* The voice lane is staged in-process by `installVoiceLane` — transport, audio sink,
       microphone and analyser — so these frames show the real `useVoiceInterview` state
       machine and the composer's live surfaces. They are evidence of the client, never of
       upstream audio. */
    /* Located by its class: the control's accessible name is 松开发送 while it is held. */
    const holdToTalk = page.locator('.prelude-button--hold')
    await page.getByRole('button', { name: '切换到语音输入' }).click()
    await expect(holdToTalk).toBeVisible()
    await capture(page, '09-composer-voice-connected')

    const restWidth = Math.round(
      await holdToTalk.evaluate((el) => el.getBoundingClientRect().width),
    )
    await holdToTalk.hover()
    await page.mouse.down()
    await expect(page.locator('.voice-meter')).toBeVisible()
    /* The meter takes the words' place without moving the control under the finger. */
    await expect
      .poll(() => holdToTalk.evaluate((el) => Math.round(el.getBoundingClientRect().width)))
      .toBe(restWidth)
    await capture(page, '09-composer-voice-listening')
    await page.mouse.up()

    /* The staged session already carries a draft, so the assertion is that the transcript
       joined it rather than the exact string. */
    await pushVoiceFrame(page, { type: 'user_text', text: '示例转录文本，等待确认后发送。' })
    await expect
      .poll(() => page.getByLabel('面试回答').inputValue())
      .toContain('示例转录文本，等待确认后发送。')
    await capture(page, '09-composer-voice-transcript')

    await pushVoiceFrame(page, { type: 'status', status: 'processing' })
    await expect(holdToTalk).toBeDisabled()
    await capture(page, '09-composer-voice-processing')

    await pushVoiceFrame(page, { type: 'audio', data: 'AAAAAAAA' })
    await expect(holdToTalk).toBeDisabled()
    await capture(page, '09-composer-voice-speaking')
    await releaseVoiceAudio(page)

    await pushVoiceFrame(page, { type: 'error', message: '语音服务异常' })
    await expect(page.getByRole('button', { name: '切换到语音输入' })).toBeVisible()
    await expect(holdToTalk).toBeHidden()
    await capture(page, '09-composer-voice-fallback')
  })

  test('report, analytics, settings and development surfaces', async ({ page }) => {
    await start(page, 'light')
    await logIn(page)
    await selectContext(page, '选择简历', 'Java 后端工程师简历.pdf')
    await openContextMenu(page, '选择岗位')
    await capture(page, '07-position-picker-open')
    await page.getByRole('menuitemradio', { name: 'Java 后端工程师' }).click()
    await settle(page)
    await page.getByRole('button', { name: '开始面试' }).click()
    await expect(page).toHaveURL(/session=62/)

    await page.getByRole('button', { name: '生成报告' }).click()
    await expect(page.getByRole('heading', { name: '求职训练报告' })).toBeVisible()
    await capture(page, '10-report-structured')

    await page.getByRole('link', { name: '数据看板' }).click()
    await expect(page.getByRole('heading', { name: '能力雷达' })).toBeVisible()
    await capture(page, '11-analytics-dashboard')

    await page.getByRole('button', { name: '设置' }).click()
    await expect(page.getByRole('dialog', { name: '全局设置' })).toBeVisible()
    for (const [index, section] of (
      ['账号资料', '简历管理', '岗位管理', '模型管理', '主题'] as const
    ).entries()) {
      await page
        .getByRole('dialog', { name: '全局设置' })
        .getByRole('button', { name: section })
        .click()
      await capture(page, `12-settings-${String(index + 1)}-${sectionSlug(section)}`)
    }
    await page.keyboard.press('Escape')

    await captureLab(page, '17-components-lab-light')
  })

  test('dark variants and terminal states', async ({ page }) => {
    await start(page, 'dark')
    await expect(page.locator('html')).toHaveClass(/dark/)
    await capture(page, '02-login-dark')

    await logIn(page)
    await page.getByRole('button', { name: '开始新面试' }).click()
    await capture(page, '13-interview-empty-dark')

    // Both routes are lazy-loaded, so the previous document is still painted when
    // navigation starts; waiting on the heading is what keeps these off a blank frame.
    await captureLab(page, '18-components-lab-dark')

    await page.goto('/no-such-route')
    await expect(page.getByRole('heading', { name: '页面不存在' })).toBeVisible()
    await capture(page, '16-not-found-dark')
  })

  test('secondary states and every shared primitive', async ({ page }) => {
    await start(page, 'light')
    await page.getByRole('button', { name: '注册', exact: true }).click()
    await expect(page.getByRole('heading', { level: 1, name: '创建工作台账号' })).toBeVisible()
    await capture(page, '14-login-register-light')

    await page.goto('/no-such-route')
    await expect(page.getByRole('heading', { name: '页面不存在' })).toBeVisible()
    await capture(page, '15-not-found-light')

    await page.goto('/components-lab')
    await expect(page.getByRole('heading', { name: 'Component Lab' })).toBeVisible()
    await page.getByRole('button', { name: '菜单触发器', exact: true }).click()
    await hoverContextMenuItem(page, '子菜单')
    await expect(page.getByRole('menuitemradio', { name: '单选项一' })).toBeVisible()
    await settle(page)
    await capture(page, '19-menu-with-submenu')
    /* One Escape closes the open submenu, a second closes the root menu. */
    await page.keyboard.press('Escape')
    await page.keyboard.press('Escape')
    await expect(page.getByRole('menu')).toHaveCount(0)

    await page.getByRole('button', { name: '打开工作台浮层' }).click()
    await expect(page.getByRole('dialog', { name: '工作台浮层' })).toBeVisible()
    await capture(page, '20-dialog-workspace')
    await page.keyboard.press('Escape')

    await page.getByRole('button', { name: '危险确认' }).click()
    await expect(page.getByRole('button', { name: '危险操作' })).toBeVisible()
    await capture(page, '21-confirm-destructive')
    await page.getByRole('button', { name: '取消' }).click()

    await page.getByRole('button', { name: '警告', exact: true }).click()
    await capture(page, '22-toast-warning')
    /* Dismiss it so the tooltip frame below shows only the tooltip. */
    await page.locator('.prelude-toast__close').click()
    await expect(page.locator('.prelude-toast')).toHaveCount(0)

    await page.getByRole('button', { name: '提示', exact: true }).hover()
    await expect(page.locator('.prelude-tooltip')).toBeVisible()
    await capture(page, '23-tooltip-icon')
  })

  test.afterAll(async () => {
    await mkdir(surfaceDirectory, { recursive: true })
    const revision = execFileSync('git', ['rev-parse', 'HEAD'], { encoding: 'utf8' }).trim()
    /* The frames are written before the commit that carries them, so `revision` alone never
       identifies these pixels. Anything other than the screenshot set itself that still
       differs from HEAD means the shots came from uncommitted code — say so out loud. */
    const inputs = execFileSync('git', ['status', '--porcelain'], { encoding: 'utf8' })
      .split('\n')
      .filter((line) => line.trim() && !line.includes('docs/screenshots/surfaces/'))
    if (inputs.length) {
      console.warn(
        `capture:surfaces ran on a dirty tree: ${inputs.length} input file(s) differ from ${revision.slice(0, 7)}`,
      )
    }
    await writeFile(
      path.join(surfaceDirectory, 'manifest.json'),
      `${JSON.stringify(
        {
          revision,
          capturedAt: new Date().toISOString(),
          inputsMatchRevision: inputs.length === 0,
          dirtyInputFiles: inputs.length,
          surfaces: captured,
        },
        null,
        2,
      )}\n`,
      'utf8',
    )
  })
})

function sectionSlug(section: string) {
  if (section === '账号资料') return 'profile'
  if (section === '简历管理') return 'resumes'
  if (section === '岗位管理') return 'positions'
  if (section === '模型管理') return 'llm'
  return 'theme'
}
