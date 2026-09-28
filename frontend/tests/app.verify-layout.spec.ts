import { writeFileSync } from 'node:fs'
import { expect, test } from '@playwright/test'
import { createDemoState, DEMO_VIEWPORT, installDemoHarness } from './demo-harness'

test.use({ viewport: DEMO_VIEWPORT })

/**
 * Throwaway probe: measure the tool trace's geometry so the alignment fixes are decided from
 * numbers rather than from a scaled screenshot. The pointer is parked away from the trace
 * first — a reference frame must show the resting state, not the hover slab.
 */
test('@verify measure tool trace alignment', async ({ page }) => {
  await installDemoHarness(page, createDemoState())
  await page.emulateMedia({ colorScheme: 'light', reducedMotion: 'reduce' })
  await page.goto('/login')
  await page.getByLabel('用户名').fill('demo')
  await page.getByLabel('密码', { exact: true }).fill('123456')
  await page.locator('form').getByRole('button', { name: '登录', exact: true }).click()
  await page.getByRole('button', { name: '简历', exact: true }).click()
  await page.getByLabel('简历制作指令').fill('把工作经历改成量化导向，突出接口性能结果。')
  await page.getByRole('button', { name: '发送', exact: true }).click()
  await expect(page.getByRole('button', { name: /思考 2轮/ })).toBeVisible()

  const measure = () =>
    page.evaluate(() => {
      const rect = (node: Element | null) => {
        if (!node) return null
        const box = node.getBoundingClientRect()
        return {
          left: Number(box.left.toFixed(2)),
          centerY: Number((box.top + box.height / 2).toFixed(2)),
          height: Number(box.height.toFixed(2)),
        }
      }
      const pick = (selector: string, root: ParentNode = document) =>
        rect(root.querySelector(selector))
      const style = (selector: string) => {
        const node = document.querySelector(selector)
        if (!node) return null
        const computed = getComputedStyle(node)
        return {
          background: computed.backgroundColor,
          paddingInlineStart: computed.paddingInlineStart,
          lineHeight: computed.lineHeight,
        }
      }
      return {
        summary: pick('.tool-trace__summary-text'),
        summaryStyle: style('.tool-trace__summary'),
        status: pick('.tool-trace__status'),
        statusStyle: style('.tool-trace__status'),
        rows: [...document.querySelectorAll('.tool-trace__item')].map((item) => ({
          label: item.querySelector('.tool-trace__text')?.textContent?.slice(0, 10),
          open: item.getAttribute('data-open'),
          row: rect(item.querySelector('.tool-trace__row')),
          icon: rect(item.querySelector('.tool-trace__row svg')),
          text: rect(item.querySelector('.tool-trace__text')),
          chip: rect(item.querySelector('.tool-trace__chip, .tool-trace__file')),
          rowStyle: item.querySelector('.tool-trace__row')
            ? {
                background: getComputedStyle(item.querySelector('.tool-trace__row') as Element)
                  .backgroundColor,
              }
            : null,
        })),
        detail: pick('.tool-trace__item[data-open="true"] .tool-trace__detail-body'),
      }
    })

  const resting = await measure()
  writeFileSync('test-results/probe-resting.json', JSON.stringify(resting, null, 2))

  await page.getByRole('button', { name: /改写工作经历/ }).click()
  await page.mouse.move(4, 4)
  await page.waitForTimeout(350)
  const expanded = await measure()
  writeFileSync('test-results/probe-expanded.json', JSON.stringify(expanded, null, 2))
  await page.screenshot({ path: 'test-results/probe-toolcall.png' })
})

/**
 * The running and finished marks must be distinguishable at a glance. The harness always answers
 * `done`, so the running group is forced here rather than baked into the shared sample.
 */
