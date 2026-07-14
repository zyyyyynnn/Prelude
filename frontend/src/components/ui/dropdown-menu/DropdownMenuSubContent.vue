<script setup lang="ts">
import type { DropdownMenuSubContentEmits, DropdownMenuSubContentProps } from 'reka-ui'
import type { HTMLAttributes } from 'vue'
import { reactiveOmit } from '@vueuse/core'
import { DropdownMenuSubContent, useForwardPropsEmits } from 'reka-ui'
import { cn } from '@/lib/utils'
import { dropdownContentClasses } from '@/components/ui/shared-dropdown'

const props = defineProps<DropdownMenuSubContentProps & { class?: HTMLAttributes['class'] }>()
const emits = defineEmits<DropdownMenuSubContentEmits>()

const delegatedProps = reactiveOmit(props, 'class')

const forwarded = useForwardPropsEmits(delegatedProps, emits)
</script>

<template>
  <DropdownMenuSubContent
    v-bind="forwarded"
    :class="cn(dropdownContentClasses, 'min-w-32', props.class)"
  >
    <slot />
  </DropdownMenuSubContent>
</template>
