const { spawn, spawnSync } = require('node:child_process')
const fs = require('node:fs')
const net = require('node:net')
const path = require('node:path')
const { chromium } = require('playwright')

const rootDir = path.resolve(__dirname, '..')
const screenshotDir = path.resolve(rootDir, '..', 'output', 'playwright')
const COLD_START_TIMEOUT_MS = 60000

function findBrowserExecutable() {
  const candidates = [
    process.env.PLAYWRIGHT_CHROMIUM_EXECUTABLE_PATH,
    'C:/Program Files/Google/Chrome/Application/chrome.exe',
    'C:/Program Files (x86)/Google/Chrome/Application/chrome.exe',
    path.join(process.env.LOCALAPPDATA || '', 'Google/Chrome/Application/chrome.exe'),
    'C:/Program Files/Microsoft/Edge/Application/msedge.exe',
    'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe',
  ].filter(Boolean)

  return candidates.find((candidate) => fs.existsSync(candidate))
}

function waitForPort(port, timeoutMs = 30000) {
  const started = Date.now()
  return new Promise((resolve, reject) => {
    const tryConnect = () => {
      const socket = net.connect(port, '127.0.0.1')
      socket.once('connect', () => {
        socket.end()
        resolve()
      })
      socket.once('error', () => {
        socket.destroy()
        if (Date.now() - started > timeoutMs) {
          reject(new Error(`Timed out waiting for port ${port}`))
          return
        }
        setTimeout(tryConnect, 300)
      })
    }
    tryConnect()
  })
}

async function startVite(port) {
  const command = process.platform === 'win32' ? 'cmd.exe' : 'npm'
  const args = process.platform === 'win32'
    ? ['/d', '/s', '/c', 'npm', 'run', 'dev', '--', '--host', '127.0.0.1', '--port', String(port), '--strictPort']
    : ['run', 'dev', '--', '--host', '127.0.0.1', '--port', String(port), '--strictPort']
  const child = spawn(command, args, {
    cwd: rootDir,
    env: {
      ...process.env,
      VITE_HOST: '127.0.0.1',
      VITE_PORT: String(port),
      VITE_PROXY_TARGET: 'http://127.0.0.1:8080',
    },
    stdio: ['ignore', 'pipe', 'pipe'],
    windowsHide: true,
  })
  child.stdout.on('data', (data) => process.stdout.write(data))
  child.stderr.on('data', (data) => process.stderr.write(data))
  await waitForPort(port)
  return child
}

function stopProcess(child) {
  if (!child || child.killed) return
  if (process.platform === 'win32') {
    spawnSync('taskkill', ['/pid', String(child.pid), '/T', '/F'], { stdio: 'ignore', windowsHide: true })
    return
  }
  child.kill('SIGTERM')
}

function ok(data) {
  return { code: 200, message: 'OK', data }
}

async function captureDiagnostics(page, label) {
  const safe = (text) => String(text || '').slice(0, 2000)
  return {
    label,
    url: page.url(),
    title: await page.title().catch(() => ''),
    appChildren: await page.evaluate(() => document.querySelector('#app')?.children.length || 0),
    hasBrandHost: await page.evaluate(() => Boolean(document.querySelector('.brand-metaballs'))),
    hasBrandCanvas: await page.evaluate(() => Boolean(document.querySelector('.brand-metaballs canvas'))),
    localStorageAuth: await page.evaluate(() => localStorage.getItem('auth')),
    localStorageTheme: await page.evaluate(() => localStorage.getItem('prelude-theme')),
    localStorageKeys: await page.evaluate(() => Object.keys(localStorage)),
    bodyText: safe(await page.evaluate(() => document.body?.innerText || '')),
  }
}

function printDiagnostics(diagnostics) {
  console.error('--- verify:dark diagnostics ---')
  console.error(JSON.stringify(diagnostics, null, 2))
}

async function saveFailureScreenshot(page, label) {
  try {
    fs.mkdirSync(screenshotDir, { recursive: true })
    const filePath = path.join(screenshotDir, 'dark-failure.png')
    await page.screenshot({ path: filePath, fullPage: true })
    console.error(`Saved failure screenshot to ${filePath}`)
  } catch (screenshotError) {
    console.error(`Failed to capture screenshot (${label}):`, screenshotError.message)
  }
}

async function waitForAppMounted(page) {
  await page.waitForFunction(
    () => Boolean(document.querySelector('#app')?.children?.length),
    null,
    { timeout: COLD_START_TIMEOUT_MS },
  )
}

