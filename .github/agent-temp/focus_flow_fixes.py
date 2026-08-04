from pathlib import Path

root = Path(__file__).resolve().parents[2]


def read(path: str) -> str:
    return (root / path).read_text(encoding='utf-8-sig')


def write(path: str, content: str) -> None:
    (root / path).write_text(content.rstrip() + '\n', encoding='utf-8')


sidebar_path = 'frontend/src/features/interview/components/SessionSidebar.vue'
sidebar = read(sidebar_path)
sidebar = sidebar.replace('  pointer-events: none;\n  transition: opacity', '  transition: opacity', 1)
sidebar = sidebar.replace('  pointer-events: auto;\n}', '}', 1)
write(sidebar_path, sidebar)

session_test_path = 'frontend/tests/flows/interview-session.spec.ts'
session_test = read(session_test_path)
session_test = session_test.replace(
    "  await page.getByRole('button', { name: '删除会话' }).click()",
    "  const sessionRow = page.locator('.session-item-wrapper').filter({\n"
    "    has: page.getByRole('button', { name: '打开会话 Java 后端工程师' }),\n"
    "  })\n"
    "  await sessionRow.hover()\n"
    "  await page.getByRole('button', { name: '删除会话' }).click()",
    1,
)
session_test = session_test.replace(
    "  await page.getByRole('button', { name: '删除会话' }).click()",
    "  await sessionRow.hover()\n"
    "  await page.getByRole('button', { name: '删除会话' }).click()",
    1,
)
write(session_test_path, session_test)

focus_test_path = 'frontend/tests/flows/focus-system.spec.ts'
focus_test = read(focus_test_path)
focus_test = focus_test.replace(
    "page.getByRole('button', { name: '实验室 Select' })",
    "page.getByRole('combobox', { name: '实验室 Select' })",
    1,
)
anchor = "async function setTheme(page: Page, theme: 'light' | 'dark') {"
helper = """async function resolveTokenColor(page: Page, token: string) {
  return page.evaluate((name) => {
    const probe = document.createElement('span')
    probe.style.color = `var(${name})`
    document.body.appendChild(probe)
    const value = getComputedStyle(probe).color
    probe.remove()
    return value
  }, token)
}

"""
if anchor not in focus_test:
    raise RuntimeError('focus test theme anchor missing')
focus_test = focus_test.replace(anchor, helper + anchor, 1)
focus_test = focus_test.replace(
    "    await setTheme(page, theme)\n\n    for (const field of [",
    "    await setTheme(page, theme)\n"
    "    const focusColor = await resolveTokenColor(page, '--color-focus-field')\n\n"
    "    for (const field of [",
    1,
)
focus_test = focus_test.replace(
    "      await field.click()\n      await page.mouse.move(0, 0)\n      const pointer = await styleOf(field)",
    "      await field.click()\n"
    "      await page.mouse.move(0, 0)\n"
    "      await expect.poll(async () => (await styleOf(field)).borderColor).toBe(focusColor)\n"
    "      const pointer = await styleOf(field)",
    1,
)
focus_test = focus_test.replace(
    "      await tabTo(page, field)\n      const keyboard = await styleOf(field)",
    "      await tabTo(page, field)\n"
    "      await expect.poll(async () => (await styleOf(field)).borderColor).toBe(focusColor)\n"
    "      const keyboard = await styleOf(field)",
    1,
)
focus_test = focus_test.replace(
    "  await expect(actions).toHaveCSS('opacity', '1')\n  await expect(actions).toHaveCSS('pointer-events', 'auto')",
    "  await expect(actions).toHaveCSS('opacity', '1')",
    1,
)
write(focus_test_path, focus_test)

print('Focus flow fixes applied successfully')
