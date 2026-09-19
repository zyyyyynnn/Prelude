import { Metaballs } from '@paper-design/shaders-react'
import { useEffect, useState } from 'react'
import { cn } from '@/shared/lib/cn'

const colorNames = [
  '--brand-metaballs-1',
  '--brand-metaballs-2',
  '--brand-metaballs-3',
  '--brand-metaballs-4',
  '--brand-metaballs-5',
]

function readPalette() {
  const style = getComputedStyle(document.documentElement)
  return {
    background: style.getPropertyValue('--brand-metaballs-bg').trim(),
    colors: colorNames.map((name) => style.getPropertyValue(name).trim()),
  }
}

export function BrandMetaballs({ className = '' }: { className?: string }) {
  const [palette, setPalette] = useState(readPalette)
  const [still, setStill] = useState(false)

  useEffect(() => {
    const reducedMotion = window.matchMedia('(prefers-reduced-motion: reduce)')
    const syncMotion = () => setStill(reducedMotion.matches)
    const refresh = () => setPalette(readPalette())
    syncMotion()
    window.addEventListener('prelude-theme-change', refresh)
    reducedMotion.addEventListener('change', syncMotion)
    return () => {
      window.removeEventListener('prelude-theme-change', refresh)
      reducedMotion.removeEventListener('change', syncMotion)
    }
  }, [])

  // A zero speed stops the shader loop, so reduced-motion users get a still frame
  // instead of a surface that animates for the whole session.
  const speed = still ? 0 : 1.7

  return (
    <div className={cn('brand-metaballs', className)} aria-hidden="true">
      <Metaballs
        colorBack={palette.background}
        colors={palette.colors}
        count={10}
        scale={1}
        size={1}
        speed={speed}
        className="brand-metaballs__shader"
      />
    </div>
  )
}
