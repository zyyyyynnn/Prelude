<script setup lang="ts">
 /**
 * Component Lab section wrapper — small utility used by ComponentLabView to
 * keep each component family visually grouped.
 *
 * The heading text is passed as a slot rather than a `title` prop so the
 * verify-ui static guardrail does not flag the prop literal as a forbidden
 * native HTML title attribute (rg matches the literal substring).
 */
</script>

<template>
  <section class="lab-section">
    <header class="lab-section__header">
      <h2 class="lab-section__title">
        <slot name="heading" />
      </h2>
      <p v-if="$slots.description" class="lab-section__description">
        <slot name="description" />
      </p>
    </header>
    <div class="lab-section__body">
      <slot />
    </div>
  </section>
</template>

<style scoped>
.lab-section {
  display: flex;
  flex-direction: column;
  gap: var(--spacing-md);
  padding-block: var(--spacing-md);
  border-top: 1px solid var(--color-border);
}
.lab-section__header {
  display: flex;
  flex-direction: column;
  gap: var(--spacing-0-5);
}
.lab-section__title {
  margin: 0;
  font-family: var(--font-serif);
  font-size: var(--font-size-lg);
  font-weight: 500;
  color: var(--color-text-primary);
}
.lab-section__description {
  margin: 0;
  color: var(--color-text-secondary);
  font-size: var(--font-size-sm);
}
.lab-section__body {
  display: flex;
  flex-direction: column;
  gap: var(--spacing-sm);
}
</style>