test('@verify capture running and finished trace marks', async ({ page }) => {
  await installDemoHarness(page, createDemoState())
  await page.emulateMedia({ colorScheme: 'light', reducedMotion: 'no-preference' })
  await page.route('**/api/resume/workspace/conversations/*/turns', async (route) => {
    if (route.request().method() !== 'GET') return route.fallback()
    await route.fulfill({
      contentType: 'application/json',
      body: JSON.stringify({
        code: 200,
        message: 'ok',
        data: [
          {
            id: 1,
            instruction: '把工作经历改成量化导向，突出接口性能结果。',
            status: 'running',
            createdAt: '2026-09-27T10:01:00+08:00',
            startedAt: '2026-09-27T10:01:00+08:00',
            completedAt: null,
            messages: [
              {
                id: 11,
                turnId: 1,
                content: '我先核对与本次指令相关的材料，再给出可落地的修改。',
                createdAt: '2026-09-27T10:01:02+08:00',
                toolCalls: {
                  id: 21,
                  summary: '思考 2轮 · 读1次文件、改1次文件、网络搜1次…',
                  status: 'running',
                  steps: [
                    { id: 31, icon: 'think', text: '思考了 3s' },
                    {
                      id: 32,
                      icon: 'write',
                      text: '写入 resume.md',
                      files: [{ name: 'resume.md', add: 22, del: 10 }],
                    },
                    {
                      id: 33,
                      icon: 'run',
                      text: '执行 npm run check',
                      state: 'error',
                      badge: '退出码 1',
                      badgeTone: 'error',
                    },
                  ],
                },
              },
            ],
          },
        ],
      }),
    })
  })
  await page.goto('/login')
  await page.getByLabel('用户名').fill('demo')
  await page.getByLabel('密码', { exact: true }).fill('123456')
  await page.locator('form').getByRole('button', { name: '登录', exact: true }).click()
  await page.getByRole('button', { name: '简历', exact: true }).click()
  await page.getByLabel('简历制作指令').fill('把工作经历改成量化导向，突出接口性能结果。')
  await page.getByRole('button', { name: '发送', exact: true }).click()
  await expect(page.getByRole('button', { name: /思考 2轮/ })).toBeVisible()
  await page.waitForTimeout(500)
  const status = page.locator('.tool-trace__status')
  await status.screenshot({ path: 'test-results/mark-running.png' })
  const geometry = await status.evaluate((node) => {
    const mark = node.querySelector('svg')?.getBoundingClientRect()
    const label = node.querySelector('span')?.getBoundingClientRect()
    return {
      running: node.getAttribute('data-running'),
      mark: mark && { w: mark.width, h: mark.height, top: mark.top },
      label: label && { w: label.width, h: label.height, top: label.top },
    }
  })
  writeFileSync('test-results/mark-running.json', JSON.stringify(geometry, null, 2))
  await page.unroute('**/api/resume/workspace/conversations/*/turns')
  await page.reload()
  await expect(page.getByRole('button', { name: /思考 2轮/ })).toBeVisible()
  await page.waitForTimeout(500)
  await page.locator('.tool-trace__status').screenshot({ path: 'test-results/mark-done.png' })
})

/**
 * Where is the expanded state's hairline, and what is it misaligned against? Reports the x of
 * every vertical border inside the trace next to each row's icon centre, in one resting frame.
 */
test('@verify locate trace rails against icon centres', async ({ page }) => {
  await installDemoHarness(page, createDemoState())
  await page.emulateMedia({ colorScheme: 'light', reducedMotion: 'reduce' })
  await page.goto('/login')
  await page.getByLabel('用户名').fill('demo')
  await page.getByLabel('密码', { exact: true }).fill('123456')
  await page.locator('form').getByRole('button', { name: '登录', exact: true }).click()
  await page.getByRole('button', { name: '简历', exact: true }).click()
  await page.getByLabel('简历制作指令').fill('把工作经历改成量化导向，突出接口性能结果。')
  await page.getByRole('button', { name: '发送', exact: true }).click()
  await expect(page.getByRole('button', { name: /思考 2轮/ })).toBeVisible()
  await page.getByRole('button', { name: '改写工作经历' }).click()
  await page.mouse.move(4, 4)
  await page.waitForTimeout(400)

  const report = await page.evaluate(() => {
    const round = (value: number) => Number(value.toFixed(2))
    const rails: Array<{ selector: string; left: number; width: number }> = []
    for (const el of document.querySelectorAll<HTMLElement>('.tool-trace, .tool-trace *')) {
      const style = getComputedStyle(el)
      const left = Number.parseFloat(style.borderLeftWidth) || 0
      if (left < 1) continue
      const box = el.getBoundingClientRect()
      rails.push({
        selector: `${el.tagName.toLowerCase()}.${(el.getAttribute('class') ?? '').split(/\s+/)[0]}`,
        left: round(box.left),
        width: round(box.width),
      })
    }
    const rows = [...document.querySelectorAll<HTMLElement>('.tool-trace__item')].map((item) => {
      const icon = item.querySelector<HTMLElement>('.tool-trace__mark')?.getBoundingClientRect()
      const text = item.querySelector<HTMLElement>('.tool-trace__text')?.getBoundingClientRect()
      return {
        label: item.querySelector('.tool-trace__text')?.textContent?.slice(0, 8),
        open: item.getAttribute('data-open'),
        iconCenterX: icon ? round(icon.left + icon.width / 2) : null,
        iconLeft: icon ? round(icon.left) : null,
        textLeft: text ? round(text.left) : null,
      }
    })
    const detail = document
      .querySelector<HTMLElement>('.tool-trace__item[data-open="true"] .tool-trace__detail-body')
      ?.getBoundingClientRect()
    const detailSpan = document
      .querySelector<HTMLElement>(
        '.tool-trace__item[data-open="true"] .tool-trace__detail-body span',
      )
      ?.getBoundingClientRect()
    return {
      rails,
      rows,
      detailLeft: detail ? round(detail.left) : null,
      detailTextLeft: detailSpan ? round(detailSpan.left) : null,
      traceLeft: round(
        document.querySelector<HTMLElement>('.tool-trace')!.getBoundingClientRect().left,
      ),
    }
  })
  writeFileSync('test-results/rails.json', JSON.stringify(report, null, 2))
  await page.locator('.tool-trace').screenshot({ path: 'test-results/trace-expanded.png' })
})

