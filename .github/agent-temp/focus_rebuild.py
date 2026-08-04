from __future__ import annotations

import json
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]


def read(path: str) -> str:
    return (ROOT / path).read_text(encoding="utf-8-sig")


def write(path: str, content: str) -> None:
    target = ROOT / path
    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_text(content.rstrip() + "\n", encoding="utf-8")


def replace_once(path: str, old: str, new: str) -> None:
    source = read(path)
    count = source.count(old)
    if count != 1:
        raise RuntimeError(f"{path}: expected one occurrence, found {count}: {old[:120]!r}")
    write(path, source.replace(old, new, 1))


def regex_once(path: str, pattern: str, replacement: str, flags: int = 0) -> None:
    source = read(path)
    updated, count = re.subn(pattern, replacement, source, count=1, flags=flags)
    if count != 1:
        raise RuntimeError(f"{path}: expected one regex match, found {count}: {pattern}")
    write(path, updated)


# ---------------------------------------------------------------------------
# Input intent: pointer actions must not inherit a false keyboard focus style
# after browser chrome shortcuts such as F12. Fields intentionally ignore this
# distinction and retain their single-border focus feedback for all focus input.
# ---------------------------------------------------------------------------
write(
    "frontend/src/shared/lib/input-intent.ts",
    """type InputIntent = 'pointer' | 'keyboard'

const KEYBOARD_INTENT_KEYS = new Set([
  'Tab',
  'ArrowUp',
  'ArrowDown',
  'ArrowLeft',
  'ArrowRight',
  'Enter',
  ' ',
  'Escape',
  'Home',
  'End',
  'PageUp',
  'PageDown',
])

let initialized = false

function setInputIntent(intent: InputIntent) {
  document.documentElement.dataset.inputIntent = intent
}

function handlePointerDown() {
  setInputIntent('pointer')
}

function handleKeyDown(event: KeyboardEvent) {
  if (event.altKey || event.ctrlKey || event.metaKey) return
  if (KEYBOARD_INTENT_KEYS.has(event.key)) setInputIntent('keyboard')
}

export function initInputIntentListener() {
  if (initialized || typeof document === 'undefined') return
  initialized = true
  document.addEventListener('pointerdown', handlePointerDown, true)
  document.addEventListener('keydown', handleKeyDown, true)
}

export function cleanupInputIntentListener() {
  if (!initialized || typeof document === 'undefined') return
  document.removeEventListener('pointerdown', handlePointerDown, true)
  document.removeEventListener('keydown', handleKeyDown, true)
  delete document.documentElement.dataset.inputIntent
  initialized = false
}
""",
)

replace_once(
    "frontend/src/app/App.vue",
    "import GlobalConfirmDialog from '@/shared/ui/confirm-dialog/GlobalConfirmDialog.vue'",
    "import { cleanupInputIntentListener, initInputIntentListener } from '@/shared/lib/input-intent'\nimport GlobalConfirmDialog from '@/shared/ui/confirm-dialog/GlobalConfirmDialog.vue'",
)
replace_once(
    "frontend/src/app/App.vue",
    "onMounted(() => {\n  void loadThemePreference()",
    "onMounted(() => {\n  initInputIntentListener()\n  void loadThemePreference()",
)
replace_once(
    "frontend/src/app/App.vue",
    "onBeforeUnmount(() => {\n  mediaQuery.removeEventListener('change', handleSystemThemeChange)",
    "onBeforeUnmount(() => {\n  cleanupInputIntentListener()\n  mediaQuery.removeEventListener('change', handleSystemThemeChange)",
)

# ---------------------------------------------------------------------------
# Shared primitives own stable geometry; semantic focus classes own only the
# visible state. No ring, offset, focus shadow, or focus transform is allowed.
# ---------------------------------------------------------------------------
write(
    "frontend/src/shared/ui/button/index.ts",
    """import type { VariantProps } from 'class-variance-authority'
import { cva } from 'class-variance-authority'

export { default as Button } from './Button.vue'

export const buttonVariants = cva(
  'ui-action inline-flex items-center justify-center gap-2 whitespace-nowrap rounded-md border border-transparent text-sm font-medium font-serif transition-colors [transition-duration:var(--motion-duration-base)] [transition-timing-function:var(--motion-ease-standard)] disabled:pointer-events-none disabled:opacity-50 [&_svg]:pointer-events-none [&_svg]:size-4 [&_svg]:shrink-0',
  {
    variants: {
      variant: {
        default: 'ui-action-primary bg-primary !text-[var(--color-surface)] hover:bg-primary/90',
        destructive:
          'ui-action-destructive bg-destructive text-destructive-foreground hover:bg-destructive/90',
        outline:
          'ui-action-outline border-input bg-background hover:bg-accent hover:text-accent-foreground',
        secondary:
          'ui-action-secondary bg-secondary text-secondary-foreground hover:bg-secondary/80',
        ghost: 'ui-action-ghost hover:bg-accent hover:text-accent-foreground',
        link: 'ui-action-link text-primary underline-offset-4 hover:underline',
      },
      size: {
        default: 'h-[var(--ui-height-base)] px-4 py-1.5',
        sm: 'h-[var(--ui-height-base)] rounded-md px-3',
        compact: 'h-[var(--ui-height-compact)] rounded-md px-2',
        icon: 'size-[var(--ui-height-base)]',
        'icon-sm': 'size-[var(--ui-height-base)]',
        'icon-compact': 'size-[var(--ui-height-compact)]',
      },
    },
    defaultVariants: {
      variant: 'default',
      size: 'default',
    },
  },
)

export type ButtonVariants = VariantProps<typeof buttonVariants>
""",
)

write(
    "frontend/src/shared/ui/input/Input.vue",
    """<script setup lang="ts">
import type { HTMLAttributes } from 'vue'
import { useVModel } from '@vueuse/core'
import { cn } from '@/shared/lib/utils'

const props = defineProps<{
  defaultValue?: string | number
  modelValue?: string | number
  class?: HTMLAttributes['class']
}>()

const emits = defineEmits<{
  (e: 'update:modelValue', payload: string | number): void
}>()

const modelValue = useVModel(props, 'modelValue', emits, {
  passive: true,
  defaultValue: props.defaultValue,
})
</script>

<template>
  <input
    v-model="modelValue"
    :class="
      cn(
        'ui-field-control flex h-[var(--ui-height-base)] w-full rounded-md border border-input bg-surface px-3 py-1.5 text-sm font-serif file:border-0 file:bg-transparent file:text-foreground file:text-sm file:font-medium placeholder:text-muted-foreground disabled:cursor-not-allowed disabled:opacity-50',
        props.class,
      )
    "
  />
</template>
""",
)

