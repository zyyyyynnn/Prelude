import AxeBuilder from '@axe-core/playwright'
import { expect, type Page } from '@playwright/test'

/* Two things had to hold for a scan here to judge anything.

   It must run against the rendered surface. `goto()` resolves on `load`, and the gallery was
   measured both ways: 19 DOM nodes at that moment against 1235 once its panels mount, so a scan
   started immediately reads an empty shell and reports a clean page.

   It must keep the severity range its own tags ask for. `wcag2a/aa/21a/21aa` report mostly
   `serious`, so filtering the result down to `critical` discarded precisely what the rule set
   finds — including the one violation this app actually had. */
const TAGS = ['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa']

export async function accessibilityViolations(page: Page) {
  const results = await new AxeBuilder({ page }).withTags(TAGS).analyze()
  return results.violations.map((violation) => ({
    impact: violation.impact,
    id: violation.id,
    nodes: violation.nodes.map((node) => `${node.target.join(' ')} — ${node.failureSummary}`),
  }))
}

export async function expectAccessible(page: Page, surface: string) {
  await expect(accessibilityViolations(page), surface).resolves.toEqual([])
}
