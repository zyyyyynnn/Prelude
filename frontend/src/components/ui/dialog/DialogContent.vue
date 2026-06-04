<script setup lang="ts">
import type { DialogContentEmits, DialogContentProps } from "reka-ui"
import type { HTMLAttributes } from "vue"
import { reactiveOmit } from "@vueuse/core"
import { X } from "lucide-vue-next"
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
      class="fixed inset-0 z-[101] dialog-overlay"
      style="background-color: var(--mask-overlay, rgba(20, 19, 19, 0.38))"
    />
    <DialogContent
      v-bind="forwarded"
      :class="
        cn(
          'fixed left-1/2 top-1/2 z-[101] grid w-full max-w-lg gap-4 border bg-background p-6 shadow-lg sm:rounded-lg dialog-content',
          props.class,
        )"
    >
      <slot />

      <DialogClose
        class="absolute right-4 top-4 rounded-sm opacity-70 ring-offset-background transition-opacity hover:opacity-100 focus:outline-none focus:ring-2 focus:ring-ring focus:ring-offset-2 disabled:pointer-events-none data-[state=open]:bg-accent data-[state=open]:text-muted-foreground"
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
  animation: dialog-overlay-fade-in 0.3s ease-in-out;
}
.dialog-overlay[data-state="closed"] {
  animation: dialog-overlay-fade-out 0.3s ease-in-out;
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
  animation: dialog-content-enter 0.3s ease-in-out;
}
.dialog-content[data-state="closed"] {
  animation: dialog-content-leave 0.3s ease-in-out;
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
</style>
