<script setup lang="ts">
import { computed, type CSSProperties } from 'vue'

const props = defineProps<{
  items: string[]
  modelValue: string
}>()

const emit = defineEmits<{
  (e: 'update:modelValue', value: string): void
}>()

const activeIndex = computed(() => {
  const idx = props.items.indexOf(props.modelValue)
  return idx === -1 ? 0 : idx
})

const itemCount = computed(() => Math.max(props.items.length, 1))

// Item count is a sizing primitive: a single token multiplied by N item widths plus track inset.
// surface only depends on it through CSS variables, so we expose the count via inline style.
const segmentedStyle = computed(
  () =>
    ({
      '--segmented-item-count': itemCount.value,
    }) as CSSProperties,
)
</script>

<template>
  <div class="segmented-control" :style="segmentedStyle">
    <div
      class="segmented-control__pill"
      :style="{ transform: `translateX(${activeIndex * 100}%)` }"
    ></div>
    <button
      v-for="item in items"
      :key="item"
      class="segmented-control__item text-sm"
      :class="{ 'is-active': item === modelValue }"
      @click="emit('update:modelValue', item)"
    >
      {{ item }}
    </button>
  </div>
</template>

<style scoped>
.segmented-control {
  /* 组件级几何变量：pill 宽度、圆角和层级集中声明，避免 calc 散落在属性里。 */
  --segmented-track-inset: var(--spacing-0-5);
  --segmented-pill-radius: calc(var(--radius-md) - var(--segmented-track-inset));
  --segmented-pill-width: calc(
    (100% - var(--segmented-track-inset) * 2) / var(--segmented-item-count, 1)
  );
  --segmented-layer-pill: 1;
  --segmented-layer-item: 2;

  position: relative;
  display: grid;
  grid-template-columns: repeat(
    var(--segmented-item-count, 1),
    minmax(var(--ui-segmented-item-min-inline-size), 1fr)
  );
  block-size: var(--ui-height-base);
  min-inline-size: calc(
    var(--ui-segmented-item-min-inline-size) * var(--segmented-item-count, 1) +
      var(--segmented-track-inset) * 2
  );
  background-color: var(--color-surface-muted);
  border: 1px solid var(--color-border-warm);
  border-radius: var(--radius-md);
  padding: var(--segmented-track-inset);
  isolation: isolate;
}

.segmented-control__pill {
  position: absolute;
  top: var(--segmented-track-inset);
  bottom: var(--segmented-track-inset);
  left: var(--segmented-track-inset);
  inline-size: var(--segmented-pill-width);
  background-color: var(--color-surface);
  border-radius: var(--segmented-pill-radius);
  box-shadow: var(--shadow-ring);
  transition: transform var(--motion-duration-base) var(--motion-ease-standard);
  z-index: var(--segmented-layer-pill);
}

.segmented-control__item {
  position: relative;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 0 var(--spacing-md);
  min-inline-size: 0;
  white-space: nowrap;
  font-weight: 500;
  font-family: var(--font-serif);
  color: var(--color-text-secondary);
  border-radius: var(--segmented-pill-radius);
  transition:
    color var(--motion-duration-base) var(--motion-ease-standard),
    background-color var(--motion-duration-base) var(--motion-ease-standard);
  z-index: var(--segmented-layer-item);
  cursor: pointer;
}

.segmented-control__item.is-active {
  color: var(--color-text-primary);
}

.segmented-control__item:focus-visible {
  outline: none;
  box-shadow: var(--shadow-icon-action-focus);
}
</style>