write(
    "frontend/src/shared/ui/textarea/Textarea.vue",
    """<script setup lang="ts">
import type { HTMLAttributes } from 'vue'
import { useVModel } from '@vueuse/core'
import { cn } from '@/shared/lib/utils'

const props = defineProps<{
  class?: HTMLAttributes['class']
  defaultValue?: string | number
  modelValue?: string | number
}>()

const emits = defineEmits<{
  (e: 'update:modelValue', payload: string | number): void
}>()

const modelValue = useVModel(props, 'modelValue', emits, {
  passive: true,
  defaultValue: props.defaultValue,
})
</script>

<template>
  <textarea
    v-model="modelValue"
    :class="
      cn(
        'ui-field-control block min-h-20 w-full resize-none rounded-md border border-input bg-surface px-3 py-2 text-sm font-serif placeholder:text-muted-foreground disabled:cursor-not-allowed disabled:opacity-50',
        props.class,
      )
    "
  />
</template>
""",
)

write(
    "frontend/src/shared/ui/shared-dropdown.ts",
    """import { cva } from 'class-variance-authority'

export const dropdownContentClasses =
  'relative z-[105] overflow-hidden rounded-md border border-input bg-surface text-popover-foreground p-0.5 shadow-[var(--shadow-whisper)] [animation-duration:var(--motion-duration-base)] [animation-timing-function:var(--motion-ease-standard)] data-[state=open]:animate-in data-[state=closed]:animate-out data-[state=closed]:fade-out-0 data-[state=open]:fade-in-0 data-[side=bottom]:slide-in-from-top-2 data-[side=left]:slide-in-from-right-2 data-[side=right]:slide-in-from-left-2 data-[side=top]:slide-in-from-bottom-2'

export const dropdownItemVariants = cva(
  'relative flex w-full cursor-default select-none items-center rounded-md pl-2 pr-2 text-sm font-serif outline-none focus:bg-accent focus:text-accent-foreground data-[highlighted]:bg-accent data-[highlighted]:text-accent-foreground data-[disabled]:pointer-events-none data-[disabled]:opacity-50',
  {
    variants: {
      size: {
        default: 'h-[var(--ui-height-base)]',
        compact: 'h-[var(--ui-height-compact)]',
      },
    },
    defaultVariants: {
      size: 'default',
    },
  },
)

export const dropdownTriggerVariants = cva(
  'ui-field-boundary flex w-full items-center justify-between rounded-md border border-input bg-surface px-3 py-1.5 text-sm font-serif data-[placeholder]:text-muted-foreground disabled:cursor-not-allowed disabled:opacity-50 [&>span]:truncate text-start',
  {
    variants: {
      size: {
        default: 'h-[var(--ui-height-base)]',
        compact: 'h-[var(--ui-height-compact)]',
      },
    },
    defaultVariants: {
      size: 'default',
    },
  },
)
""",
)

replace_once(
    "frontend/src/shared/ui/dropdown-menu/DropdownMenuTrigger.vue",
    '<DropdownMenuTrigger class="outline-none" v-bind="forwardedProps">',
    '<DropdownMenuTrigger v-bind="forwardedProps">',
)

# Close buttons and third-party close control use the icon-action contract.
for path, old, new in [
    (
        "frontend/src/shared/ui/dialog/DialogContent.vue",
        'class="absolute right-4 top-4 rounded-sm opacity-70 ring-offset-background transition-opacity [transition-duration:var(--motion-duration-base)] [transition-timing-function:var(--motion-ease-standard)] hover:opacity-100 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-focus disabled:pointer-events-none data-[state=open]:bg-accent data-[state=open]:text-muted-foreground"',
        'class="ui-action ui-action-icon absolute right-4 top-4 rounded-sm border border-transparent opacity-70 transition-opacity [transition-duration:var(--motion-duration-base)] [transition-timing-function:var(--motion-ease-standard)] hover:opacity-100 disabled:pointer-events-none data-[state=open]:bg-accent data-[state=open]:text-muted-foreground"',
    ),
    (
        "frontend/src/shared/ui/dialog/DialogScrollContent.vue",
        'class="absolute top-3 right-3 p-0.5 transition-colors [transition-duration:var(--motion-duration-base)] [transition-timing-function:var(--motion-ease-standard)] rounded-md hover:bg-secondary focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-focus"',
        'class="ui-action ui-action-icon absolute top-3 right-3 rounded-md border border-transparent p-0.5 transition-colors [transition-duration:var(--motion-duration-base)] [transition-timing-function:var(--motion-ease-standard)] hover:bg-secondary"',
    ),
]:
    replace_once(path, old, new)

replace_once(
    "frontend/src/shared/ui/sonner/Sonner.vue",
    "          '!size-6 !rounded-md !border-0 !bg-surface !text-muted-foreground !shadow-none hover:!bg-surface-hover hover:!text-foreground focus-visible:!outline-none focus-visible:!shadow-[var(--shadow-icon-action-focus)]',",
    "          'ui-action ui-action-icon !size-6 !rounded-md !border !bg-surface !text-muted-foreground !shadow-none hover:!bg-surface-hover hover:!text-foreground',",
)

replace_once(
    "frontend/src/shared/ui/sonner/Sonner.vue",
    ".toaster [data-sonner-toast] [data-close-button] {\n  --toast-close-button-left: auto;",
    ".toaster [data-sonner-toast] [data-close-button] {\n  border-color: transparent !important;\n  --toast-close-button-left: auto;",
)
replace_once(
    "frontend/src/shared/ui/sonner/Sonner.vue",
    "  --toast-close-button-transform: translateY(-50%);\n}",
    "  --toast-close-button-transform: translateY(-50%);\n}\n\nhtml:not([data-input-intent]) .toaster [data-sonner-toast] [data-close-button]:focus-visible,\nhtml[data-input-intent='keyboard'] .toaster [data-sonner-toast] [data-close-button]:focus {\n  border-color: var(--color-focus-action) !important;\n  background-color: var(--color-surface-hover) !important;\n}\n\nhtml[data-input-intent='pointer'] .toaster [data-sonner-toast] [data-close-button]:focus {\n  border-color: transparent !important;\n}",
)

replace_once(
    "frontend/src/shared/ui/segmented-control/SegmentedControl.vue",
    'class="segmented-control__item text-sm"',
    'class="segmented-control__item ui-action ui-action-selectable text-sm"',
)
replace_once(
    "frontend/src/shared/ui/segmented-control/SegmentedControl.vue",
    "  color: var(--color-text-secondary);\n  border-radius: var(--segmented-pill-radius);",
    "  color: var(--color-text-secondary);\n  border: 1px solid transparent;\n  border-radius: var(--segmented-pill-radius);",
)
regex_once(
    "frontend/src/shared/ui/segmented-control/SegmentedControl.vue",
    r"\n\.segmented-control__item:focus-visible \{[\s\S]*?\n\}",
    "",
)