async function installApiMocks(page) {
  await page.route('**/*', async (route) => {
    const url = new URL(route.request().url())
    if (!url.pathname.startsWith('/api/')) return route.continue()

    const pathname = url.pathname.replace(/^\/api/, '')
    const method = route.request().method()

    if (method === 'GET' && pathname === '/user/profile') {
      return route.fulfill({
        json: ok({
          username: 'demo',
          email: 'demo@example.com',
          avatarUrl: '',
          themePreference: 'dark',
        }),
      })
    }
    if (method === 'PUT' && pathname === '/user/profile') {
      const body = route.request().postDataJSON()
      return route.fulfill({
        json: ok({
          username: 'demo',
          email: 'demo@example.com',
          avatarUrl: '',
          themePreference: body.themePreference || 'dark',
        }),
      })
    }
    if (method === 'GET' && pathname === '/interview/sessions') return route.fulfill({ json: ok([]) })
    if (method === 'POST' && pathname === '/interview/start') return route.fulfill({ json: ok({ sessionId: 100 }) })
    if (method === 'GET' && pathname === '/interview/100/messages') {
      return route.fulfill({
        json: ok({
          sessionId: 100,
          targetPosition: '前端工程师',
          status: 'ongoing',
          currentStage: 'technical',
          llmProvider: 'DeepSeek',
          llmModel: 'deepseek-chat',
          summaryReport: '',
          stages: [
            { stageName: 'warmup', status: 'finished' },
            { stageName: 'technical', status: 'active' },
          ],
          messages: [
            { id: 1, role: 'assistant', content: '我们先聊一个组件状态同步问题。', createdAt: '2026-04-23T14:00:00' },
          ],
        }),
      })
    }
    if (method === 'GET' && pathname === '/resume/list') {
      return route.fulfill({
        json: ok([{ id: 1, fileName: '高级前端工程师-长文件名简历.pdf' }]),
      })
    }
    if (method === 'GET' && pathname === '/position/list') {
      return route.fulfill({
        json: ok([
          { id: 1, name: 'Java 后端工程师' },
          { id: 2, name: '前端工程师' },
          { id: 3, name: '算法工程师' },
        ]),
      })
    }
    if (method === 'GET' && pathname === '/llm/providers') {
      return route.fulfill({
        json: ok([
          { providerKey: 'deepseek', displayName: 'DeepSeek', models: ['deepseek-chat', 'deepseek-reasoner'] },
        ]),
      })
    }
    if (method === 'GET' && pathname === '/user/llm-config') {
      return route.fulfill({
        json: ok({
          providerKey: 'deepseek',
          baseUrl: null,
          model: 'deepseek-chat',
          hasApiKey: false,
          apiKeyMasked: '',
          displayName: 'DeepSeek',
        }),
      })
    }
    if (method === 'GET' && pathname === '/analytics/radar') {
      return route.fulfill({ json: ok({ technical: 7.8, expression: 7.1, logic: 8.2, sessionCount: 4 }) })
    }
    if (method === 'GET' && pathname === '/analytics/trend') {
      return route.fulfill({
        json: ok([
          { sessionId: 1, createdAt: '2026-04-18T15:30:00', technical: 6, expression: 7, logic: 7 },
          { sessionId: 2, createdAt: '2026-04-20T16:10:00', technical: 7, expression: 7, logic: 8 },
          { sessionId: 3, createdAt: '2026-04-22T10:00:00', technical: 8, expression: 7, logic: 8 },
          { sessionId: 4, createdAt: '2026-04-23T14:00:00', technical: 8, expression: 8, logic: 9 },
        ]),
      })
    }
    if (method === 'GET' && pathname === '/analytics/weaknesses') {
      return route.fulfill({
        json: ok([
          { category: '性能量化', count: 2, descriptions: ['回答需要补充指标口径和压测数据。'] },
        ]),
      })
    }
    if (method === 'GET' && pathname === '/resume/download/1') {
      return route.fulfill({ status: 204 })
    }

    return route.fulfill({ json: ok(null) })
  })
}

async function assertNoDarkConsoleErrors(page, action) {
  const messages = []
  const listener = (message) => {
    if (message.type() === 'error') messages.push(message.text())
  }
  page.on('console', listener)
  await action()
  page.off('console', listener)
  const unexpected = messages.filter((message) => !message.includes('Failed to load resource'))
  if (unexpected.length) {
    throw new Error(`Unexpected console errors: ${unexpected.join('\n')}`)
  }
}

