const { spawn, spawnSync } = require('node:child_process')
const fs = require('node:fs')
const net = require('node:net')
const path = require('node:path')
const { chromium } = require('playwright')

const rootDir = path.resolve(__dirname, '..')
const panelPath = path.join(rootDir, 'src', 'components', 'workspace', 'LlmSettingsPanel.vue')
const sharedDropdownPath = path.join(rootDir, 'src', 'components', 'ui', 'shared-dropdown.ts')
const baseUrl = 'https://api.tokenrouter.com/v1'
const apiKey = 'sk-tokenrouter-new'
const manualModel = 'manual-model-2026'
const expectedControlHeight = 34
const expectedCompactControlHeight = 30

function assertNoLegacyPanelMarkup() {
  const source = fs.readFileSync(panelPath, 'utf8')
  const inputTags = source.match(/<Input[\s\S]*?\/>/g) || []
  const mixed = inputTags.filter((tag) => tag.includes('v-model=') && tag.includes('v-bind="componentField"'))
  if (mixed.length > 0) {
    throw new Error(`Input must not combine v-model with v-bind="componentField"; found ${mixed.length}`)
  }
  const legacyTokens = [
    'data' + 'list',
    'model-' + 'suggestions',
    'model-' + 'suggestion',
    'model-combobox__' + 'item',
  ]
  if (legacyTokens.some((token) => source.includes(token))) {
    throw new Error('Legacy model suggestion implementation is still present')
  }
  if (source.includes('test-status-row')) {
    throw new Error('Panel test status row must be removed')
  }
  const dropdownSource = fs.readFileSync(sharedDropdownPath, 'utf8')
  if (!dropdownSource.includes('shadow-whisper') || dropdownSource.includes('shadow-md')) {
    throw new Error('Shared dropdown content must keep the low-elevation shadow token')
  }
  if (!dropdownSource.includes('border-transparent') || dropdownSource.includes('border-border')) {
    throw new Error('Shared dropdown content must not use a hard border token')
  }
}

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
  if (!child || child.killed) {
    return
  }
  if (process.platform === 'win32') {
    spawnSync('taskkill', ['/pid', String(child.pid), '/T', '/F'], { stdio: 'ignore', windowsHide: true })
    return
  }
  child.kill('SIGTERM')
}

function ok(data) {
  return { code: 200, message: 'OK', data }
}

