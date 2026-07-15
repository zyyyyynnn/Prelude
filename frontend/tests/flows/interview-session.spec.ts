import { expect, test } from '@playwright/test'
import { installMockApi } from '../_helpers/mock-api'

const session = {
  sessionId: 101,
  status: 'ongoing',
  targetPosition: 'Java 后端工程师',
  currentStage: 'warmup',
}

test('migrates legacy session preferences and persists local hide behavior', async ({ page }) => {
  await page.addInitScript(() => {
    localStorage.setItem('pinnedSessionIds', '[101]')
    localStorage.setItem('deletedSessionIds', '[]')
  })
  await installMockApi(page, {
    sessions: [session],
    interviewDetail: { ...session, stages: [], messages: [], resumeId: 1, positionId: 1 },
  })

  await page.goto('/interview')
  await expect(page.getByRole('button', { name: '打开会话 Java 后端工程师' })).toBeVisible()
  await expect(page.getByRole('button', { name: '取消置顶' })).toBeAttached()

  const migrated = await page.evaluate(() => ({
    current: localStorage.getItem('prelude-interview-session-preferences'),
    legacyPinned: localStorage.getItem('pinnedSessionIds'),
    legacyDeleted: localStorage.getItem('deletedSessionIds'),
  }))
  expect(JSON.parse(migrated.current ?? '{}')).toEqual({ pinnedIds: [101], hiddenIds: [] })
  expect(migrated.legacyPinned).toBeNull()
  expect(migrated.legacyDeleted).toBeNull()

  await page.getByRole('button', { name: '删除会话' }).click()
  await page.keyboard.press('Escape')
  await expect(page.getByRole('button', { name: '确定' })).toHaveCount(0)
  expect(
    await page.evaluate(() =>
      JSON.parse(localStorage.getItem('prelude-interview-session-preferences') ?? '{}'),
    ),
  ).toEqual({ pinnedIds: [101], hiddenIds: [] })
  await expect(page.getByRole('button', { name: '打开会话 Java 后端工程师' })).toBeVisible()

  await page.getByRole('button', { name: '删除会话' }).click()
  await page.getByRole('button', { name: '确定' }).click()
  await expect
    .poll(() =>
      page.evaluate(() =>
        JSON.parse(localStorage.getItem('prelude-interview-session-preferences') ?? '{}'),
      ),
    )
    .toEqual({ pinnedIds: [101], hiddenIds: [101] })
  await expect(page.getByRole('button', { name: '打开会话 Java 后端工程师' })).toHaveCount(0)

  await page.reload()
  await expect(page.getByRole('button', { name: '打开会话 Java 后端工程师' })).toHaveCount(0)
  const persisted = await page.evaluate(() =>
    JSON.parse(localStorage.getItem('prelude-interview-session-preferences') ?? '{}'),
  )
  expect(persisted).toEqual({ pinnedIds: [101], hiddenIds: [101] })
})
