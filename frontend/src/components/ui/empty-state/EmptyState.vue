<script setup lang="ts">
const props = defineProps<{
  description?: string
}>()
</script>

<template>
  <div class="flex flex-col items-center justify-center p-8 text-center">
    <div class="text-muted-foreground mb-4">
      <svg xmlns="http://www.w3.org/2000/svg" width="48" height="48" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" class="opacity-50">
        <rect width="18" height="18" x="3" y="3" rx="2" ry="2"/>
        <line x1="9" x2="15" y1="9" y2="9"/>
        <line x1="9" x2="15" y1="15" y2="15"/>
      </svg>
    </div>
    <p class="text-sm text-muted-foreground">{{ description || '暂无数据' }}</p>
  </div>
</template>
