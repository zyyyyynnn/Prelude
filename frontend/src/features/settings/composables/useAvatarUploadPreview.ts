import { onBeforeUnmount, ref } from 'vue'

export type AvatarPreviewSelection = {
  url: string
  generation: number
}

export function useAvatarUploadPreview() {
  const previewUrl = ref<string | null>(null)
  let generation = 0

  function revokeCurrent() {
    if (previewUrl.value) {
      URL.revokeObjectURL(previewUrl.value)
      previewUrl.value = null
    }
  }

  async function prepare(file: File): Promise<AvatarPreviewSelection | null> {
    const token = ++generation
    revokeCurrent()
    const url = URL.createObjectURL(file)
    const image = new Image()

    try {
      await new Promise<void>((resolve, reject) => {
        image.onload = () => resolve()
        image.onerror = () => reject(new Error('头像预览无法读取'))
        image.src = url
      })
      if (typeof image.decode === 'function') {
        try {
          await image.decode()
        } catch {
          throw new Error('头像预览无法解码')
        }
      }
    } catch (error) {
      URL.revokeObjectURL(url)
      throw error
    }

    if (token !== generation) {
      URL.revokeObjectURL(url)
      return null
    }
    previewUrl.value = url
    return { url, generation: token }
  }

  function isCurrent(selection: AvatarPreviewSelection) {
    return selection.generation === generation && previewUrl.value === selection.url
  }

  function clear() {
    generation++
    revokeCurrent()
  }

  onBeforeUnmount(clear)

  return { previewUrl, prepare, isCurrent, clear }
}
