import { expect, test } from '@playwright/test'
import { installMockApi, STRUCTURED_REPORT } from '../_helpers/mock-api'

const resumeDocument = {
  schemaVersion: 1 as const,
  locale: 'zh-CN',
  profile: { fullName: '张三', email: '', phone: '', targetRole: 'Java 工程师' },
  summary: '后端工程师',
  skills: [{ name: 'Java', level: 'proficient' }],
  experiences: [],
  projects: [
    {
      name: 'Prelude',
      role: '后端开发',
      techStack: ['Spring Boot'],
      bullets: ['负责接口开发'],
      outcome: '',
    },
  ],
  education: [],
  extras: [],
}

test('resume management exposes upload and cleanup without direct editing', async ({ page }) => {
  await installMockApi(page)
  let documentRequestCount = 0
  await page.route(/\/api\/resume(?:\/.*)?$/, async (route) => {
    const url = new URL(route.request().url())
    const method = route.request().method()
    if (method === 'GET' && url.pathname === '/api/resume/list') {
      return route.fulfill({
        json: {
          code: 200,
          data: [
            {
              id: 1,
              fileName: 'Java高级架构.pdf',
              createdAt: '2026-05-01T10:00:00Z',
              sessionCount: 1,
              inUse: true,
            },
          ],
        },
      })
    }
    if (url.pathname.endsWith('/document')) {
      documentRequestCount++
      return route.fulfill({ status: 404 })
    }
    return route.fallback()
  })

  await page.goto('/resumes')
  await expect(page.getByRole('button', { name: '上传新简历' })).toBeVisible()
  await expect(page.getByText('Java高级架构.pdf')).toBeVisible()
  await expect(page.getByRole('button', { name: '编辑' })).toHaveCount(0)
  await expect(page.getByText(/结构化简历/)).toHaveCount(0)
  expect(documentRequestCount).toBe(0)
})

test('accepts one report suggestion and updates only its decision state', async ({ page }) => {
  const improvement = {
    id: 11,
    resumeId: 1,
    sessionId: 101,
    targetPath: 'projects[0].bullets[0]',
    currentText: '负责接口开发',
    proposedText: '将接口 P95 降至 180ms',
    rationale: '补充可验证的量化结果',
    evidence: '通过缓存和批处理降低接口耗时',
    baseDocumentVersion: 1,
    status: 'pending' as const,
  }
  const report = JSON.stringify({
    ...JSON.parse(STRUCTURED_REPORT),
    resumeImprovements: [improvement],
  })
  const session = {
    sessionId: 101,
    status: 'finished',
    targetPosition: 'Java 后端工程师',
    currentStage: 'closing',
    summaryReport: report,
  }
  await installMockApi(page, {
    sessions: [session],
    interviewDetail: {
      ...session,
      stages: [],
      messages: [],
      resumeId: 1,
      positionId: 1,
    },
  })
  let acceptCalls = 0
  await page.route('**/api/resume/improvements/11/accept', async (route) => {
    acceptCalls++
    return route.fulfill({
      json: {
        code: 200,
        data: {
          improvement: { ...improvement, status: 'accepted', appliedDocumentVersion: 2 },
          resume: {
            resumeId: 1,
            fileName: 'Java高级架构.pdf',
            documentVersion: 2,
            sourceType: 'improvement',
            document: resumeDocument,
          },
        },
      },
    })
  })

  await page.goto('/interview')
  await page.getByRole('button', { name: '打开已结束会话 Java 后端工程师' }).click()
  await page.getByRole('button', { name: '报告', exact: true }).click()
  await expect(page.getByRole('heading', { name: '基于本场证据的改写建议' })).toBeVisible()
  await page.getByRole('button', { name: '接受并写入简历' }).click()

  await expect(page.getByText('已接受')).toBeVisible()
  expect(acceptCalls).toBe(1)
  await expect(page.getByText('负责接口开发')).toBeVisible()
  await expect(page.getByText('将接口 P95 降至 180ms')).toBeVisible()
})
