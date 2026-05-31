<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref } from 'vue'
import { createElement } from 'react'
import { createRoot, type Root } from 'react-dom/client'
import { Metaballs } from '@paper-design/shaders-react'

const hostRef = ref<HTMLDivElement | null>(null)
let root: Root | null = null

onMounted(() => {
  if (!hostRef.value) {
    return
  }

  root = createRoot(hostRef.value)
  root.render(createElement(Metaballs, {
    speed: 1.7,
    count: 10,
    size: 1,
    scale: 1,
    colors: ['#E9DED6', '#C6A99A', '#9E7B6A', '#5F463B', '#2F241F'],
    colorBack: '#00000000',
    style: {
      width: '100%',
      height: '100%',
      backgroundColor: '#F5F4ED',
      borderRadius: '9999px',
      boxShadow: '#00000033 2px 2px 2px',
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
