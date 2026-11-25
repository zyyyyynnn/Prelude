import { useEffect, useState } from 'react'
import { observeAvatarDecode, type AvatarDecodeStatus } from './avatar-decode'

export function ProfileAvatar({
  src,
  fallback,
  alt = '当前头像',
}: {
  src?: string | null
  fallback: string
  alt?: string
}) {
  return (
    <div
      data-slot="profile-avatar"
      className="grid size-(--layout-avatar-inline-size) place-items-center overflow-hidden rounded-full bg-surface-muted font-serif text-lg font-semibold text-brand elevated-ring"
    >
      {src ? (
        <DecodedAvatar key={src} src={src} fallback={fallback} alt={alt} />
      ) : (
        <Fallback value={fallback} alt={alt} />
      )}
    </div>
  )
}

function DecodedAvatar({ src, fallback, alt }: { src: string; fallback: string; alt: string }) {
  const [status, setStatus] = useState<AvatarDecodeStatus | 'loading'>('loading')

  useEffect(() => observeAvatarDecode(src, decodeImage, setStatus), [src])

  if (status === 'error') return <Fallback value={fallback} alt={alt} />
  if (status === 'loading') return <Fallback value={fallback} alt={alt} />
  return <img className="size-full object-cover" src={src} alt={alt} />
}

function Fallback({ value, alt }: { value: string; alt: string }) {
  return (
    <span role="img" aria-label={alt}>
      {value}
    </span>
  )
}

function decodeImage(src: string): Promise<void> {
  const image = new Image()
  image.src = src
  return image.decode()
}
