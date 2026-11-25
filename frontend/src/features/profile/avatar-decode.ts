export type AvatarDecodeStatus = 'ready' | 'error'

export function observeAvatarDecode(
  src: string,
  decode: (source: string) => Promise<void>,
  onSettled: (status: AvatarDecodeStatus) => void,
): () => void {
  let active = true
  void decode(src).then(
    () => {
      if (active) onSettled('ready')
    },
    () => {
      if (active) onSettled('error')
    },
  )
  return () => {
    active = false
  }
}
