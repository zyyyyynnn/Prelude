<script setup lang="ts">
import type { ComboboxInputEmits, ComboboxInputProps } from "reka-ui"
import type { HTMLAttributes } from "vue"
import { reactiveOmit } from "@vueuse/core"
import { ComboboxInput, ComboboxTrigger, useForwardPropsEmits } from "reka-ui"
import { ChevronDown } from "@lucide/vue"
import { cn } from "@/lib/utils"
import { dropdownTriggerVariants } from "@/components/ui/shared-dropdown"

defineOptions({
  inheritAttrs: false,
})

const props = defineProps<ComboboxInputProps & { class?: HTMLAttributes["class"] }>()
const emits = defineEmits<ComboboxInputEmits>()

const delegatedProps = reactiveOmit(props, "class")
const forwarded = useForwardPropsEmits(delegatedProps, emits)
</script>

<template>
  <div :class="cn(dropdownTriggerVariants(), props.class)">
    <ComboboxInput
      v-bind="{ ...forwarded, ...$attrs }"
      class="flex-1 bg-transparent outline-none placeholder:text-muted-foreground truncate"
    />
    <ComboboxTrigger tabindex="-1" class="shrink-0 outline-none border-0 bg-transparent flex items-center justify-center p-0">
      <ChevronDown class="w-4 h-4 opacity-50 shrink-0" />
    </ComboboxTrigger>
  </div>
</template>
