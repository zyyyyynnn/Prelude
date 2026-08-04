<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch, type HTMLAttributes } from 'vue'
import { Skeleton } from '@/shared/ui/skeleton'

type AvatarState =
  | 'profile-pending'
  | 'no-avatar'
  | 'image-loading'
  | 'image-loaded'
  | 'image-error'

const props = withDefaults(
  defineProps<{
    src?: string | null
    previewSrc?: string | null
    name?: string | null
    pending?: boolean
    alt?: string
    size?: number | string
    class?: HTMLAttributes['class']
  }>(),
  {
    src: null,
    previewSrc: null,
    name: '',
    pending: false,
    alt: '',
    size: 40,
    class: undefined,
  },
)

const emit = defineEmits<{
  (event: 'image-loaded', src: string): void
  (event: 'image-error', src: string): void
}>()

const state = ref<AvatarState>('no-avatar')
const visibleSrc = ref<string | null>(null)
const loadedCanonicalSrc = ref<string | null>(null)
let generation = 0
let activeImage: HTMLImageElement | null = null

const initials = computed(() => (props.name?.trim().slice(0, 1) || 'P').toUpperCase())
const accessibleAlt = computed(() => props.alt || (props.name ? `${props.name}的头像` : '用户头像'))
const geometryStyle = computed(() => {
  const value = typeof props.size === 'number' ? `${props.size}px` : props.size
  return { inlineSize: value, blockSize: value }
})
const isBusy = computed(
  () => props.pending || state.value === 'image-loading' || state.value === 'profile-pending',
)

function showFallback(nextState: 'no-avatar' | 'image-error') {
  visibleSrc.value = null
  state.value = nextState
}

async function preloadCanonical(src: string, previewSrc: string | null, token: number) {
  if (loadedCanonicalSrc.value === src) {
    visibleSrc.value = previewSrc || src
    state.value = previewSrc ? 'image-loading' : 'image-loaded'
    return
  }

  const image = new Image()
  activeImage = image
  const handleImageFailure = () => {
    if (token !== generation || activeImage !== image) return
    loadedCanonicalSrc.value = null
    if (previewSrc) {
      visibleSrc.value = previewSrc
      state.value = 'image-error'
    } else {
      showFallback('image-error')
    }
    emit('image-error', src)
  }
  image.onload = async () => {
    if (typeof image.decode === 'function') {
      try {
        await image.decode()
      } catch {
        handleImageFailure()
        return
      }
    }
    if (token !== generation || activeImage !== image) return
    loadedCanonicalSrc.value = src
    visibleSrc.value = src
    state.value = 'image-loaded'
    emit('image-loaded', src)
  }
  image.onerror = handleImageFailure
  state.value = 'image-loading'
  visibleSrc.value = previewSrc
  image.src = src
}

function syncState() {
  const token = ++generation
  activeImage = null

  if (props.pending) {
    loadedCanonicalSrc.value = null
    visibleSrc.value = null
    state.value = 'profile-pending'
    return
  }

  const src = props.src?.trim() || ''
  const previewSrc = props.previewSrc?.trim() || null
  if (!src && !previewSrc) {
    loadedCanonicalSrc.value = null
    showFallback('no-avatar')
    return
  }
  if (!src && previewSrc) {
    visibleSrc.value = previewSrc
    state.value = 'image-loaded'
    return
  }

  void preloadCanonical(src, previewSrc, token)
}

watch(() => [props.src, props.previewSrc, props.pending], syncState, { immediate: true })

onBeforeUnmount(() => {
  generation++
  activeImage = null
})
</script>

<template>
  <div
    :class="['user-avatar', props.class]"
    :style="geometryStyle"
    :aria-busy="isBusy"
    :data-avatar-state="state"
  >
    <Skeleton
      v-if="state === 'profile-pending' || (state === 'image-loading' && !visibleSrc)"
      class="user-avatar__skeleton"
    />
    <img
      v-else-if="
        (state === 'image-loaded' || state === 'image-loading' || state === 'image-error') &&
        visibleSrc
      "
      :src="visibleSrc"
      :alt="accessibleAlt"
      decoding="async"
    />
    <span v-else class="user-avatar__initials" role="img" :aria-label="accessibleAlt">{{
      initials
    }}</span>
  </div>
</template>

<style scoped>
.user-avatar {
  position: relative;
  display: grid;
  flex: 0 0 auto;
  place-items: center;
  overflow: hidden;
  border-radius: var(--radius-full);
  background: var(--color-surface-muted);
  color: var(--color-brand);
  font-family: var(--font-serif);
  font-size: var(--font-size-md);
  font-weight: 600;
}

.user-avatar :deep(.ui-skeleton) {
  inline-size: 100%;
  block-size: 100%;
  min-block-size: 0;
  border-radius: inherit;
}

.user-avatar img {
  display: block;
  inline-size: 100%;
  block-size: 100%;
  object-fit: cover;
}

.user-avatar__initials {
  display: grid;
  place-items: center;
  inline-size: 100%;
  block-size: 100%;
}
</style>