# ---------------------------------------------------------------------------
# Business controls: keep state ownership local, but use explicit semantic
# action markers so pointer and keyboard intent cannot become conflated.
# ---------------------------------------------------------------------------
replace_once(
    "frontend/src/features/auth/pages/LoginPage.vue",
    'class="absolute right-0 top-0 h-full px-3 py-2 hover:bg-transparent text-muted-foreground flex items-center justify-center focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-focus"',
    'class="ui-action ui-action-icon absolute right-0 top-0 flex h-full items-center justify-center border border-transparent px-3 py-2 text-muted-foreground hover:bg-transparent"',
)

# Sidebar markup semantic ownership.
sidebar = read("frontend/src/features/interview/components/SessionSidebar.vue")
sidebar_replacements = {
    'class="app-sidebar__toggle"': 'class="app-sidebar__toggle ui-action ui-action-icon"',
    'class="app-sidebar__btn app-sidebar__btn--primary"': 'class="app-sidebar__btn app-sidebar__btn--primary ui-action ui-action-primary"',
    "'session-item-btn',": "'session-item-btn ui-action ui-action-nav',",
    'class="action-btn"': 'class="action-btn ui-action ui-action-icon"',
    'class="action-btn delete-btn"': 'class="action-btn delete-btn ui-action ui-action-danger"',
    "'app-sidebar__btn app-sidebar__btn--icon',": "'app-sidebar__btn app-sidebar__btn--icon ui-action ui-action-nav',",
    "'app-sidebar__btn app-sidebar__btn--tool',": "'app-sidebar__btn app-sidebar__btn--tool ui-action ui-action-nav',",
    'class="app-sidebar__btn app-sidebar__btn--settings"': 'class="app-sidebar__btn app-sidebar__btn--settings ui-action ui-action-nav"',
}
for old, new in sidebar_replacements.items():
    if old not in sidebar:
        raise RuntimeError(f"SessionSidebar.vue missing {old!r}")
    sidebar = sidebar.replace(old, new)

# Stable transparent boundaries and removal of legacy focus shadows.
sidebar = sidebar.replace(
    "  border-radius: var(--radius-sm);\n  flex-shrink: 0;",
    "  border: 1px solid transparent;\n  border-radius: var(--radius-sm);\n  flex-shrink: 0;",
    1,
)
sidebar = sidebar.replace(
    "  overflow: hidden;\n}\n.app-sidebar__btn--primary",
    "  overflow: hidden;\n  border: 1px solid transparent;\n}\n.app-sidebar__btn--primary",
    1,
)
sidebar = sidebar.replace(
    "  text-overflow: ellipsis;\n}\n.session-item-btn:hover",
    "  text-overflow: ellipsis;\n  border: 1px solid transparent;\n}\n.session-item-btn:hover",
    1,
)
sidebar = sidebar.replace(
    "  justify-content: center;\n  transition:",
    "  justify-content: center;\n  border: 1px solid transparent;\n  transition:",
    1,
)
sidebar = re.sub(
    r"\n\.(?:app-sidebar__toggle|app-sidebar__btn|session-item-btn|action-btn):focus-visible \{[\s\S]*?\n\}",
    "",
    sidebar,
)
sidebar = sidebar.replace(
    ".session-item-wrapper:hover .session-item-actions {\n  opacity: 1;\n}",
    ".session-item-wrapper:hover .session-item-actions,\n.session-item-wrapper:focus-within .session-item-actions {\n  opacity: 1;\n  pointer-events: auto;\n}",
)
sidebar = sidebar.replace(
    "  opacity: 0;\n  transition: opacity",
    "  opacity: 0;\n  pointer-events: none;\n  transition: opacity",
    1,
)
sidebar = sidebar.replace(
    ".session-item-wrapper:hover .session-item-actions {\n  background:",
    ".session-item-wrapper:hover .session-item-actions,\n.session-item-wrapper:focus-within .session-item-actions {\n  background:",
)
sidebar = sidebar.replace(
    ".session-item-wrapper:hover:has(.session-item-btn.is-active) .session-item-actions {\n  background:",
    ".session-item-wrapper:hover:has(.session-item-btn.is-active) .session-item-actions,\n.session-item-wrapper:focus-within:has(.session-item-btn.is-active) .session-item-actions {\n  background:",
)
sidebar = sidebar.replace(
    ".session-item-wrapper:hover .pin-indicator {\n  display: none;\n}",
    ".session-item-wrapper:hover .pin-indicator,\n.session-item-wrapper:focus-within .pin-indicator {\n  display: none;\n}",
)
write("frontend/src/features/interview/components/SessionSidebar.vue", sidebar)

# Settings navigation uses a stable internal border; active state remains owned
# by the settings pattern and no longer stacks a decorative shadow.
settings = read("frontend/src/features/settings/components/GlobalSettingsModal.vue")
settings = settings.replace("'menu-item',", "'menu-item ui-action ui-action-nav',")
settings = settings.replace(
    'class="menu-item menu-item--danger"',
    'class="menu-item menu-item--danger ui-action ui-action-danger"',
)
settings = settings.replace("  border: none;", "  border: 1px solid transparent;", 1)
settings = settings.replace(
    "  color: var(--color-brand);\n  box-shadow: var(--shadow-ring);",
    "  color: var(--color-brand);\n  border-color: var(--color-ring);",
)
write("frontend/src/features/settings/components/GlobalSettingsModal.vue", settings)

# Theme options: selected/hover remain a component state; keyboard focus uses
# the selectable action contract without a second shadow.
theme = read("frontend/src/features/settings/components/ThemeSettingsPanel.vue")
theme = theme.replace(
    ":class=\"['theme-option', { 'is-active': state.themePreference === option.value }]\"",
    ":class=\"[\n          'theme-option ui-action ui-action-selectable',\n          { 'is-active': state.themePreference === option.value },\n        ]\"",
)
theme = theme.replace("  box-shadow: var(--shadow-ring);\n", "")
theme = theme.replace(
    "    border-color var(--motion-duration-base) var(--motion-ease-standard),\n    box-shadow var(--motion-duration-base) var(--motion-ease-standard);",
    "    border-color var(--motion-duration-base) var(--motion-ease-standard);",
)
theme = theme.replace("  box-shadow: var(--shadow-ring-deep);\n", "")
theme = re.sub(r"\n\.theme-option:focus-visible \{[\s\S]*?\n\}", "", theme)
write("frontend/src/features/settings/components/ThemeSettingsPanel.vue", theme)

