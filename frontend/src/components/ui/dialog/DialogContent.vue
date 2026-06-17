<script setup lang="ts">
import type { DialogContentEmits, DialogContentProps } from "reka-ui"
import type { HTMLAttributes } from "vue"
import { reactiveOmit } from "@vueuse/core"
import { X } from "@lucide/vue"
import {
  DialogClose,
  DialogContent,
  DialogOverlay,
  DialogPortal,
  useForwardPropsEmits,
} from "reka-ui"
import { cn } from "@/lib/utils"

const props = defineProps<DialogContentProps & { class?: HTMLAttributes["class"] }>()
const emits = defineEmits<DialogContentEmits>()

const delegatedProps = reactiveOmit(props, "class")

const forwarded = useForwardPropsEmits(delegatedProps, emits)
</script>

<template>
  <DialogPortal>
    <DialogOverlay
      class="fixed inset-0 z-[101] bg-[var(--mask-overlay)] dialog-overlay"
    />
    <DialogContent
      v-bind="forwarded"
      :class="
        cn(
          'fixed left-1/2 top-1/2 z-[101] grid w-full max-w-lg gap-4 border border-transparent bg-surface p-6 shadow-[var(--shadow-modal)] sm:rounded-lg dialog-content',
          props.class,
        )"
    >
      <slot />

      <DialogClose
        class="absolute right-4 top-4 rounded-sm opacity-70 ring-offset-background transition-opacity [transition-duration:var(--motion-duration-base)] [transition-timing-function:var(--motion-ease-standard)] hover:opacity-100 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-focus disabled:pointer-events-none data-[state=open]:bg-accent data-[state=open]:text-muted-foreground"
      >
        <X class="w-4 h-4" />
        <span class="sr-only">Close</span>
      </DialogClose>
    </DialogContent>
  </DialogPortal>
</template>

<style>
/* Overlay Animations */
.dialog-overlay[data-state="open"] {
  animation: dialog-overlay-fade-in var(--motion-duration-base) var(--motion-ease-standard);
}
.dialog-overlay[data-state="closed"] {
  animation: dialog-overlay-fade-out var(--motion-duration-base) var(--motion-ease-standard);
}

@keyframes dialog-overlay-fade-in {
  from { opacity: 0; }
  to { opacity: 1; }
}
@keyframes dialog-overlay-fade-out {
  from { opacity: 1; }
  to { opacity: 0; }
}

/* Content Animations (Floating in/out 4px matching Voice/Text switch) */
.dialog-content {
  transform: translate(-50%, -50%);
}
.dialog-content[data-state="open"] {
  animation: dialog-content-enter var(--motion-duration-base) var(--motion-ease-standard);
}
.dialog-content[data-state="closed"] {
  animation: dialog-content-leave var(--motion-duration-base) var(--motion-ease-standard);
}

@keyframes dialog-content-enter {
  from {
    opacity: 0;
    transform: translate(-50%, calc(-50% + 4px));
  }
  to {
    opacity: 1;
    transform: translate(-50%, -50%);
  }
}
@keyframes dialog-content-leave {
  from {
    opacity: 1;
    transform: translate(-50%, -50%);
  }
  to {
    opacity: 0;
    transform: translate(-50%, calc(-50% - 4px));
  }
}
/* 允许调用方通过 .dialog-no-close 隐藏默认关闭按钮 */
.dialog-no-close > button.absolute.right-4.top-4 {
  display: none;
}
</style>
