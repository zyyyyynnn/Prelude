<script setup lang="ts">
import type { ComboboxItemEmits, ComboboxItemProps } from 'reka-ui'
import type { HTMLAttributes } from 'vue'
import { reactiveOmit } from '@vueuse/core'
import { Check } from '@lucide/vue'
import { ComboboxItem, ComboboxItemIndicator, useForwardPropsEmits } from 'reka-ui'
import { cn } from '@/shared/lib/utils'
import { dropdownItemVariants } from '@/shared/ui/shared-dropdown'

const props = defineProps<ComboboxItemProps & { class?: HTMLAttributes['class'] }>()
const emits = defineEmits<ComboboxItemEmits>()

const delegatedProps = reactiveOmit(props, 'class')
const forwarded = useForwardPropsEmits(delegatedProps, emits)
</script>

<template>
  <ComboboxItem v-bind="forwarded" :class="cn(dropdownItemVariants(), 'pl-8', props.class)">
    <span class="absolute left-2 flex h-3.5 w-3.5 items-center justify-center">
      <ComboboxItemIndicator>
        <Check class="h-4 w-4" />
      </ComboboxItemIndicator>
    </span>

    <slot />
  </ComboboxItem>
</template>