/** Does the expanded rail actually paint, and where? Read the pseudo-element, not the source. */
test('@verify probe rail paint', async ({ page }) => {
  await installDemoHarness(page, createDemoState())
  await page.emulateMedia({ colorScheme: 'light', reducedMotion: 'reduce' })
  await page.goto('/login')
  await page.getByLabel('用户名').fill('demo')
  await page.getByLabel('密码', { exact: true }).fill('123456')
  await page.locator('form').getByRole('button', { name: '登录', exact: true }).click()
  await page.getByRole('button', { name: '简历', exact: true }).click()
  await page.getByLabel('简历制作指令').fill('把工作经历改成量化导向，突出接口性能结果。')
  await page.getByRole('button', { name: '发送', exact: true }).click()
  await expect(page.getByRole('button', { name: /思考 2轮/ })).toBeVisible()
  await page.getByRole('button', { name: '改写工作经历' }).click()
  await page.mouse.move(4, 4)
  await page.waitForTimeout(400)

  const out = await page.evaluate(() => {
    const body = document.querySelector<HTMLElement>(
      '.tool-trace__item[data-open="true"] .tool-trace__detail-body',
    )
    if (!body) return { error: 'no detail body' }
    const before = getComputedStyle(body, '::before')
    const rect = body.getBoundingClientRect()
    const mark = document
      .querySelector<HTMLElement>('.tool-trace__item[data-open="true"] .tool-trace__mark')
      ?.getBoundingClientRect()
    return {
      content: before.content,
      position: before.position,
      width: before.width,
      height: before.height,
      left: before.insetInlineStart || before.left,
      background: before.backgroundColor,
      bodyLeft: Number(rect.left.toFixed(2)),
      markCenterX: mark ? Number((mark.left + mark.width / 2).toFixed(2)) : null,
    }
  })
  console.log('RAIL', JSON.stringify(out))
  await page.locator('.tool-trace').screenshot({ path: 'test-results/rail-check.png' })
})

/** Widths of the trace's two columns and the widest thing that has to fit in the second. */
test('@verify measure trace columns', async ({ page }) => {
  await installDemoHarness(page, createDemoState())
  await page.emulateMedia({ colorScheme: 'light', reducedMotion: 'reduce' })
  await page.goto('/login')
  await page.getByLabel('用户名').fill('demo')
  await page.getByLabel('密码', { exact: true }).fill('123456')
  await page.locator('form').getByRole('button', { name: '登录', exact: true }).click()
  await page.getByRole('button', { name: '简历', exact: true }).click()
  await page.getByLabel('简历制作指令').fill('把工作经历改成量化导向，突出接口性能结果。')
  await page.getByRole('button', { name: '发送', exact: true }).click()
  await expect(page.getByRole('button', { name: /思考 2轮/ })).toBeVisible()
  await page.mouse.move(4, 4)
  await page.waitForTimeout(300)
  const out = await page.evaluate(() => {
    const w = (sel: string, root: ParentNode = document) => {
      const n = root.querySelector<HTMLElement>(sel)
      return n ? Number(n.getBoundingClientRect().width.toFixed(1)) : null
    }
    const scroll = (sel: string) => {
      const n = document.querySelector<HTMLElement>(sel)
      return n ? { client: n.clientWidth, scroll: n.scrollWidth } : null
    }
    return {
      trace: w('.tool-trace'),
      rows: [...document.querySelectorAll<HTMLElement>('.tool-trace__item')].map((item) => ({
        label: item.querySelector('.tool-trace__text')?.textContent?.slice(0, 8),
        textW: w('.tool-trace__text', item),
        textNatural: (() => {
          const n = item.querySelector<HTMLElement>('.tool-trace__text')
          if (!n) return null
          const clone = n.cloneNode(true) as HTMLElement
          clone.style.inlineSize = 'max-content'
          clone.style.position = 'absolute'
          n.parentElement!.appendChild(clone)
          const value = Number(clone.getBoundingClientRect().width.toFixed(1))
          clone.remove()
          return value
        })(),
        trailingW: w('.tool-trace__trailing', item),
        rowW: w('.tool-trace__row', item),
      })),
      traceOverflow: scroll('.tool-trace'),
    }
  })
  writeFileSync('test-results/columns.json', JSON.stringify(out, null, 2))
})
