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
      class="segmented-control__item"
      :class="{ 'is-active': item === modelValue }"
      @click="emit('update:modelValue', item)"
    >
      {{ item }}
    </button>
  </div>
</template>

<style scoped>
.segmented-control {
  position: relative;
  display: flex;
  height: 32px;
  background-color: color-mix(in srgb, var(--color-border) 30%, var(--color-surface));
  border-radius: var(--radius-xl);
  padding: 2px;
  min-width: 160px;
}

.segmented-control__pill {
  position: absolute;
  top: 2px;
  bottom: 2px;
  left: 2px;
  /* Calculate width based on number of items */
  width: calc((100% - 4px) / v-bind('items.length'));
  background-color: var(--color-surface);
  border-radius: var(--radius-lg);
  box-shadow: 0 1px 3px color-mix(in srgb, #000 12%, transparent);
  transition: transform 300ms ease-in-out;
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
  font-size: 13px;
  font-weight: 500;
  color: var(--color-text-secondary);
  border-radius: var(--radius-md);
  transition: color 300ms ease-in-out;
  z-index: 2;
  cursor: pointer;
}

.segmented-control__item.is-active {
  color: var(--color-text-primary);
}

.segmented-control__item:focus-visible {
  outline: 2px solid var(--color-brand);
  outline-offset: -2px;
}
</style>