async function verifyBrowserFlow(port) {
  const executablePath = findBrowserExecutable()
  const launchOptions = executablePath ? { headless: true, executablePath } : { headless: true }
  const browser = await chromium.launch(launchOptions)
  const page = await browser.newPage({ viewport: { width: 1440, height: 1000 }, deviceScaleFactor: 1 })
  const requests = {
    discover: [],
    test: [],
    save: [],
  }

  await page.route('**/*', async (route) => {
    const url = new URL(route.request().url())
    if (!url.pathname.startsWith('/api/')) {
      return route.continue()
    }

    const pathname = url.pathname.replace(/^\/api/, '')
    const method = route.request().method()
    const json = route.request().postDataJSON?.bind(route.request())

    if (method === 'GET' && pathname === '/interview/sessions') return route.fulfill({ json: ok([]) })
    if (method === 'GET' && pathname === '/resume/list') return route.fulfill({ json: ok([]) })
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
          { providerKey: 'openai-compatible', displayName: 'OpenAI 兼容接口', models: [] },
        ]),
      })
    }
    if (method === 'GET' && pathname === '/user/llm-config') {
      return route.fulfill({
        json: ok({
          providerKey: 'openai-compatible',
          baseUrl: 'https://old.example/v1',
          model: 'old-model',
          hasApiKey: true,
          apiKeyMasked: 'sk-***old',
          displayName: 'OpenAI 兼容接口',
        }),
      })
    }
    if (method === 'POST' && pathname === '/user/llm-config/discover-models') {
      requests.discover.push(json())
      return route.fulfill({
        json: ok({
          providerKey: 'openai-compatible',
          baseUrl,
          models: ['detected-model', 'detected-model-pro'],
        }),
      })
    }
    if (method === 'POST' && pathname === '/user/llm-config/test') {
      requests.test.push(json())
      return route.fulfill({ json: ok({ providerKey: 'openai-compatible', model: manualModel, ok: true, message: '模型配置测试通过' }) })
    }
    if (method === 'PUT' && pathname === '/user/llm-config') {
      requests.save.push(json())
      return route.fulfill({
        json: ok({
          providerKey: 'openai-compatible',
          baseUrl,
          model: manualModel,
          hasApiKey: true,
          apiKeyMasked: 'sk-***new',
          displayName: 'OpenAI 兼容接口',
        }),
      })
    }
    if (pathname === '/user/profile') return route.fulfill({ json: ok({ username: 'demo', email: 'demo@example.com' }) })

    return route.fulfill({ json: ok(null) })
  })

  await page.addInitScript(() => {
    localStorage.setItem('auth', JSON.stringify({ token: 'playwright-token' }))
  })

  try {
    await page.goto(`http://127.0.0.1:${port}/interview`, { waitUntil: 'domcontentloaded' })
    await page.getByRole('button', { name: '设置' }).waitFor({ state: 'visible', timeout: 30000 })

    await page.locator('button').filter({ has: page.locator('.lucide-briefcase') }).first().click()
    const positionOptionText = await page.getByRole('menuitem', { name: 'Java 后端工程师' }).innerText()
    if (!positionOptionText.includes('Java 后端工程师')) {
      throw new Error('Position list should render correct Chinese data')
    }
    await page.keyboard.press('Escape')

    const resumeTrigger = page.locator('button').filter({ has: page.locator('.lucide-file-text') }).first()
    const resumeTriggerBox = await resumeTrigger.boundingBox()
    if (Math.abs(resumeTriggerBox.height - expectedCompactControlHeight) > 1) throw new Error(`Resume trigger height is not compact, got ${resumeTriggerBox.height}`)

    await page.getByRole('button', { name: '设置' }).click()
    await page.getByRole('button', { name: 'LLM 配置' }).click()
    if (await page.locator('.panel-content-wrapper .text-destructive', { hasText: '请选择模型' }).count() !== 0) {
      throw new Error('Model validation error should not be shown before submit/test')
    }

    const providerSelect = page.locator('.field-grid').getByRole('combobox').first()
    await providerSelect.click()
    await page.getByRole('option', { name: 'DeepSeek' }).click()
    const builtInModelText = await page.locator('.field-grid').getByRole('combobox').nth(1).innerText()
    if (!builtInModelText.includes('请选择模型')) {
      throw new Error(`Built-in model field should reset to placeholder, got: ${builtInModelText}`)
    }

    await providerSelect.click()
    await page.getByRole('option', { name: '自定义 OpenAI 兼容接口' }).click()
    const modelInput = page.getByPlaceholder('请选择模型')
    if (await modelInput.inputValue() !== '') {
      throw new Error('OpenAI-compatible model input should reset after provider switch')
    }

    await page.getByPlaceholder('例如：https://api.deepseek.com/v1').fill(baseUrl)
    await page.getByPlaceholder('留空表示不修改当前 Key').fill(apiKey)
    const discoverButtonStyle = await page.getByRole('button', { name: '检测模型' }).evaluate(el => {
      const style = window.getComputedStyle(el)
      return { width: el.getBoundingClientRect().width, fontFamily: style.fontFamily }
    })
    if (discoverButtonStyle.width > 120) {
      throw new Error(`Discover button should use natural width, got ${discoverButtonStyle.width}`)
    }
    if (!discoverButtonStyle.fontFamily.toLowerCase().includes('lora') && !discoverButtonStyle.fontFamily.includes('Noto Serif')) {
      throw new Error(`Discover button should use serif font, got ${discoverButtonStyle.fontFamily}`)
    }
    await page.getByRole('button', { name: '检测模型' }).click()
    await page.waitForFunction(() => document.body.innerText.includes('模型列表已更新'))
    if (await modelInput.inputValue() !== '') {
      throw new Error('Model discovery must not auto-select the first model')
    }
    if (await page.locator('.test-status-row').count() !== 0) {
      throw new Error('Panel test status row is still rendered')
    }

    await modelInput.press('ArrowDown')
    await page.waitForSelector('[data-byok-model-combobox-content]', { state: 'visible', timeout: 5000 })
    await modelInput.press('Enter')
    await page.waitForFunction(() => {
      const input = document.querySelector('input[placeholder="请选择模型"]')
      return input?.value === 'detected-model'
    })
    await modelInput.press('ArrowDown')
    await page.waitForSelector('[data-byok-model-combobox-content]', { state: 'visible', timeout: 5000 })
    await modelInput.press('Escape')
    await page.waitForSelector('[data-byok-model-combobox-content]', { state: 'hidden', timeout: 5000 })

    await modelInput.fill(manualModel)
    await page.getByRole('button', { name: '测试连接' }).click({ force: true })
    await page.waitForFunction(() => document.body.innerText.includes('模型配置测试通过'))
    const panelText = await page.locator('.panel-content-wrapper').innerText()
    if (panelText.includes('模型配置测试通过') || panelText.includes('已通过') || panelText.includes('测试中')) {
      throw new Error('Panel still renders connection test status text')
    }

    await page.getByRole('button', { name: '保存设置' }).click({ force: true })
    await page.waitForFunction(() => document.body.innerText.includes('LLM 配置已保存'))

    await modelInput.click()
    await page.waitForSelector('[data-byok-model-combobox-content]', { state: 'visible', timeout: 5000 })
    const comboboxContent = page.locator('[data-byok-model-combobox-content]')
    const comboboxSurfaceStyle = await comboboxContent.evaluate(el => {
      const style = window.getComputedStyle(el)
      return { zIndex: style.zIndex, shadow: style.boxShadow, className: el.className }
    })
    if (comboboxSurfaceStyle.zIndex !== '105') {
      throw new Error(`Combobox content z-index should be 105, got ${comboboxSurfaceStyle.zIndex}`)
    }
    if (!comboboxSurfaceStyle.className.includes('border-transparent') || comboboxSurfaceStyle.className.includes('border-border')) {
      throw new Error('Combobox content should keep the shared low-border dropdown styling')
    }
    if (!comboboxSurfaceStyle.className.includes('shadow-whisper') || comboboxSurfaceStyle.className.includes('shadow-md')) {
      throw new Error('Combobox content should keep the shared low-elevation shadow styling')
    }
    if (comboboxSurfaceStyle.shadow === 'none') {
      throw new Error('Combobox content should render a dropdown shadow')
    }

    const legacyItemSelector = ['.model-combobox__' + 'item', '.model-' + 'suggestion'].join(', ')
    const legacyItemCount = await page.locator(legacyItemSelector).count()
    if (legacyItemCount !== 0) {
      throw new Error('Legacy raw suggestion item is still rendered')
    }
    const optionCount = await page.locator('[data-byok-model-combobox-item]').count()
    if (optionCount < 1) {
      throw new Error('Combobox items are not rendered')
    }

    const triggerBox = await page.locator('.model-combobox > *').first().boundingBox()
    if (Math.abs(triggerBox.height - expectedControlHeight) > 1) {
      throw new Error(`Combobox trigger height is not standard, got ${triggerBox.height}`)
    }

    const contentBox = await comboboxContent.boundingBox()
    if (Math.abs(contentBox.width - triggerBox.width) > 1) {
      throw new Error(`Dropdown content width (${contentBox.width}) does not match trigger width (${triggerBox.width})`)
    }

    const itemBox = await page.locator('[data-byok-model-combobox-item]').first().boundingBox()
    if (Math.abs(itemBox.height - expectedControlHeight) > 1) {
      throw new Error(`Dropdown item height is not standard, got ${itemBox.height}`)
    }
  } finally {
    await browser.close()
  }

  const discover = requests.discover.at(-1)
  const test = requests.test.at(-1)
  const save = requests.save.at(-1)

  if (discover?.baseUrl !== baseUrl) throw new Error(`discover baseUrl mismatch: ${discover?.baseUrl}`)
  if (discover?.apiKey !== apiKey) throw new Error('discover apiKey mismatch')
  if (test?.baseUrl !== baseUrl) throw new Error(`test baseUrl mismatch: ${test?.baseUrl}`)
  if (test?.model !== manualModel) throw new Error(`test model mismatch: ${test?.model}`)
  if (test?.apiKey !== apiKey) throw new Error('test apiKey mismatch')
  if (save?.baseUrl !== baseUrl) throw new Error(`save baseUrl mismatch: ${save?.baseUrl}`)
  if (save?.model !== manualModel) throw new Error(`save model mismatch: ${save?.model}`)
  if (save?.apiKey !== apiKey) throw new Error('save apiKey mismatch')
}

async function main() {
  assertNoLegacyPanelMarkup()
  const port = Number(process.env.BYOK_VERIFY_PORT || 5175)
  const vite = await startVite(port)
  try {
    await verifyBrowserFlow(port)
  } finally {
    stopProcess(vite)
  }
}

main().catch((error) => {
  console.error(error)
  process.exit(1)
})
