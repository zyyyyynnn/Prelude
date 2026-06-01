import { computed, onBeforeUnmount, ref } from 'vue'

export function usePopperMatchTrigger() {
  const width = ref(0)
  const height = ref(0)
  let ro: ResizeObserver | null = null

  function bind(el: HTMLElement | null) {
    if (ro) {
      ro.disconnect()
      ro = null
    }
    if (!el) return
    const measure = () => {
      const rect = el.getBoundingClientRect()
      width.value = rect.width
      height.value = rect.height
    }
    measure()
    ro = new ResizeObserver(measure)
    ro.observe(el)
  }

  onBeforeUnmount(() => {
    ro?.disconnect()
    ro = null
  })

  const popperStyle = computed(() => {
    if (width.value <= 0 || height.value <= 0) return undefined
    return {
      width: `${Math.round(width.value)}px`,
      '--trigger-height': `${Math.round(height.value * 100) / 100}px`,
    }
  })

  return { popperStyle, bind }
}
