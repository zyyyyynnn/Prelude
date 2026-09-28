import { mkdir, readdir, writeFile } from 'node:fs/promises'
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

/**
 * Surface references are filed as
 *   docs/screenshots/<light|dark>/<interview|resume|lab>/<name>.png
 * so theme and product area are visible in the path.
 *
 * Each flow is written once and run for both themes. A per-theme copy of a flow is how the dark
 * set silently fell to a third of the light set: unpaired frames cannot be diffed, so "dark
 * looks wrong" had nothing to compare against.
 */
type Theme = 'light' | 'dark'
type Area = 'interview' | 'resume' | 'lab'

const themes: readonly Theme[] = ['light', 'dark']

const surfaceDirectory = path.resolve(
  path.dirname(fileURLToPath(import.meta.url)),
  '../../docs/screenshots',
)

const captured: string[] = []

test.use({
  viewport: DEMO_VIEWPORT,
  deviceScaleFactor: 1,
  launchOptions: { args: ['--use-fake-device-for-media-stream', '--use-fake-ui-for-media-stream'] },
})

async function start(page: Page, colorScheme: Theme, voiceLane = false) {
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

/**
 * A reference frame shows the resting state. After a click the virtual pointer stays where it
 * landed, so any row that paints a hover background at rest gets that slab baked into the
 * reference — a state no user ever sees. `pointerIsState` is the opt-out for the frames whose
 * subject genuinely is a hover, a tooltip or an open submenu.
 */
async function capture(
  page: Page,
  theme: Theme,
  area: Area,
  name: string,
  options: { fullPage?: boolean; pointerIsState?: boolean } = {},
) {
  if (!options.pointerIsState) await page.mouse.move(4, 4)
  await settle(page)
  // The manifest is a committed file read on every OS, so its paths are POSIX by contract.
  const relative = `${theme}/${area}/${name}.png`
  const target = path.join(surfaceDirectory, theme, area, `${name}.png`)
  await mkdir(path.dirname(target), { recursive: true })
  /* Windows intermittently refuses the write for a beat after the directory is created or the
     previous frame is still held by the indexer — an `UNKNOWN: unknown error, open`. A capture
     that dies on that leaves the theme sets asymmetric for a reason that has nothing to do with
     the UI, so the write retries before it fails the run. */
  for (let attempt = 1; ; attempt += 1) {
    try {
      await page.screenshot({ path: target, fullPage: options.fullPage ?? false })
      break
    } catch (error) {
      if (attempt >= 3) throw error
      await new Promise((resolve) => setTimeout(resolve, 250 * attempt))
    }
  }
  captured.push(relative)
}

async function settle(page: Page) {
  await page.evaluate(async () => {
    await document.fonts.ready
    await new Promise((resolve) => requestAnimationFrame(() => resolve(null)))
    await new Promise((resolve) => requestAnimationFrame(() => resolve(null)))
    await new Promise((resolve) => setTimeout(resolve, 250))
  })
}

async function captureLab(page: Page, theme: Theme, name: string) {
  await page.emulateMedia({ reducedMotion: 'no-preference' })
  await page.goto('/components-lab')
  await expect(page.getByRole('heading', { name: 'Component Lab' })).toBeVisible()
  const rose = page.locator('.generating-card .rose-three-loader > g')
  await expect(rose).toHaveAttribute('transform', /rotate\(/)
  const firstFrame = await rose.getAttribute('transform')
  await expect.poll(() => rose.getAttribute('transform')).not.toBe(firstFrame)
  await settle(page)
  /* The lab lives in a viewport-sized scroll shell. Expand that shell to its
     content height so fullPage captures the whole gallery as one long reference. */
  await page.evaluate(() => {
    const shell = document.querySelector<HTMLElement>('.workspace-page')
    const content = document.querySelector<HTMLElement>('.workspace-page__content')
    if (shell) {
      shell.style.height = 'auto'
      shell.style.overflow = 'visible'
      shell.style.flex = 'none'
    }
    if (content) {
      content.style.overflow = 'visible'
      content.style.flex = 'none'
      content.style.maxHeight = 'none'
    }
  })
  await capture(page, theme, 'lab', name, { fullPage: true })
}

/** The interview workspace from the anonymous entry through the six voice states. */
async function interviewWorkspaceFlow(page: Page, theme: Theme) {
  await start(page, theme, true)
  await capture(page, theme, 'interview', 'login')

  await logIn(page)
  await capture(page, theme, 'interview', 'workspace-empty')

  await openContextMenu(page, '选择简历')
  await capture(page, theme, 'interview', 'resume-picker-open', { pointerIsState: true })
  await page.getByRole('menuitemradio', { name: 'Java 后端工程师简历.pdf' }).click()
  await selectContext(page, '选择岗位', 'Java 后端工程师')
  await page.getByRole('button', { name: '开始面试' }).click()
  await expect(page).toHaveURL(/session=62/)
  await expect(page.locator('[data-slot="message-thread"]')).toBeVisible()

  await capture(page, theme, 'interview', 'sidebar-expanded')

  await page.getByLabel('面试回答').fill('先用本地消息表把出站消息和订单状态写进同一个事务。')
  await capture(page, theme, 'interview', 'composer-text')

  await page.getByRole('button', { name: '收起侧栏' }).click()
  await expect(page.locator('.sidebar-frame')).toHaveClass(/is-collapsed/)
  await capture(page, theme, 'interview', 'sidebar-collapsed')
  await page.getByRole('button', { name: '展开侧栏' }).click()
  await expect(page.locator('.sidebar-frame')).not.toHaveClass(/is-collapsed/)

  const holdToTalk = page.locator('.ui-button--hold')
  await page.getByRole('button', { name: '切换到语音输入' }).click()
  await expect(holdToTalk).toBeVisible()
  await capture(page, theme, 'interview', 'voice-connected')

  const restWidth = Math.round(await holdToTalk.evaluate((el) => el.getBoundingClientRect().width))
  await holdToTalk.hover()
  await page.mouse.down()
  await expect(page.locator('.voice-meter')).toBeVisible()
  await expect
    .poll(() => holdToTalk.evaluate((el) => Math.round(el.getBoundingClientRect().width)))
    .toBe(restWidth)
  await capture(page, theme, 'interview', 'voice-listening', { pointerIsState: true })
  await page.mouse.up()

  await pushVoiceFrame(page, { type: 'user_text', text: '示例转录文本，等待确认后发送。' })
  await expect
    .poll(() => page.getByLabel('面试回答').inputValue())
    .toContain('示例转录文本，等待确认后发送。')
  await capture(page, theme, 'interview', 'voice-transcript')

  await pushVoiceFrame(page, { type: 'status', status: 'processing' })
  await expect(holdToTalk).toBeDisabled()
  await capture(page, theme, 'interview', 'voice-processing')

  await pushVoiceFrame(page, { type: 'audio', data: 'AAAAAAAA' })
  await expect(holdToTalk).toBeDisabled()
  await capture(page, theme, 'interview', 'voice-speaking')
  await releaseVoiceAudio(page)

  await pushVoiceFrame(page, { type: 'error', message: '语音服务异常' })
  await expect(page.getByRole('button', { name: '切换到语音输入' })).toBeVisible()
  await expect(holdToTalk).toBeHidden()
  await capture(page, theme, 'interview', 'voice-fallback')
}

/** Started session, report, analytics and the five settings sections. */
async function interviewProductFlow(page: Page, theme: Theme) {
  await start(page, theme)
  await logIn(page)
  await selectContext(page, '选择简历', 'Java 后端工程师简历.pdf')
  await openContextMenu(page, '选择岗位')
  await capture(page, theme, 'interview', 'position-picker-open', { pointerIsState: true })
  await page.getByRole('menuitemradio', { name: 'Java 后端工程师' }).click()
  await page.getByRole('button', { name: '开始面试' }).click()
  await expect(page).toHaveURL(/session=62/)

  await page.getByRole('button', { name: '生成报告' }).click()
  await expect(page.getByRole('heading', { name: '求职训练报告' })).toBeVisible()
  await capture(page, theme, 'interview', 'report')

  await page.getByRole('link', { name: '数据看板' }).click()
  await expect(page.getByRole('heading', { name: '能力雷达' })).toBeVisible()
  await capture(page, theme, 'interview', 'analytics')

  await page.getByRole('button', { name: '设置' }).click()
  await expect(page.getByRole('dialog', { name: '全局设置' })).toBeVisible()
  for (const [index, section] of (
    ['账号资料', '简历管理', '岗位管理', '模型管理', '主题'] as const
  ).entries()) {
    await page
      .getByRole('dialog', { name: '全局设置' })
      .getByRole('button', { name: section })
      .click()
    await capture(page, theme, 'interview', `settings-${index + 1}-${sectionSlug(section)}`)
  }
  await page.keyboard.press('Escape')
  await expect(page.getByRole('dialog', { name: '全局设置' })).toHaveCount(0)
}

/** The resume assistant: empty, answered, one row expanded, whole trace collapsed. */
async function resumeWorkspaceFlow(page: Page, theme: Theme) {
  await start(page, theme)
  await logIn(page)
  await page.getByRole('button', { name: '简历', exact: true }).click()
  await expect(page).toHaveURL(/\/resume$/)
  await capture(page, theme, 'resume', 'workspace-empty')

  await page.getByLabel('简历制作指令').fill('把工作经历改成量化导向，突出接口性能结果。')
  await page.getByRole('button', { name: '发送', exact: true }).click()
  await expect(page.getByText('简历制作助手')).toBeVisible()
  await expect(page.getByRole('button', { name: /思考 2轮/ })).toBeVisible()
  for (const tool of ['思考了 3s', '搜索网络', '查找', '改写工作经历', '写入']) {
    await expect(page.getByText(tool, { exact: true }).first()).toBeVisible()
  }
  for (const target of ['*.md', 'resume-context', '接口性能指标写法']) {
    await expect(page.locator('.tool-trace__chip').filter({ hasText: target })).toBeVisible()
  }
  // One turn, one trace, one file chip: a leaked prior turn would render a second of each and
  // the frame would still look plausible.
  await expect(page.locator('.tool-trace')).toHaveCount(1)
  await expect(page.locator('.tool-trace__file')).toHaveCount(1)
  await expect(page.locator('.tool-trace__file')).toContainText('resume.md')
  await capture(page, theme, 'resume', 'workspace-toolcall')

  await page.getByRole('button', { name: '改写工作经历' }).click()
  await expect(page.getByText('+ P99 从 480ms 降到 210ms')).toBeVisible()
  await capture(page, theme, 'resume', 'workspace-toolcall-row')

  await page.getByRole('button', { name: /思考 2轮/ }).click()
  await expect(page.locator('.tool-trace')).toHaveAttribute('data-open', 'false')
  await capture(page, theme, 'resume', 'workspace-toolcall-collapsed')
  await page.getByRole('button', { name: /思考 2轮/ }).click()
  await expect(page.locator('.tool-trace')).toHaveAttribute('data-open', 'true')
}

/** Anonymous pages, the gallery, and the shared primitive surfaces. */
async function terminalStatesFlow(page: Page, theme: Theme) {
  await start(page, theme)
  await page.getByRole('button', { name: '注册', exact: true }).click()
  await expect(page.getByRole('heading', { level: 1, name: '创建工作台账号' })).toBeVisible()
  await capture(page, theme, 'interview', 'register')

  await page.goto('/no-such-route')
  await expect(page.getByRole('heading', { name: '页面不存在' })).toBeVisible()
  await capture(page, theme, 'interview', 'not-found')

  await captureLab(page, theme, 'overview')

  await page.goto('/components-lab')
  await expect(page.getByRole('heading', { name: 'Component Lab' })).toBeVisible()
  const menuTrigger = page.getByRole('button', { name: '菜单触发器', exact: true })
  await menuTrigger.scrollIntoViewIfNeeded()
  await menuTrigger.click()
  await hoverContextMenuItem(page, '子菜单')
  await expect(page.getByRole('menuitemradio', { name: '单选项一' })).toBeVisible()
  await capture(page, theme, 'lab', 'menu-submenu', { pointerIsState: true })
  await page.keyboard.press('Escape')
  await page.keyboard.press('Escape')
  await expect(page.getByRole('menu')).toHaveCount(0)

  await page.getByRole('button', { name: '打开工作台浮层' }).click()
  await expect(page.getByRole('dialog', { name: '工作台浮层' })).toBeVisible()
  await capture(page, theme, 'lab', 'dialog-workspace')
  await page.keyboard.press('Escape')

  await page.getByRole('button', { name: '危险确认' }).click()
  await expect(page.getByRole('button', { name: '危险操作' })).toBeVisible()
  await capture(page, theme, 'lab', 'confirm-destructive')
  await page.getByRole('button', { name: '取消' }).click()

  await page.getByRole('button', { name: '警告', exact: true }).click()
  await capture(page, theme, 'lab', 'toast-warning')
  await page.locator('.ui-toast__close').click()
  await expect(page.locator('.ui-toast')).toHaveCount(0)

  // The ⓘ icon button is the tooltip's only trigger; asserting the count keeps a second
  // "提示" button from silently retargeting this frame onto a text button.
  const tooltipTrigger = page.getByRole('button', { name: '提示', exact: true })
  await expect(tooltipTrigger).toHaveCount(1)
  await tooltipTrigger.hover()
  await expect(page.locator('.ui-tooltip')).toBeVisible()
  await capture(page, theme, 'lab', 'tooltip-icon', { pointerIsState: true })
}

for (const theme of themes) {
  test.describe(`@capture surface reference set · ${theme}`, () => {
    test('interview workspace', async ({ page }) => {
      await interviewWorkspaceFlow(page, theme)
    })

    test('interview product surfaces', async ({ page }) => {
      await interviewProductFlow(page, theme)
    })

    test('resume workspace', async ({ page }) => {
      await resumeWorkspaceFlow(page, theme)
    })

    test('terminal states and shared primitives', async ({ page }) => {
      await terminalStatesFlow(page, theme)
    })
  })
}

test.afterAll(async () => {
  await mkdir(surfaceDirectory, { recursive: true })
  const revision = execFileSync('git', ['rev-parse', 'HEAD'], { encoding: 'utf8' }).trim()
  const inputs = execFileSync('git', ['status', '--porcelain'], { encoding: 'utf8' })
    .split('\n')
    .filter((line) => line.trim() && !line.includes('docs/screenshots/'))
  if (inputs.length) {
    console.warn(
      `capture:surfaces ran on a dirty tree: ${inputs.length} input file(s) differ from ${revision.slice(0, 7)}`,
    )
  }

  /* The manifest lists what is on disk, not what this worker remembers. Built from the
     in-memory capture list it silently under-reported whenever a worker restarted or the suite
     ran in parallel — the file claimed eight frames while thirty-two were committed. */
  const onDisk = await listSurfaces(surfaceDirectory)
  const missing = captured.filter((relative) => !onDisk.has(relative))
  if (missing.length) {
    throw new Error(`captured in this run but absent on disk: ${missing.join(', ')}`)
  }

  /* Both themes are produced by the same flow, so a frame that exists in one and not the other
     is a capture that failed on one side — exactly how the dark set fell behind the light one.
     The comparison is on the `area/name` tail: the theme prefix differs by construction, so
     comparing full paths would call every run asymmetric. */
  const perTheme = new Map(
    themes.map((theme) => [
      theme,
      framesOfTheme(onDisk, theme).map((surface) => surface.slice(theme.length + 1)),
    ]),
  )
  const reference = [...perTheme.get(themes[0])!].sort()
  for (const theme of themes.slice(1)) {
    const frames = [...perTheme.get(theme)!].sort()
    const asymmetry = [
      ...reference.filter((f) => !frames.includes(f)).map((f) => `only in ${themes[0]}: ${f}`),
      ...frames.filter((f) => !reference.includes(f)).map((f) => `only in ${theme}: ${f}`),
    ]
    if (asymmetry.length) throw new Error(`theme parity broken: ${asymmetry.join(', ')}`)
  }

  await writeFile(
    path.join(surfaceDirectory, 'manifest.json'),
    `${JSON.stringify(
      {
        revision,
        capturedAt: new Date().toISOString(),
        inputsMatchRevision: inputs.length === 0,
        dirtyInputFiles: inputs.length,
        layout: '<theme>/<area>/<name>.png',
        themes: [...themes],
        framesPerTheme: Object.fromEntries(
          themes.map((theme) => [theme, perTheme.get(theme)!.length]),
        ),
        surfaces: [...onDisk].sort(),
      },
      null,
      2,
    )}\n`,
    'utf8',
  )
})

async function listSurfaces(root: string): Promise<Set<string>> {
  const found = new Set<string>()
  for (const theme of themes) {
    for (const area of ['interview', 'resume', 'lab'] as const) {
      const directory = path.join(root, theme, area)
      let entries: string[]
      try {
        entries = await readdir(directory)
      } catch {
        continue
      }
      for (const entry of entries) {
        if (entry.endsWith('.png')) found.add(`${theme}/${area}/${entry}`)
      }
    }
  }
  return found
}

function framesOfTheme(surfaces: Set<string>, theme: Theme): string[] {
  return [...surfaces].filter((surface) => surface.startsWith(`${theme}/`))
}

function sectionSlug(section: string) {
  if (section === '账号资料') return 'profile'
  if (section === '简历管理') return 'resumes'
  if (section === '岗位管理') return 'positions'
  if (section === '模型管理') return 'llm'
  return 'theme'
}

async function hoverContextMenuItem(page: Page, menuLabel: string) {
  await page.getByRole('menuitem', { name: menuLabel }).hover()
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
