<script setup lang="ts">
import type { HTMLAttributes } from 'vue'
import { cn } from '@/shared/lib/utils'

const props = defineProps<{
  class?: HTMLAttributes['class']
}>()
</script>

<template>
  <div
    :class="
      cn(
        'rounded-lg border border-transparent bg-card text-card-foreground shadow-none',
        props.class,
      )
    "
  >
    <slot />
  </div>
</template>
