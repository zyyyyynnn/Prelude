<script setup lang="ts">
import type { HTMLAttributes } from 'vue'
import Tooltip from './Tooltip.vue'
import TooltipContent from './TooltipContent.vue'
import TooltipProvider from './TooltipProvider.vue'
import TooltipTrigger from './TooltipTrigger.vue'
import { cn } from '@/lib/utils'

defineOptions({
  inheritAttrs: false,
})

const props = withDefaults(defineProps<{
  text?: string | null
  fallback?: string
  as?: string
  class?: HTMLAttributes['class']
}>(), {
  fallback: '',
  as: 'span',
})
</script>

<template>
  <TooltipProvider>
    <Tooltip>
      <TooltipTrigger as-child>
        <component :is="as" v-bind="$attrs" :class="cn('truncate', props.class)">
          {{ text || fallback }}
        </component>
      </TooltipTrigger>
      <TooltipContent>
        {{ text || fallback }}
      </TooltipContent>
    </Tooltip>
  </TooltipProvider>
</template>
