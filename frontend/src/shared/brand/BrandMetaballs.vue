<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref } from 'vue'
import { createElement } from 'react'
import { createRoot, type Root } from 'react-dom/client'
import { Metaballs } from '@paper-design/shaders-react'

const hostRef = ref<HTMLDivElement | null>(null)
let root: Root | null = null

function normalizeShaderColor(value: string) {
  const srgbMatch = value.match(/^color\(srgb\s+([0-9.]+)\s+([0-9.]+)\s+([0-9.]+)/)
  if (!srgbMatch) return value
  const [, r, g, b] = srgbMatch
  return `rgb(${Math.round(Number(r) * 255)}, ${Math.round(Number(g) * 255)}, ${Math.round(Number(b) * 255)})`
}

function resolveShaderColor(name: string, fallback: string) {
  const styles = getComputedStyle(document.documentElement)
  const raw = styles.getPropertyValue(name).trim() || fallback
  const probe = document.createElement('span')
  probe.style.color = raw
  document.body.appendChild(probe)
  const computed = getComputedStyle(probe).color
  probe.remove()
  return normalizeShaderColor(computed || raw)
}

function renderMetaballs() {
  if (!hostRef.value) {
    return
  }

  root ??= createRoot(hostRef.value)

  const colors = [
    resolveShaderColor('--brand-metaballs-1', 'var(--color-surface)'),
    resolveShaderColor('--brand-metaballs-2', 'var(--color-brand-light)'),
    resolveShaderColor('--brand-metaballs-3', 'var(--color-brand)'),
    resolveShaderColor('--brand-metaballs-4', 'var(--color-ring-deep)'),
    resolveShaderColor('--brand-metaballs-5', 'var(--color-text-primary)'),
  ]
  const bg = resolveShaderColor('--brand-metaballs-bg', 'var(--color-bg)')
  const renderKey = `${bg}:${colors.join(':')}`

  root.render(
    createElement(Metaballs, {
      key: renderKey,
      speed: 1.7,
      count: 10,
      size: 1,
      scale: 1,
      colors,
      colorBack: bg,
      style: {
        width: '100%',
        height: '100%',
        backgroundColor: bg,
        borderRadius: 'var(--radius-3xl)',
        boxShadow: 'var(--brand-metaballs-shadow)',
      },
    }),
  )
}

onMounted(() => {
  renderMetaballs()
  window.addEventListener('prelude-theme-change', renderMetaballs)
})

onBeforeUnmount(() => {
  window.removeEventListener('prelude-theme-change', renderMetaballs)
  root?.unmount()
  root = null
})
</script>

<template>
  <div ref="hostRef" class="brand-metaballs" aria-hidden="true" />
</template>