profile = read("frontend/src/features/settings/components/UserProfilePanel.vue")
profile = profile.replace(
    'class="password-toggle"',
    'class="password-toggle ui-action ui-action-icon"',
)
profile = profile.replace(
    "  color: var(--color-text-tertiary);\n  border-radius: var(--radius-md);",
    "  color: var(--color-text-tertiary);\n  border: 1px solid transparent;\n  border-radius: var(--radius-md);",
)
profile = profile.replace(
    ".password-toggle:hover,\n.password-toggle:focus-visible {\n  background: var(--color-surface-hover);\n  color: var(--color-text-primary);\n  outline: none;\n  box-shadow: var(--shadow-icon-action-focus);\n}",
    ".password-toggle:hover {\n  background: var(--color-surface-hover);\n  color: var(--color-text-primary);\n}",
)
write("frontend/src/features/settings/components/UserProfilePanel.vue", profile)

llm = read("frontend/src/features/settings/components/LlmSettingsPanel.vue")
llm = llm.replace(
    'class="px-2 py-2 hover:bg-transparent text-muted-foreground hover:text-destructive flex items-center justify-center transition-colors [transition-duration:var(--motion-duration-base)] [transition-timing-function:var(--motion-ease-standard)]"',
    'class="ui-action ui-action-danger border border-transparent px-2 py-2 text-muted-foreground transition-colors [transition-duration:var(--motion-duration-base)] [transition-timing-function:var(--motion-ease-standard)] hover:bg-transparent hover:text-destructive"',
)
llm = llm.replace(
    'class="px-2 py-2 hover:bg-transparent text-muted-foreground flex items-center justify-center transition-colors [transition-duration:var(--motion-duration-base)] [transition-timing-function:var(--motion-ease-standard)]"',
    'class="ui-action ui-action-icon border border-transparent px-2 py-2 text-muted-foreground transition-colors [transition-duration:var(--motion-duration-base)] [transition-timing-function:var(--motion-ease-standard)] hover:bg-transparent"',
)
write("frontend/src/features/settings/components/LlmSettingsPanel.vue", llm)

composer_text = read("frontend/src/features/interview/components/ComposerText.vue")
composer_text = composer_text.replace(" focus-visible:ring-0 focus-visible:ring-offset-0", "")
write("frontend/src/features/interview/components/ComposerText.vue", composer_text)

composer = read("frontend/src/features/interview/components/InterviewComposer.vue")
anchor = ".interview-composer.is-disabled .interview-composer__inner {"
if anchor not in composer:
    raise RuntimeError("InterviewComposer focus insertion anchor missing")
composer = composer.replace(
    anchor,
    ".interview-composer__inner:has(.composer-textarea:focus) {\n  border-color: var(--color-focus-field);\n}\n" + anchor,
    1,
)
write("frontend/src/features/interview/components/InterviewComposer.vue", composer)

# Report carousel delegates keyboard navigation to explicit previous/next buttons.
report = read("frontend/src/features/report/components/QuestionReviewList.vue")
report = report.replace('    tabindex="0"\n', "")
report = report.replace('    @keydown.left.prevent="showReview(activeIndex - 1)"\n', "")
report = report.replace('    @keydown.right.prevent="showReview(activeIndex + 1)"\n', "")
report = re.sub(r"\n\.question-review-carousel:focus-visible \{[\s\S]*?\n\}", "", report)
write("frontend/src/features/report/components/QuestionReviewList.vue", report)

# Component Lab must expose real Textarea state for the focus matrix.
lab = read("frontend/src/devtools/component-lab/ComponentLabView.vue")
lab = lab.replace(
    "import { Input } from '@/shared/ui/input'",
    "import { Input } from '@/shared/ui/input'\nimport { Textarea } from '@/shared/ui/textarea'",
)
lab = lab.replace("const inputValue = ref('')", "const inputValue = ref('')\nconst textareaValue = ref('')")
lab = lab.replace("<template #heading>`Input`</template", "<template #heading>`Input / Textarea`</template")
lab = lab.replace(
    "          <div class=\"lab__cell\">\n            <Label for=\"lab-input-disabled\">disabled</Label>",
    "          <div class=\"lab__cell\">\n            <Label for=\"lab-textarea-default\">textarea</Label>\n            <Textarea\n              id=\"lab-textarea-default\"\n              v-model=\"textareaValue\"\n              placeholder=\"textarea placeholder\"\n            />\n          </div>\n          <div class=\"lab__cell\">\n            <Label for=\"lab-input-disabled\">disabled</Label>",
)
write("frontend/src/devtools/component-lab/ComponentLabView.vue", lab)

# ---------------------------------------------------------------------------
# Foundations: semantic tokens, a real pointer/keyboard consumer, forced-color
# fallback, and removal of legacy style blocks with no source-tree consumers.
# ---------------------------------------------------------------------------
styles_path = "frontend/src/shared/ui/styles/index.css"
styles = read(styles_path)
styles = styles.replace(
    "  --color-focus: var(--color-brand);",
    "  --color-focus-field: var(--color-focus-field);\n  --color-focus-action: var(--color-focus-action);",
)
styles = styles.replace(
    "  --color-focus: #b39b8d;",
    "  --color-focus-field: #7c7469;\n  --color-focus-action: #9e7b6a;",
)
styles = styles.replace(
    "  --color-focus: #d5b9a9;",
    "  --color-focus-field: #a09689;\n  --color-focus-action: #c7a392;",
)
styles = styles.replace("  --ring: var(--color-brand);", "  --ring: var(--color-focus-action);")
styles = styles.replace("  --shadow-icon-action-focus: 0 0 0 2px var(--color-brand);\n", "")