async function assertCanvasNonBlank(page, selector, timeout = 10000) {
  await page.waitForSelector(selector, { state: 'visible', timeout })
  const nonBlank = await page.locator(selector).first().evaluate((canvas) => {
    const webgl = canvas.getContext('webgl2') || canvas.getContext('webgl')
    if (webgl) {
      return canvas.getBoundingClientRect().width > 0
        && canvas.getBoundingClientRect().height > 0
        && webgl.drawingBufferWidth > 0
        && webgl.drawingBufferHeight > 0
    }
    const context = canvas.getContext('2d')
    if (!context) return false
    const { width, height } = canvas
    const data = context.getImageData(0, 0, width, height).data
    for (let index = 0; index < data.length; index += 64) {
      if (data[index + 3] !== 0 || data[index] !== 0 || data[index + 1] !== 0 || data[index + 2] !== 0) {
        return true
      }
    }
    return false
  })
  if (!nonBlank) throw new Error(`${selector} should render nonblank canvas pixels`)
}

async function assertDarkInputTextFill(page) {
  const inputTextFill = await page.getByPlaceholder('请输入用户名').evaluate((input) => {
    const style = window.getComputedStyle(input)
    return {
      fill: style.webkitTextFillColor,
      primary: window.getComputedStyle(document.documentElement).getPropertyValue('--color-text-primary').trim(),
    }
  })
  if (!inputTextFill.fill || inputTextFill.fill === 'rgb(0, 0, 0)') {
    throw new Error(`Dark login input text fill should not be black, got ${inputTextFill.fill}`)
  }
}

async function assertBrandMetaballsThemeUpdate(page) {
  const darkPalette = await page.evaluate(() => {
    const style = window.getComputedStyle(document.documentElement)
    return [
      style.getPropertyValue('--brand-metaballs-1').trim(),
      style.getPropertyValue('--brand-metaballs-2').trim(),
      style.getPropertyValue('--brand-metaballs-3').trim(),
      style.getPropertyValue('--brand-metaballs-4').trim(),
      style.getPropertyValue('--brand-metaballs-5').trim(),
      style.getPropertyValue('--brand-metaballs-bg').trim(),
    ].join('|')
  })

  await page.evaluate(() => {
    window.__preludeBrandCanvas = document.querySelector('.brand-metaballs canvas')
  })

  await page.evaluate(() => {
    document.documentElement.classList.remove('dark')
    document.documentElement.dataset.theme = 'light'
    window.dispatchEvent(new CustomEvent('prelude-theme-change', { detail: { theme: 'light' } }))
  })

  await page.waitForFunction(() => {
    const canvas = document.querySelector('.brand-metaballs canvas')
    return canvas && canvas !== window.__preludeBrandCanvas
  }, null, { timeout: COLD_START_TIMEOUT_MS })

  const lightPalette = await page.evaluate(() => {
    const style = window.getComputedStyle(document.documentElement)
    return [
      style.getPropertyValue('--brand-metaballs-1').trim(),
      style.getPropertyValue('--brand-metaballs-2').trim(),
      style.getPropertyValue('--brand-metaballs-3').trim(),
      style.getPropertyValue('--brand-metaballs-4').trim(),
      style.getPropertyValue('--brand-metaballs-5').trim(),
      style.getPropertyValue('--brand-metaballs-bg').trim(),
    ].join('|')
  })

  if (!darkPalette || !lightPalette || darkPalette === lightPalette) {
    throw new Error('BrandMetaballs palette tokens should change between dark and light themes')
  }
}

async function chartFrame(page) {
  return page.locator('.chart-surface canvas').first().evaluate((canvas) => canvas.toDataURL())
}

