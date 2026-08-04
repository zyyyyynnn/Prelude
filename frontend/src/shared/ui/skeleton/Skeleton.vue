<template>
  <div class="ui-skeleton" aria-hidden="true" />
</template>

<style scoped>
.ui-skeleton {
  min-block-size: var(--ui-height-sm);
  border-radius: var(--radius-sm);
  background: var(--color-surface-muted);
  animation: ui-skeleton-pulse var(--motion-duration-slow) var(--motion-ease-standard) infinite
    alternate;
}

@keyframes ui-skeleton-pulse {
  from {
    opacity: 0.55;
  }
  to {
    opacity: 1;
  }
}

@media (prefers-reduced-motion: reduce) {
  .ui-skeleton {
    animation: none;
  }
}
</style>