focus_contract = """
/*
 * Focus contract
 * - fields always expose one existing 1px boundary;
 * - actions expose a semantic keyboard-only state;
 * - pointer focus and browser chrome function keys never create a residual halo;
 * - selected/open/active remain component-owned states.
 */
.ui-field-control,
.ui-field-boundary {
  outline: none;
  transition: border-color var(--motion-duration-base) var(--motion-ease-standard);
}

.ui-field-control:focus,
.ui-field-control:focus-visible,
.ui-field-boundary:focus,
.ui-field-boundary:focus-visible,
.ui-field-boundary:focus-within {
  outline: none;
  border-color: var(--color-focus-field);
}

.ui-action {
  border-width: 1px;
  border-style: solid;
  outline: none;
}

html[data-input-intent='pointer'] .ui-action:focus {
  outline: none;
}

html:not([data-input-intent]) :is(.ui-action-primary, .ui-action-destructive):focus-visible,
html[data-input-intent='keyboard'] :is(.ui-action-primary, .ui-action-destructive):focus {
  outline: none;
  border-color: currentColor;
}

html:not([data-input-intent]) :is(.ui-action-outline, .ui-action-secondary):focus-visible,
html[data-input-intent='keyboard'] :is(.ui-action-outline, .ui-action-secondary):focus {
  outline: none;
  border-color: var(--color-focus-field);
}

html:not([data-input-intent]) .ui-action-ghost:focus-visible,
html[data-input-intent='keyboard'] .ui-action-ghost:focus {
  outline: none;
  border-color: var(--color-focus-action);
  background-color: var(--color-accent);
  color: var(--color-accent-foreground);
}

html:not([data-input-intent]) :is(.ui-action-icon, .ui-action-nav):focus-visible,
html[data-input-intent='keyboard'] :is(.ui-action-icon, .ui-action-nav):focus {
  outline: none;
  border-color: var(--color-focus-action);
  background-color: var(--color-surface-hover);
  color: var(--color-text-primary);
}

html:not([data-input-intent]) .ui-action-selectable:focus-visible,
html[data-input-intent='keyboard'] .ui-action-selectable:focus {
  outline: none;
  border-color: var(--color-focus-action);
}

html:not([data-input-intent]) .ui-action-danger:focus-visible,
html[data-input-intent='keyboard'] .ui-action-danger:focus {
  outline: none;
  border-color: var(--color-error);
  background-color: color-mix(in srgb, var(--color-error) 10%, transparent);
  color: var(--color-error);
}

html:not([data-input-intent]) .ui-action-link:focus-visible,
html[data-input-intent='keyboard'] .ui-action-link:focus {
  outline: none;
  color: var(--color-primary);
  text-decoration-line: underline;
  text-decoration-thickness: 1px;
  text-underline-offset: var(--spacing-0-5);
}

@media (forced-colors: active) {
  :focus-visible {
    outline: 2px solid CanvasText !important;
    outline-offset: 2px !important;
  }
}
"""
needle = "button,\ninput,\nselect,\ntextarea {\n  color: var(--color-text-primary);\n}\n"
if needle not in styles:
    raise RuntimeError("index.css focus contract anchor missing")
styles = styles.replace(needle, needle + focus_contract, 1)

# Dead legacy App Shell and legacy global button implementations have no source
# consumers. Removing them prevents stale focus rules from silently returning.
styles, app_shell_count = re.subn(r"\n\.app-shell \{[\s\S]*?\n\.page \{", "\n.page {", styles, count=1)
if app_shell_count != 1:
    raise RuntimeError("Unable to remove dead app-shell block")
styles, legacy_button_count = re.subn(
    r"\n\.ui-button \{[\s\S]*?\n/\* ── Composer dropdown ── \*/",
    "\n/* ── Composer dropdown ── */",
    styles,
    count=1,
)
if legacy_button_count != 1:
    raise RuntimeError("Unable to remove dead ui-button block")
styles, upload_button_count = re.subn(
    r"\n\.upload-field__button \{[\s\S]*?\n\.upload-field__name \{",
    "\n.upload-field__name {",
    styles,
    count=1,
)
if upload_button_count != 1:
    raise RuntimeError("Unable to remove dead upload button block")
styles, text_button_count = re.subn(
    r"\n\.text-button \{[\s\S]*?\n\.login-card \{",
    "\n.login-card {",
    styles,
    count=1,
)
if text_button_count != 1:
    raise RuntimeError("Unable to remove dead text-button block")
styles, icon_button_count = re.subn(
    r"\n/\* ── Icon Action Button ── \*/[\s\S]*?\n/\* 彻底消除 Chrome Autofill",
    "\n/* 彻底消除 Chrome Autofill",
    styles,
    count=1,
)
if icon_button_count != 1:
    raise RuntimeError("Unable to remove dead icon-action block")
styles = re.sub(
    r"\n@keyframes app-shell-menu-enter \{[\s\S]*?\n@media \(max-width: 1080px\)",
    "\n@media (max-width: 1080px)",
    styles,
    count=1,
)
# Remove residual responsive declarations for the already removed legacy shell.
styles = re.sub(r"\n\s*\.app-shell[^{}]*\{[^{}]*\}", "", styles)
write(styles_path, styles)

# Token schema follows actual Foundations ownership.
token_path = ROOT / "frontend/tokens/ui-tokens.json"
tokens = json.loads(token_path.read_text(encoding="utf-8-sig"))
color_tokens = tokens["categories"]["color"]["tokens"]
if "color-focus" in color_tokens:
    color_tokens.remove("color-focus")
for token in ["color-focus-field", "color-focus-action"]:
    if token not in color_tokens:
        color_tokens.insert(color_tokens.index("color-mask-overlay"), token)
shadow_tokens = tokens["categories"]["shadow"]["tokens"]
if "shadow-icon-action-focus" in shadow_tokens:
    shadow_tokens.remove("shadow-icon-action-focus")
