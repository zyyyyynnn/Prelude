<script setup lang="ts">
import { computed } from 'vue'

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
</script>

<template>
  <div class="segmented-control">
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
  /* 组件级几何变量：pill 几何集中声明，避免 calc 散落在属性里 */
  --segmented-pill-radius: calc(var(--radius-md) - var(--spacing-0-5));
  --segmented-pill-width: calc((100% - var(--spacing-xs)) / v-bind('items.length'));
  position: relative;
  display: flex;
  height: var(--ui-height-base);
  min-width: calc(var(--ui-height-base) * 4.75);
  background-color: var(--color-surface-muted);
  border: 1px solid var(--color-border-warm);
  border-radius: var(--radius-md);
  padding: var(--spacing-0-5);
}

.segmented-control__pill {
  position: absolute;
  top: var(--spacing-0-5);
  bottom: var(--spacing-0-5);
  left: var(--spacing-0-5);
  width: var(--segmented-pill-width);
  background-color: var(--color-surface);
  border-radius: var(--segmented-pill-radius);
  box-shadow: var(--shadow-ring);
  transition: transform var(--motion-duration-base) var(--motion-ease-standard);
  z-index: 1;
}

.segmented-control__item {
  position: relative;
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 0 var(--spacing-md);
  min-width: 0;
  white-space: nowrap;
  font-weight: 500;
  font-family: var(--font-serif);
  color: var(--color-text-secondary);
  border-radius: var(--segmented-pill-radius);
  transition:
    color var(--motion-duration-base) var(--motion-ease-standard),
    background-color var(--motion-duration-base) var(--motion-ease-standard);
  z-index: 2;
  cursor: pointer;
}

.segmented-control__item.is-active {
  color: var(--color-text-primary);
}

.segmented-control__item:focus-visible {
  outline: none;
  box-shadow: inset 0 0 0 var(--spacing-0-5) var(--color-brand);
}
</style>
