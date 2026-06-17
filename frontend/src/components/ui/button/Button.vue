<script setup lang="ts">
import type { PrimitiveProps } from "reka-ui"
import type { HTMLAttributes } from "vue"
import type { ButtonVariants } from "."
import { Primitive } from "reka-ui"
import { Loader2 } from "@lucide/vue"
import { cn } from "@/lib/utils"
import { buttonVariants } from "."

interface Props extends PrimitiveProps {
  variant?: ButtonVariants["variant"]
  size?: ButtonVariants["size"]
  class?: HTMLAttributes["class"]
  loading?: boolean
  disabled?: boolean
}

const props = withDefaults(defineProps<Props>(), {
  as: "button",
})
</script>

<template>
  <Primitive
    :as="as"
    :as-child="asChild"
    :class="cn(buttonVariants({ variant, size }), 'relative overflow-hidden transition-colors [transition-duration:var(--motion-duration-base)] [transition-timing-function:var(--motion-ease-standard)]', props.class)"
    :disabled="loading || disabled"
  >
    <span 
      :class="cn('flex w-full h-full min-w-0 items-center transition-opacity [transition-duration:var(--motion-duration-base)] [transition-timing-function:var(--motion-ease-standard)]', loading ? 'opacity-0' : 'opacity-100')"
      style="gap: inherit; justify-content: inherit; align-items: inherit; flex-direction: inherit;"
    >
      <slot />
    </span>
    <Transition
      enter-active-class="transition-opacity [transition-duration:var(--motion-duration-base)] [transition-timing-function:var(--motion-ease-standard)]"
      enter-from-class="opacity-0"
      enter-to-class="opacity-100"
      leave-active-class="transition-opacity [transition-duration:var(--motion-duration-base)] [transition-timing-function:var(--motion-ease-standard)]"
      leave-from-class="opacity-100"
      leave-to-class="opacity-0"
    >
      <span v-if="loading" class="absolute inset-0 flex items-center justify-center">
        <Loader2 class="size-4 animate-spin" />
      </span>
    </Transition>
  </Primitive>
</template>
