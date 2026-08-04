type InputIntent = 'pointer' | 'keyboard'

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
