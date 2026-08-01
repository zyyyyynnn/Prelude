<script setup lang="ts">
import { computed, ref } from 'vue'
import type { HTMLAttributes } from 'vue'
import Tooltip from './Tooltip.vue'
import TooltipContent from './TooltipContent.vue'
import TooltipProvider from './TooltipProvider.vue'
import TooltipTrigger from './TooltipTrigger.vue'
import { cn } from '@/shared/lib/utils'

defineOptions({
  inheritAttrs: false,
})

const props = withDefaults(
  defineProps<{
    text?: string | null
    fallback?: string
    as?: string
    anchor?: 'self' | 'parent'
    class?: HTMLAttributes['class']
  }>(),
  {
    fallback: '',
    as: 'span',
    anchor: 'self',
  },
)

const triggerElement = ref<HTMLElement | null>(null)
const anchorReference = computed(() =>
  props.anchor === 'parent' ? (triggerElement.value?.parentElement ?? undefined) : undefined,
)
</script>

<template>
  <TooltipProvider>
    <Tooltip>
      <TooltipTrigger as-child :reference="anchorReference">
        <component
          :is="as"
          ref="triggerElement"
          v-bind="$attrs"
          :class="cn('truncate', props.class)"
        >
          {{ text || fallback }}
        </component>
      </TooltipTrigger>
      <TooltipContent>
        {{ text || fallback }}
      </TooltipContent>
    </Tooltip>
  </TooltipProvider>
</template>
