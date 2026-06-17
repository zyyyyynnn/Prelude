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

onMounted(() => {
  if (!hostRef.value) {
    return
  }

  const brand = resolveShaderColor('--color-brand', 'var(--color-brand)')
  const brandLight = resolveShaderColor('--color-brand-light', 'var(--color-brand)')
  const surface = resolveShaderColor('--color-surface', 'var(--color-surface)')
  const bg = resolveShaderColor('--color-bg', 'var(--color-bg)')
  const text = resolveShaderColor('--color-text-primary', 'var(--color-text-primary)')

  root = createRoot(hostRef.value)
  root.render(createElement(Metaballs, {
    speed: 1.7,
    count: 10,
    size: 1,
    scale: 1,
    colors: [surface, brandLight, brand, text, bg],
    colorBack: bg,
    style: {
      width: '100%',
      height: '100%',
      backgroundColor: bg,
      borderRadius: 'var(--radius-3xl)',
      boxShadow: 'var(--shadow-ring)',
    },
  }))
})

onBeforeUnmount(() => {
  root?.unmount()
  root = null
})
</script>

<template>
  <div ref="hostRef" class="brand-metaballs" aria-hidden="true" />
</template>
