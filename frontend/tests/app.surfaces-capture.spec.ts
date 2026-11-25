import { mkdir, readdir, readFile, rename, rm, writeFile } from 'node:fs/promises'
import path from 'node:path'
import { createRequire } from 'node:module'
import { fileURLToPath } from 'node:url'
import { expect, test, type Page } from '@playwright/test'
import {
  createDemoState,
  DEMO_VIEWPORT,
  installDemoHarness,
  installVoiceLane,
  pushVoiceFrame,
  releaseVoiceAudio,
} from './demo-harness'

type Theme = 'light' | 'dark'
type Area = 'interview' | 'lab'

type SurfaceManifest = {
  inputRevision: string
  capturedAt: string
  layout: string
  themes: Theme[]
  framesPerTheme: Record<string, number>
  surfaces: string[]
  surfaceHashes: Record<string, string>
}

type BuildSurfaceManifestOptions = {
  previousManifest?: SurfaceManifest
  screenshotsRoot: string
  inputRevision: string
  capturedAt?: string
  themes: readonly Theme[]
  framesPerTheme: Record<string, number>
  surfaces: readonly string[]
}

const { buildSurfaceManifest, computeInputRevision, dirtyInputFiles } = createRequire(
  import.meta.url,
)('../scripts/surface-manifest.cjs') as {
  buildSurfaceManifest: (options: BuildSurfaceManifestOptions) => SurfaceManifest
  computeInputRevision: () => string
  dirtyInputFiles: () => number
}

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
  await disableCssMotion(page)
  return state
}

async function disableCssMotion(page: Page) {
  await page.addStyleTag({
    content: '*,*::before,*::after{transition:none!important;animation:none!important}',
  })
}

async function logIn(page: Page) {
  await page.getByLabel('用户名').fill('demo')
  await page.getByLabel('密码', { exact: true }).fill('123456')
  await page.locator('form').getByRole('button', { name: '登录', exact: true }).click()
  await expect(page).toHaveURL(/\/interview$/)
}

// Resting frames move the pointer away unless hover or an open menu is the subject.
async function visuallyEquivalent(page: Page, existing: string, candidate: string) {
  let previous
  try {
    previous = await readFile(existing)
  } catch (error) {
    if ((error as NodeJS.ErrnoException).code === 'ENOENT') return false
    throw error
  }
  const current = await readFile(candidate)
  if (previous.equals(current)) return true
  return page.evaluate(
    async ({ previous, current }) => {
      const decode = async (value: string) => {
        const bytes = Uint8Array.from(atob(value), (character) => character.charCodeAt(0))
        const bitmap = await createImageBitmap(new Blob([bytes], { type: 'image/png' }))
        const canvas = new OffscreenCanvas(bitmap.width, bitmap.height)
        const context = canvas.getContext('2d', { willReadFrequently: true })!
        context.drawImage(bitmap, 0, 0)
        const image = context.getImageData(0, 0, bitmap.width, bitmap.height)
        bitmap.close()
        return image
      }
      const before = await decode(previous)
      const after = await decode(current)
      if (before.width !== after.width || before.height !== after.height) return false
      let changed = 0
      for (let index = 0; index < before.data.length; index += 4) {
        let delta = 0
        for (let channel = 0; channel < 4; channel += 1) {
          delta = Math.max(
            delta,
            Math.abs(before.data[index + channel] - after.data[index + channel]),
          )
        }
        if (delta > 2) return false
        if (delta > 0) changed += 1
      }
      // Chromium can vary antialiased pixels by two RGB levels between identical runs.
      return changed / (before.width * before.height) <= 0.001
    },
    { previous: previous.toString('base64'), current: current.toString('base64') },
  )
}

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
  const temporary = `${target}.${process.pid}.png`
  try {
    await page.screenshot({
      path: temporary,
      fullPage: options.fullPage ?? false,
      animations: 'disabled',
    })
    if (!(await visuallyEquivalent(page, target, temporary))) {
      await rename(temporary, target)
    }
  } finally {
    await rm(temporary, { force: true })
  }
  captured.push(relative)
}

async function settle(page: Page) {
  await page.evaluate(async () => {
    await document.fonts.ready
    await Promise.all(Array.from(document.images).map((image) => image.decode().catch(() => {})))
    const finiteAnimations = document
      .getAnimations()
      .filter((animation) => animation.effect?.getComputedTiming().iterations !== Infinity)
    await Promise.all(finiteAnimations.map((animation) => animation.finished.catch(() => {})))
    await new Promise((resolve) => requestAnimationFrame(() => resolve(null)))
    await new Promise((resolve) => requestAnimationFrame(() => resolve(null)))
  })
}

