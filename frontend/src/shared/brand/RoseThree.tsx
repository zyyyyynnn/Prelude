import { useEffect, useRef } from 'react'

const config = {
  particleCount: 76,
  trailSpan: 0.31,
  durationMs: 5300,
  rotationDurationMs: 28000,
  pulseDurationMs: 4400,
  strokeWidth: 4.6,
  roseA: 9.2,
  roseABoost: 0.6,
  roseBreathBase: 0.72,
  roseBreathBoost: 0.28,
  roseScale: 3.25,
}

export function RoseThree({ className, speedMultiplier = 1 }: { className?: string; speedMultiplier?: number }) {
  const groupRef = useRef<SVGGElement>(null)
  const pathRef = useRef<SVGPathElement>(null)
  const particleRefs = useRef<Array<SVGCircleElement | null>>([])

  useEffect(() => {
    const group = groupRef.current
    const path = pathRef.current
    if (!group || !path) return
    path.setAttribute('stroke-width', String(config.strokeWidth))

    const media = window.matchMedia('(prefers-reduced-motion: reduce)')
    let frame = 0
    let startedAt: number | null = null
    const render = (now: number) => {
      if (startedAt == null) startedAt = now
      const time = now - startedAt
      const detailScale = media.matches ? 0.72 : getDetailScale(time, speedMultiplier)
      const progress = media.matches
        ? 0
        : (time % (config.durationMs / speedMultiplier)) / (config.durationMs / speedMultiplier)
      const rotation = media.matches ? 0 : getRotation(time, speedMultiplier)
      group.setAttribute('transform', `rotate(${rotation.toFixed(1)} 50 50)`)
      path.setAttribute('d', buildPath(detailScale))
      particleRefs.current.forEach((node, index) => {
        if (!node) return
        const particle = getParticle(index, progress, detailScale)
        node.setAttribute('cx', particle.x.toFixed(2))
        node.setAttribute('cy', particle.y.toFixed(2))
        node.setAttribute('r', particle.radius.toFixed(2))
        node.setAttribute('opacity', particle.opacity.toFixed(3))
      })
      if (!media.matches) frame = requestAnimationFrame(render)
    }

    frame = requestAnimationFrame(render)
    return () => cancelAnimationFrame(frame)
  }, [speedMultiplier])

  return (
    <svg
      className={`rose-three-loader${className ? ` ${className}` : ''}`}
      viewBox="0 0 100 100"
      fill="none"
      aria-hidden="true"
    >
      <g ref={groupRef}>
        <path
          ref={pathRef}
          stroke="currentColor"
          strokeLinecap="round"
          strokeLinejoin="round"
          opacity="0.1"
        />
        {Array.from({ length: config.particleCount }, (_, index) => (
          <circle
            key={index}
            ref={(node) => {
              particleRefs.current[index] = node
            }}
            fill="currentColor"
          />
        ))}
      </g>
    </svg>
  )
}

function point(progress: number, detailScale: number) {
  const t = progress * Math.PI * 2
  const a = config.roseA + detailScale * config.roseABoost
  const radius =
    a * (config.roseBreathBase + detailScale * config.roseBreathBoost) * Math.cos(3 * t)
  return {
    x: 50 + Math.cos(t) * radius * config.roseScale,
    y: 50 + Math.sin(t) * radius * config.roseScale,
  }
}

function normalizeProgress(progress: number) {
  return ((progress % 1) + 1) % 1
}

function getDetailScale(time: number, speedMultiplier: number) {
  const adjustedDuration = config.pulseDurationMs / speedMultiplier
  const pulseProgress = (time % adjustedDuration) / adjustedDuration
  return 0.52 + ((Math.sin(pulseProgress * Math.PI * 2 + 0.55) + 1) / 2) * 0.48
}

function getRotation(time: number, speedMultiplier: number) {
  const adjustedDuration = config.rotationDurationMs / speedMultiplier
  return -((time % adjustedDuration) / adjustedDuration) * 360
}

function buildPath(detailScale: number, steps = 240) {
  let path = ''
  for (let index = 0; index <= steps; index += 1) {
    const current = point(index / steps, detailScale)
    path += `${index === 0 ? 'M' : 'L'} ${current.x.toFixed(2)} ${current.y.toFixed(2)} `
  }
  return path
}

function getParticle(index: number, progress: number, detailScale: number) {
  const tailOffset = index / (config.particleCount - 1)
  const current = point(normalizeProgress(progress - tailOffset * config.trailSpan), detailScale)
  const fade = Math.pow(1 - tailOffset, 0.56)
  return {
    x: current.x,
    y: current.y,
    radius: 0.9 + fade * 2.7,
    opacity: 0.04 + fade * 0.96,
  }
}
