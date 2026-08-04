<script lang="ts" setup>
import type { ToasterProps } from 'vue-sonner'
import { reactiveOmit } from '@vueuse/core'
import {
  CircleCheckIcon,
  InfoIcon,
  Loader2Icon,
  OctagonXIcon,
  TriangleAlertIcon,
  XIcon,
} from '@lucide/vue'
import { Toaster as Sonner } from 'vue-sonner'

const props = withDefaults(defineProps<ToasterProps>(), {
  closeButton: true,
})
const delegatedProps = reactiveOmit(props, 'toastOptions', 'theme', 'closeButtonPosition')
</script>

<template>
  <Sonner
    theme="light"
    class="toaster group"
    :toast-options="{
      closeButtonAriaLabel: '关闭系统提示',
      classes: {
        toast:
          'group toast group-[.toaster]:!bg-surface group-[.toaster]:!text-foreground group-[.toaster]:!border-transparent group-[.toaster]:!shadow-[var(--shadow-whisper)] !font-serif rounded-md px-[var(--spacing-md)] py-[var(--spacing-sm)] !pr-[var(--spacing-2xl)] !text-sm',
        description: 'group-[.toast]:text-muted-foreground',
        actionButton: 'group-[.toast]:bg-primary group-[.toast]:text-primary-foreground',
        cancelButton: 'group-[.toast]:bg-muted group-[.toast]:text-muted-foreground',
        closeButton:
          'ui-action ui-action-icon !size-6 !rounded-md !border !bg-surface !text-muted-foreground !shadow-none hover:!bg-surface-hover hover:!text-foreground',
      },
    }"
    v-bind="delegatedProps"
  >
    <template #success-icon>
      <CircleCheckIcon class="size-4" />
    </template>
    <template #info-icon>
      <InfoIcon class="size-4" />
    </template>
    <template #warning-icon>
      <TriangleAlertIcon class="size-4" />
    </template>
    <template #error-icon>
      <OctagonXIcon class="size-4" />
    </template>
    <template #loading-icon>
      <div>
        <Loader2Icon class="size-4 animate-spin" />
      </div>
    </template>
    <template #close-icon>
      <XIcon class="size-4" />
    </template>
  </Sonner>
</template>

<style>
.toaster [data-sonner-toast] {
  pointer-events: auto;
}

.toaster [data-sonner-toast] [data-close-button] {
  border-color: transparent !important;
  --toast-close-button-left: auto;
  --toast-close-button-right: var(--spacing-sm);
  --toast-close-button-top: 50%;
  --toast-close-button-bottom: auto;
  --toast-close-button-transform: translateY(-50%);
}

html:not([data-input-intent]) .toaster [data-sonner-toast] [data-close-button]:focus-visible,
html[data-input-intent='keyboard'] .toaster [data-sonner-toast] [data-close-button]:focus {
  border-color: var(--color-focus-action) !important;
  background-color: var(--color-surface-hover) !important;
}

html[data-input-intent='pointer'] .toaster [data-sonner-toast] [data-close-button]:focus {
  border-color: transparent !important;
}
</style>
