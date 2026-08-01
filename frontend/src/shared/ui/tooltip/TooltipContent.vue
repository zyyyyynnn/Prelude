<script setup lang="ts">
import type { TooltipContentEmits, TooltipContentProps } from 'reka-ui'
import type { HTMLAttributes } from 'vue'
import { reactiveOmit } from '@vueuse/core'
import { TooltipContent, TooltipPortal, useForwardPropsEmits } from 'reka-ui'
import { cn } from '@/shared/lib/utils'

defineOptions({
  inheritAttrs: false,
})

const props = withDefaults(
  defineProps<TooltipContentProps & { class?: HTMLAttributes['class'] }>(),
  {
    sideOffset: 6,
  },
)

const emits = defineEmits<TooltipContentEmits>()

const delegatedProps = reactiveOmit(props, 'class')

const forwarded = useForwardPropsEmits(delegatedProps, emits)
</script>

<template>
  <TooltipPortal>
    <TooltipContent
      v-bind="{ ...forwarded, ...$attrs }"
      :side-offset="props.sideOffset"
      :class="
        cn(
          'z-[110] w-max max-w-[var(--content-tooltip-max-inline-size)] overflow-hidden break-words rounded-md border border-input bg-surface px-[var(--spacing-sm)] py-[var(--spacing-xs)] text-xs font-serif text-popover-foreground shadow-[var(--shadow-whisper)] [animation-duration:var(--motion-duration-base)] [animation-timing-function:var(--motion-ease-standard)] animate-in fade-in-0 data-[state=closed]:animate-out data-[state=closed]:fade-out-0',
          props.class,
        )
      "
    >
      <slot />
    </TooltipContent>
  </TooltipPortal>
</template>