async function verifyDarkFlow(port) {
  const executablePath = findBrowserExecutable()
  const browser = await chromium.launch(executablePath ? { headless: true, executablePath } : { headless: true })
  const page = await browser.newPage({ viewport: { width: 1440, height: 1000 }, deviceScaleFactor: 1 })

  page.on('pageerror', (err) => {
    console.error(`[page error] ${err.message}`)
  })

  await installApiMocks(page)
  await page.addInitScript(() => {
    localStorage.setItem('prelude-theme', 'dark')
  })

  try {
    await assertNoDarkConsoleErrors(page, async () => {
      await page.goto(`http://127.0.0.1:${port}/login`, { waitUntil: 'domcontentloaded', timeout: COLD_START_TIMEOUT_MS })
      await waitForAppMounted(page)
      await page.waitForSelector('html.dark', { timeout: COLD_START_TIMEOUT_MS })
      await assertCanvasNonBlank(page, '.brand-metaballs canvas', COLD_START_TIMEOUT_MS)
      await assertDarkInputTextFill(page)
      await assertBrandMetaballsThemeUpdate(page)
    })

    await page.addInitScript(() => {
      localStorage.setItem('auth', JSON.stringify({ token: 'playwright-token' }))
      localStorage.setItem('prelude-theme', 'dark')
    })
    await assertNoDarkConsoleErrors(page, async () => {
      await page.goto(`http://127.0.0.1:${port}/interview`, { waitUntil: 'domcontentloaded', timeout: COLD_START_TIMEOUT_MS })
      await waitForAppMounted(page)
      await page.getByRole('button', { name: '设置' }).waitFor({ state: 'visible', timeout: COLD_START_TIMEOUT_MS })

      await page.getByRole('button', { name: '设置' }).click()
      await page.getByRole('button', { name: '账号资料' }).click()
      await page.waitForFunction(() => document.querySelector('input[autocomplete="email"]')?.value === 'demo@example.com')
      await page.getByRole('button', { name: '主题' }).click()
      await page.getByRole('button', { name: '暗色' }).click()
      await page.getByRole('button', { name: '保存主题' }).click()
      await page.waitForFunction(() => document.body.innerText.includes('未检测到主题变更') || document.body.innerText.includes('主题已保存'))
      await page.getByRole('button', { name: 'LLM 配置' }).click()
      await page.getByText('接入方式').waitFor({ state: 'visible', timeout: 10000 })
      await page.keyboard.press('Escape')

      await page.locator('button').filter({ has: page.locator('.lucide-terminal') }).first().click()
      const modelContent = page.locator('[role="menu"]').filter({ hasText: 'deepseek-chat' }).last()
      await modelContent.waitFor({ state: 'visible', timeout: 10000 })
      const modelBox = await modelContent.boundingBox()
      const triggerBox = await page.locator('button').filter({ has: page.locator('.lucide-terminal') }).first().boundingBox()
      if (modelBox.width + 1 < triggerBox.width) {
        throw new Error(`Model dropdown width (${modelBox.width}) should not be narrower than trigger (${triggerBox.width})`)
      }
      await page.getByRole('menuitem', { name: 'deepseek-reasoner' }).click()

      await page.getByRole('button', { name: '开始面试' }).click()
      await page.waitForFunction(() => document.body.innerText.includes('面试已创建'))
      await page.getByRole('button', { name: '切换到语音输入' }).click()
      await page.getByRole('button', { name: '按住说话' }).waitFor({ state: 'visible', timeout: 10000 })
      await page.getByRole('button', { name: '切换到文字输入' }).click()
    })

    await assertNoDarkConsoleErrors(page, async () => {
      await page.goto(`http://127.0.0.1:${port}/analytics`, { waitUntil: 'domcontentloaded', timeout: COLD_START_TIMEOUT_MS })
      await waitForAppMounted(page)
      await page.getByText('能力雷达').waitFor({ state: 'visible', timeout: 30000 })
      await page.waitForSelector('.chart-surface canvas', { state: 'visible', timeout: 10000 })
      await assertCanvasNonBlank(page, '.chart-surface canvas')
      await page.evaluate(() => {
        window.__preludeThemeEvents = 0
        window.addEventListener('prelude-theme-change', () => {
          window.__preludeThemeEvents += 1
        })
      })

      await page.getByRole('button', { name: '设置' }).click()
      await page.getByRole('button', { name: '主题' }).click()
      const darkFrame = await chartFrame(page)
      await page.getByRole('button', { name: '浅色' }).click()
      await page.waitForSelector('html:not(.dark)', { timeout: 10000 })
      await page.waitForFunction((previous) => {
        const canvas = document.querySelector('.chart-surface canvas')
        return window.__preludeThemeEvents >= 1 && canvas && canvas.toDataURL() !== previous
      }, darkFrame)
      const lightFrame = await chartFrame(page)
      await page.getByRole('button', { name: '暗色' }).click()
      await page.waitForSelector('html.dark', { timeout: 10000 })
      await page.waitForFunction((previous) => {
        const canvas = document.querySelector('.chart-surface canvas')
        return window.__preludeThemeEvents >= 2 && canvas && canvas.toDataURL() !== previous
      }, lightFrame)
      await page.keyboard.press('Escape')
      await assertCanvasNonBlank(page, '.chart-surface canvas')
    })
  } catch (error) {
    const diagnostics = await captureDiagnostics(page, 'dark-flow-failure')
    printDiagnostics(diagnostics)
    await saveFailureScreenshot(page, diagnostics.label)
    throw error
  } finally {
    await browser.close()
  }
}

async function main() {
  const port = Number(process.env.DARK_VERIFY_PORT || 5176)
  const vite = await startVite(port)
  try {
    await verifyDarkFlow(port)
  } finally {
    stopProcess(vite)
  }
}

main().catch((error) => {
  console.error(error)
  process.exit(1)
})