async function captureLab(page: Page, theme: Theme, name: string) {
  await page.emulateMedia({ reducedMotion: 'no-preference' })
  await page.goto('/components-lab')
  await expect(page.getByRole('heading', { name: 'Component Lab' })).toBeVisible()
  await disableCssMotion(page)
  const rose = page.locator('[data-slot="rose-three"]')
  await expect(rose).toHaveAttribute('transform', /rotate\(/)
  const firstFrame = await rose.getAttribute('transform')
  await expect.poll(() => rose.getAttribute('transform')).not.toBe(firstFrame)
  await page.emulateMedia({ reducedMotion: 'reduce' })
  await expect(rose).toHaveAttribute('transform', 'rotate(0.0 50 50)')
  await settle(page)
  // Expand the lab scroll shell so fullPage captures the complete gallery.
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
  await releaseVoiceAudio(page)

  await pushVoiceFrame(page, { type: 'error', message: '语音服务异常' })
  await expect(page.getByRole('button', { name: '切换到语音输入' })).toBeVisible()
  await expect(page.getByRole('button', { name: '切换到语音输入' })).toBeEnabled()
  await expect(page.getByRole('button', { name: '发送' })).toBeEnabled()
  await expect(holdToTalk).toBeHidden()
  await capture(page, theme, 'interview', 'voice-fallback')
}

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
    const dialog = page.getByRole('dialog', { name: '全局设置' })
    await dialog.getByRole('button', { name: section }).click()
    await expect(dialog.getByText(/正在读取|正在加载设置/)).toHaveCount(0)
    await capture(page, theme, 'interview', `settings-${index + 1}-${sectionSlug(section)}`)
  }
  await page.keyboard.press('Escape')
  await expect(page.getByRole('dialog', { name: '全局设置' })).toHaveCount(0)
}

async function terminalStatesFlow(page: Page, theme: Theme) {
  await start(page, theme)
  await page.getByRole('button', { name: '注册', exact: true }).click()
  await expect(page.getByRole('heading', { level: 1, name: '创建工作台账号' })).toBeVisible()
  await capture(page, theme, 'interview', 'register')

  await page.goto('/no-such-route')
  await expect(page.getByRole('heading', { name: '页面不存在' })).toBeVisible()
  await capture(page, theme, 'interview', 'not-found')

  await captureLab(page, theme, 'overview')
}

for (const theme of themes) {
  test.describe(`@capture surface reference set · ${theme}`, () => {
    test('interview workspace', async ({ page }) => {
      await interviewWorkspaceFlow(page, theme)
    })

    test('interview product surfaces', async ({ page }) => {
      await interviewProductFlow(page, theme)
    })

    test('terminal states and shared primitives', async ({ page }) => {
      await terminalStatesFlow(page, theme)
    })
  })
}

test.afterAll(async () => {
  await mkdir(surfaceDirectory, { recursive: true })
  const inputRevision = computeInputRevision()
  const dirtyInputs = dirtyInputFiles()
  if (dirtyInputs) {
    console.warn(`capture:surfaces ran with ${dirtyInputs} dirty frontend input file(s)`)
  }

  const onDisk = await listSurfaces(surfaceDirectory)
  const missing = captured.filter((relative) => !onDisk.has(relative))
  if (missing.length) {
    throw new Error(`captured in this run but absent on disk: ${missing.join(', ')}`)
  }
  const capturedSet = new Set(captured)
  const stale = [...onDisk].filter((relative) => !capturedSet.has(relative))
  if (stale.length) {
    throw new Error(`stale surface captures absent from this run: ${stale.join(', ')}`)
  }

  // Compare area/name tails so both themes must expose the same frames.
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

  const manifestFile = path.join(surfaceDirectory, 'manifest.json')
  let previousManifest: SurfaceManifest | undefined
  try {
    const parsed: unknown = JSON.parse(await readFile(manifestFile, 'utf8'))
    if (!parsed || typeof parsed !== 'object' || Array.isArray(parsed)) {
      throw new Error('surface manifest must be a JSON object')
    }
    previousManifest = parsed as SurfaceManifest
  } catch (error) {
    if ((error as NodeJS.ErrnoException).code !== 'ENOENT') throw error
  }
  const manifest = buildSurfaceManifest({
    previousManifest,
    screenshotsRoot: surfaceDirectory,
    inputRevision,
    themes,
    framesPerTheme: Object.fromEntries(themes.map((theme) => [theme, perTheme.get(theme)!.length])),
    surfaces: [...onDisk],
  })
  const temporaryManifest = `${manifestFile}.${process.pid}.tmp`
  try {
    await writeFile(temporaryManifest, `${JSON.stringify(manifest, null, 2)}\n`, 'utf8')
    await rename(temporaryManifest, manifestFile)
  } finally {
    await rm(temporaryManifest, { force: true })
  }
})

async function listSurfaces(root: string): Promise<Set<string>> {
  const found = new Set<string>()
  for (const theme of themes) {
    for (const area of ['interview', 'lab'] as const) {
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
