import type { Page } from '@playwright/test'

/**
 * Anonymous auth bootstrap for tests that render the login surface without the
 * full API harness: the session check resolves as 401 ProblemDetail with the
 * CSRF bootstrap cookie, so no request leaks to the dev proxy target.
 */
export async function installAnonymousSession(page: Page) {
  await page.route('**/api/auth/me', async (route) => {
    await route.fulfill({
      status: 401,
      contentType: 'application/problem+json',
      headers: { 'Set-Cookie': 'XSRF-TOKEN=anonymous-bootstrap; Path=/' },
      body: JSON.stringify({
        type: 'about:blank',
        title: 'authentication_required',
        status: 401,
        detail: '请先登录',
        code: 'authentication_required',
      }),
    })
  })
}