token_path.write_text(json.dumps(tokens, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")

# ---------------------------------------------------------------------------
# Guardrail: prohibit the old implementation, verify the new primitive markers,
# and reject focus effects that create halos or geometry changes.
# ---------------------------------------------------------------------------
guardrail_path = "frontend/scripts/verify-ui-guardrails.cjs"
guardrail = read(guardrail_path)
guardrail = guardrail.replace("const componentFocusShadowToken = '--shadow-icon-action-focus'\n", "")
old_function = re.search(
    r"function findComponentFocusShadowViolations\(\) \{[\s\S]*?\n\}\n\nfunction extractSingleQuotedValue",
    guardrail,
)
if not old_function:
    raise RuntimeError("Old focus guardrail function not found")
new_function = r"""function collectSourceFiles(directory, files = []) {
  let entries
  try {
    entries = fs.readdirSync(directory, { withFileTypes: true })
  } catch {
    return files
  }
  for (const entry of entries) {
    const file = path.join(directory, entry.name)
    if (entry.isDirectory()) collectSourceFiles(file, files)
    else if (entry.isFile() && /\.(vue|ts|css)$/.test(entry.name)) files.push(file)
  }
  return files
}

function findFocusContractViolations() {
  const violations = []
  const forbiddenPatterns = [
    [/focus-visible:ring-|focus:ring-|ring-offset-/, '旧 Tailwind ring/offset 焦点模式不得回流'],
    [/focus-visible:shadow-/, '焦点不得通过 Tailwind shadow 绘制外部 halo'],
    [/shadow-icon-action-focus/, '已废止的外部 action focus shadow 不得回流'],
    [/ui-focus-quiet/, '交互控件不得通过 quiet 例外隐藏键盘焦点'],
    [/focus-visible:outline-none/, '焦点 outline 必须由共享语义契约统一管理'],
  ]

  for (const file of collectSourceFiles(frontendSrc)) {
    const rawSource = fs.readFileSync(file, 'utf8')
    const source = stripNonExecutableComments(rawSource)
    const lines = source.split(/\r?\n/)
    for (let index = 0; index < lines.length; index++) {
      for (const [pattern, description] of forbiddenPatterns) {
        if (!pattern.test(lines[index])) continue
        violations.push({
          id: 'focus-obsolete-contract',
          description,
          hit: { file, line: index + 1, text: lines[index] },
        })
      }
    }

    const styleSources = []
    if (file.endsWith('.vue')) {
      const stylePattern = /<style\b[^>]*>([\s\S]*?)<\/style>/g
      let styleMatch
      while ((styleMatch = stylePattern.exec(source)) !== null) {
        styleSources.push({ content: styleMatch[1], offset: styleMatch.index })
      }
    } else if (file.endsWith('.css')) {
      styleSources.push({ content: source, offset: 0 })
    }

    for (const { content, offset } of styleSources) {
      const blockPattern = /([^{}]+)\{([^{}]*)\}/g
      let blockMatch
      while ((blockMatch = blockPattern.exec(content)) !== null) {
        const selector = blockMatch[1].trim()
        const isFocusSelector =
          /:focus(?:-visible|-within)?\b/.test(selector) ||
          /data-input-intent=['"]keyboard['"]/.test(selector)
        if (!isFocusSelector) continue
        const declarations = blockMatch[2]
        if (/(?:^|[;\s])box-shadow\s*:/.test(declarations)) {
          violations.push({
            id: 'focus-external-shadow-contract',
            description: '焦点 selector 不得改变 box-shadow',
            hit: {
              file,
              line: sourceLine(rawSource, offset + blockMatch.index),
              text: `${selector} { ${declarations.trim()} }`,
            },
          })
        }
        if (/(?:^|[;\s])transform\s*:/.test(declarations)) {
          violations.push({
            id: 'focus-transform-contract',
            description: '焦点 selector 不得改变 transform 或几何位置',
            hit: {
              file,
              line: sourceLine(rawSource, offset + blockMatch.index),
              text: `${selector} { ${declarations.trim()} }`,
            },
          })
        }
        const removesOutline = /outline\s*:\s*none/.test(declarations)
        const pointerSuppression = /data-input-intent=['"]pointer['"]/.test(selector)
        const hasReplacement =
          /(border-color|background(?:-color)?|color|text-decoration|outline\s*:\s*2px)/.test(
            declarations,
          )
        if (removesOutline && !pointerSuppression && !hasReplacement) {
          violations.push({
            id: 'focus-visible-replacement-contract',
            description: 'outline: none 必须在同一焦点规则中提供可见的语义替代',
            hit: {
              file,
              line: sourceLine(rawSource, offset + blockMatch.index),
              text: `${selector} { ${declarations.trim()} }`,
            },
          })
        }
      }
    }
  }

  const buttonSource = fs.readFileSync(path.join(frontendSrc, 'shared/ui/button/index.ts'), 'utf8')
  for (const semanticClass of [
    'ui-action-primary',
    'ui-action-destructive',
    'ui-action-outline',
    'ui-action-secondary',
    'ui-action-ghost',
    'ui-action-link',
  ]) {
    if (buttonSource.includes(semanticClass)) continue
    violations.push({
      id: 'button-focus-variant-contract',
      description: '每个 Button variant 必须显式拥有语义焦点类别',
      hit: { file: path.join(frontendSrc, 'shared/ui/button/index.ts'), line: 1, text: semanticClass },
    })
  }

  for (const [relativePath, marker] of [
    ['shared/ui/input/Input.vue', 'ui-field-control'],
    ['shared/ui/textarea/Textarea.vue', 'ui-field-control'],
    ['shared/ui/shared-dropdown.ts', 'ui-field-boundary'],
  ]) {
    const file = path.join(frontendSrc, relativePath)
    if (fs.readFileSync(file, 'utf8').includes(marker)) continue
    violations.push({
      id: 'field-focus-primitive-contract',
      description: '标准字段必须复用单边框 field focus contract',
      hit: { file, line: 1, text: marker },
    })
  }

  const stylesSource = fs.readFileSync(stylesIndex, 'utf8')
  for (const marker of [
    "data-input-intent='pointer'",
    "data-input-intent='keyboard'",
    '--color-focus-field',
    '--color-focus-action',
  ]) {
    if (stylesSource.includes(marker)) continue
    violations.push({
      id: 'focus-foundation-contract',
      description: 'Focus Foundations 缺少输入意图或语义 token 消费者',
      hit: { file: stylesIndex, line: 1, text: marker },
    })
  }

  const sidebarFile = path.join(frontendSrc, 'features/interview/components/SessionSidebar.vue')
  const sidebarSource = fs.readFileSync(sidebarFile, 'utf8')
  if (!sidebarSource.includes('.session-item-wrapper:focus-within .session-item-actions')) {
    violations.push({
      id: 'sidebar-focus-within-contract',
      description: 'Sidebar 快捷操作必须在内部按钮获得键盘焦点时可见',
      hit: { file: sidebarFile, line: 1, text: 'missing focus-within visibility' },
    })
  }

  return violations
}

function extractSingleQuotedValue"""
guardrail = guardrail[: old_function.start()] + new_function + guardrail[old_function.end() :]
guardrail = guardrail.replace(
    "failures.push(...findComponentFocusShadowViolations())",
    "failures.push(...findFocusContractViolations())",
)
write(guardrail_path, guardrail)

# ---------------------------------------------------------------------------
# Design and quality documentation describe only implemented ownership.
# ---------------------------------------------------------------------------
design_path = "DESIGN.md"
design = read(design_path)
design = design.replace(
    "- `--shadow-icon-action-focus`：业务组件自定义 CSS `:focus-visible` 的共享焦点阴影。\n",
    "",
)
design = design.replace(
    "- shadcn-vue primitive 使用 `focus-visible:ring-*`，颜色必须映射到 `--color-focus`。\n- 业务组件在 scoped CSS 中自行定义 `:focus-visible` 且使用 `box-shadow` 时，只能写 `box-shadow: var(--shadow-icon-action-focus)`。\n- 禁止业务组件手写 `inset 0 0 0 ...`、裸像素或其他临时 focus shadow；`verify:ui` 必须阻断此类漂移。",
    "- 标准 Input、Textarea、Select 与 Combobox 只改变现有 1px 边框，使用 `--color-focus-field`；不得改变背景、阴影、尺寸或位置。\n- Button 按 default、destructive、outline、secondary、ghost、link 变体分别拥有语义焦点类别；Primary 与 Destructive 必须保持原背景和文字语义。\n- Sidebar、关闭按钮、密码操作、Theme 与 SegmentedControl 使用明确的 action/selectable 类；active、selected、open 与 focus 必须彼此独立。\n- Action 焦点只在键盘输入意图或浏览器初始 `:focus-visible` 下显示；pointer/F12 往返不得留下外框或灰底。Field 焦点不受输入意图区分。\n- 普通主题禁止外部 ring、ring offset、focus box-shadow 与 focus transform；强制高对比度模式使用系统 `CanvasText` outline。\n- Composer compact 控件与标准 Select/Button 使用同等焦点质量，不允许 quiet/no-focus 例外；报告逐题轮播仅让上一题/下一题按钮进入 Tab 顺序。\n- `verify:ui` 必须同时阻断旧焦点样式回流和 `outline: none` 后没有可见替代的实现。",
)
write(design_path, design)

quality_path = "docs/quality/ui-quality-system.md"
quality = read(quality_path)
quality = quality.replace(
    "- scoped `:focus-visible` 绕过 `--shadow-icon-action-focus`。",
    "- 字段未复用 `ui-field-control` / `ui-field-boundary` 单边框契约，或 action 未按 Button variant / icon / nav / selectable 语义分类；\n- `focus-visible:ring-*`、`focus:ring-*`、`ring-offset-*`、focus shadow/transform、`ui-focus-quiet` 以及无可见替代的 `outline: none`；\n- 输入意图没有在 Foundations 层同时处理 pointer、keyboard 与 F12 等非导航功能键。",
)
quality = quality.replace(
    "`verify:a11y` 使用 mock API 执行登录页、工作区、设置弹窗、下拉控件、侧栏、Composer 和结构化报告的 axe 与键盘路径检查。门禁只阻断 critical violation；绿色结果不代表不存在 serious color-contrast 问题，也不授权修改现有品牌色或 token 值。",
    "`verify:a11y` 使用 mock API 执行登录页、工作区、设置弹窗、下拉控件、侧栏、Composer 和结构化报告的 axe 与键盘路径检查。门禁只阻断 critical violation；绿色结果不代表不存在 serious color-contrast 问题，也不授权修改现有品牌色或 token 值。`verify:flows` 另外读取真实 computed style，覆盖字段单边框、Button variant、Sidebar F12/pointer/keyboard、快捷操作 focus-within、设置选中态、报告导航和 forced-colors。",
)
write(quality_path, quality)

# ---------------------------------------------------------------------------
# Deterministic browser contract tests. They use real pointer/Tab paths and no
# fixed sleeps or conditional empty tests.
# ---------------------------------------------------------------------------
write(
    "frontend/tests/flows/focus-system.spec.ts",
    r"""import { expect, test, type Locator, type Page } from '@playwright/test'
import { installMockApi, STRUCTURED_REPORT } from '../_helpers/mock-api'

type StyleSnapshot = {
  borderColor: string
  borderWidth: string
  backgroundColor: string
  color: string
  boxShadow: string
  outlineStyle: string
  outlineWidth: string
  textDecorationLine: string
  transform: string
  rect: { width: number; height: number }
}

async function styleOf(locator: Locator): Promise<StyleSnapshot> {
  return locator.evaluate((element) => {
    const style = getComputedStyle(element)
    const rect = element.getBoundingClientRect()
    return {
      borderColor: style.borderTopColor,
      borderWidth: style.borderTopWidth,
      backgroundColor: style.backgroundColor,
      color: style.color,
      boxShadow: style.boxShadow,
      outlineStyle: style.outlineStyle,
      outlineWidth: style.outlineWidth,
      textDecorationLine: style.textDecorationLine,
      transform: style.transform,
      rect: { width: rect.width, height: rect.height },
    }
  })
}

async function tabTo(page: Page, target: Locator, limit = 80) {
  await expect(target).toBeVisible()
  for (let index = 0; index < limit; index += 1) {
    await page.keyboard.press('Tab')
    if (await target.evaluate((element) => element === document.activeElement)) return
  }
  throw new Error(`Unable to reach target with Tab after ${limit} steps`)
}

async function blurWithPointer(page: Page) {
  await page.locator('body').click({ position: { x: 2, y: 2 } })
  await page.mouse.move(0, 0)
}

function rgb(value: string): [number, number, number] {
  const match = value.match(/rgba?\((\d+)[, ]+(\d+)[, ]+(\d+)/)
  if (!match) throw new Error(`Unsupported RGB value: ${value}`)
  return [Number(match[1]), Number(match[2]), Number(match[3])]
}

function luminance([red, green, blue]: [number, number, number]) {
  const convert = (channel: number) => {
    const value = channel / 255
    return value <= 0.04045 ? value / 12.92 : ((value + 0.055) / 1.055) ** 2.4
  }
  return 0.2126 * convert(red) + 0.7152 * convert(green) + 0.0722 * convert(blue)
}

function contrast(first: string, second: string) {
  const a = luminance(rgb(first))
  const b = luminance(rgb(second))
  return (Math.max(a, b) + 0.05) / (Math.min(a, b) + 0.05)
}

function expectStableGeometry(before: StyleSnapshot, after: StyleSnapshot) {
  expect(after.borderWidth).toBe('1px')
  expect(after.rect.width).toBeCloseTo(before.rect.width, 3)
  expect(after.rect.height).toBeCloseTo(before.rect.height, 3)
  expect(after.transform).toBe(before.transform)
  expect(after.boxShadow).toBe(before.boxShadow)
}

async function setTheme(page: Page, theme: 'light' | 'dark') {
  await page.evaluate((value) => {
    document.documentElement.classList.toggle('dark', value === 'dark')
    document.documentElement.dataset.theme = value
  }, theme)
}

const ongoingSession = {
  sessionId: 101,
  status: 'ongoing',
  targetPosition: 'Java 后端工程师',
  currentStage: 'technical',
  summaryReport: '',
}

const finishedSession = {
  ...ongoingSession,
  status: 'finished',
  currentStage: 'closing',
  summaryReport: STRUCTURED_REPORT,
}

test('fields keep one stable boundary in pointer and keyboard paths for light and dark', async ({
  page,
}) => {
  await installMockApi(page)

  for (const theme of ['light', 'dark'] as const) {
    await page.goto('/components-lab')
    await setTheme(page, theme)

    for (const field of [
      page.locator('#lab-input-default'),
      page.locator('#lab-textarea-default'),
      page.getByRole('button', { name: '实验室 Select' }),
    ]) {
      await blurWithPointer(page)
      const before = await styleOf(field)
      await field.click()
      await page.mouse.move(0, 0)
      const pointer = await styleOf(field)
      expect(pointer.borderColor).not.toBe(before.borderColor)
      expect(pointer.backgroundColor).toBe(before.backgroundColor)
      expect(pointer.outlineStyle).toBe('none')
      expectStableGeometry(before, pointer)
      expect(contrast(pointer.borderColor, pointer.backgroundColor)).toBeGreaterThanOrEqual(3)

      await blurWithPointer(page)
      await tabTo(page, field)
      const keyboard = await styleOf(field)
      expect(keyboard.borderColor).toBe(pointer.borderColor)
      expect(keyboard.backgroundColor).toBe(before.backgroundColor)
      expectStableGeometry(before, keyboard)
      await page.keyboard.press('Escape')
    }
  }
})

test('button variants preserve semantic surfaces and expose keyboard-only focus', async ({ page }) => {
  await installMockApi(page)
  await page.goto('/components-lab')

  const buttonSection = page.locator('.lab-section').filter({ hasText: 'variant × size' })
  for (const variant of ['default', 'destructive', 'secondary', 'outline', 'ghost', 'link']) {
    const row = buttonSection.locator('.lab__row').filter({ hasText: variant }).first()
    const button = row.getByRole('button').first()
    await blurWithPointer(page)
    const before = await styleOf(button)

    await button.click()
    await page.mouse.move(0, 0)
    const pointer = await styleOf(button)
    expect(pointer.borderColor).toBe(before.borderColor)
    expect(pointer.backgroundColor).toBe(before.backgroundColor)
    expect(pointer.boxShadow).toBe(before.boxShadow)

    await blurWithPointer(page)
    await tabTo(page, button)
    const keyboard = await styleOf(button)
    expectStableGeometry(before, keyboard)
    expect(keyboard.outlineStyle).toBe('none')

    if (variant === 'default' || variant === 'destructive') {
      expect(keyboard.backgroundColor).toBe(before.backgroundColor)
      expect(keyboard.color).toBe(before.color)
      expect(keyboard.borderColor).not.toBe(before.borderColor)
    } else if (variant === 'outline' || variant === 'secondary') {
      expect(keyboard.backgroundColor).toBe(before.backgroundColor)
      expect(keyboard.borderColor).not.toBe(before.borderColor)
    } else if (variant === 'ghost') {
      expect(keyboard.backgroundColor).not.toBe(before.backgroundColor)
    } else {
      expect(keyboard.textDecorationLine).toContain('underline')
      expect(keyboard.backgroundColor).toBe(before.backgroundColor)
    }
  }
})

test('pointer and F12 never leave a sidebar halo while keyboard focus remains visible', async ({
  page,
}) => {
  await installMockApi(page, {
    sessions: [ongoingSession],
    interviewDetail: { ...ongoingSession, messages: [], stages: [] },
  })
  await page.goto('/interview')

  const settings = page.getByRole('button', { name: '设置' })
  const baseline = await styleOf(settings)
  await settings.click()
  await expect(page.getByRole('dialog', { name: '全局设置' })).toBeVisible()
  await page.mouse.click(1400, 880)
  await expect(page.getByRole('dialog', { name: '全局设置' })).toHaveCount(0)
  await page.mouse.move(0, 0)
  await page.keyboard.press('F12')
  await expect(page.locator('html')).toHaveAttribute('data-input-intent', 'pointer')

  const afterF12 = await styleOf(settings)
  expect(afterF12.borderColor).toBe(baseline.borderColor)
  expect(afterF12.backgroundColor).toBe(baseline.backgroundColor)
  expect(afterF12.boxShadow).toBe(baseline.boxShadow)

  await blurWithPointer(page)
  await tabTo(page, settings)
  await expect(page.locator('html')).toHaveAttribute('data-input-intent', 'keyboard')
  const keyboard = await styleOf(settings)
  expect(keyboard.borderColor).not.toBe(baseline.borderColor)
  expect(keyboard.boxShadow).toBe(baseline.boxShadow)
  expectStableGeometry(baseline, keyboard)

  const pin = page.getByRole('button', { name: '置顶会话' })
  await tabTo(page, pin)
  const actions = pin.locator('xpath=..')
  await expect(actions).toHaveCSS('opacity', '1')
  await expect(actions).toHaveCSS('pointer-events', 'auto')
})

test('selected settings surface and report navigation keep independent keyboard semantics', async ({
  page,
}) => {
  await installMockApi(page, {
    sessions: [finishedSession],
    interviewDetail: { ...finishedSession, messages: [], stages: [] },
  })
  await page.goto('/interview')

  await page.getByRole('button', { name: '设置' }).click()
  const dialog = page.getByRole('dialog', { name: '全局设置' })
  await dialog.getByRole('button', { name: '主题' }).click()
  const selectedTheme = dialog.locator('.theme-option.is-active')
  await expect(selectedTheme).toBeEnabled()
  const selectedBefore = await styleOf(selectedTheme)
  await tabTo(page, selectedTheme)
  const selectedFocus = await styleOf(selectedTheme)
  expect(selectedFocus.borderWidth).toBe('1px')
  expect(selectedFocus.borderColor).not.toBe(selectedBefore.borderColor)
  expect(selectedFocus.boxShadow).toBe(selectedBefore.boxShadow)
  await page.keyboard.press('Escape')
  await expect(dialog).toHaveCount(0)

  await page.getByRole('button', { name: '打开已结束会话 Java 后端工程师' }).click()
  await page.getByRole('button', { name: '报告', exact: true }).click()
  const carousel = page.locator('.question-review-carousel')
  await expect(carousel).not.toHaveAttribute('tabindex', /.+/)
  const next = page.getByRole('button', { name: '下一题' })
  await blurWithPointer(page)
  await tabTo(page, next)
  await page.keyboard.press('Enter')
  await expect(page.locator('.question-review-carousel__counter')).toContainText('2 / 2')
})

test('forced colors restores a system outline without changing normal-theme geometry', async ({
  page,
}) => {
  await installMockApi(page)
  await page.emulateMedia({ forcedColors: 'active' })
  await page.goto('/components-lab')
  const button = page.getByRole('button', { name: '打开示例 Dialog' })
  await tabTo(page, button)
  const style = await styleOf(button)
  expect(style.outlineStyle).toBe('solid')
  expect(Number.parseFloat(style.outlineWidth)).toBeGreaterThanOrEqual(2)
})
""",
)

replace_once(
    "frontend/tests/flows/settings-toast.spec.ts",
    "  expect(geometry?.borderWidth).toBe('0px')",
    "  expect(geometry?.borderWidth).toBe('1px')",
)

# Final invariant scan before formatter/test execution.
for forbidden in [
    "focus-visible:ring-",
    "focus:ring-",
    "ring-offset-",
    "shadow-icon-action-focus",
    "ui-focus-quiet",
    "focus-visible:shadow-",
]:
    hits = []
    for root in [ROOT / "frontend/src", ROOT / "frontend/scripts"]:
        for file in root.rglob("*"):
            if file.suffix not in {".vue", ".ts", ".css", ".cjs"}:
                continue
            if forbidden in file.read_text(encoding="utf-8-sig"):
                hits.append(str(file.relative_to(ROOT)))
    if hits:
        raise RuntimeError(f"Forbidden focus pattern {forbidden!r}: {hits}")

print("Focus rebuild applied successfully")
