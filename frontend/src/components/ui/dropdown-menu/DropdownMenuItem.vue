<script setup lang="ts">
import type { DropdownMenuItemProps } from "reka-ui"
import type { VariantProps } from "class-variance-authority"
import type { HTMLAttributes } from "vue"
import { reactiveOmit } from "@vueuse/core"
import { DropdownMenuItem, useForwardProps } from "reka-ui"
import { cn } from "@/lib/utils"
import { dropdownItemVariants } from "@/components/ui/shared-dropdown"

type DropdownItemVariants = VariantProps<typeof dropdownItemVariants>

const props = defineProps<DropdownMenuItemProps & { class?: HTMLAttributes["class"], inset?: boolean, size?: DropdownItemVariants["size"] }>()

const delegatedProps = reactiveOmit(props, "class", "inset", "size")

const forwardedProps = useForwardProps(delegatedProps)
</script>

<template>
  <DropdownMenuItem
    v-bind="forwardedProps"
    :class="cn(
      dropdownItemVariants({ size }),
      inset && 'pl-8',
      props.class,
    )"
  >
    <slot />
  </DropdownMenuItem>
</template>
