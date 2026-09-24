import { Metaballs } from '@paper-design/shaders-react'
import { useEffect, useRef, useState } from 'react'
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
  const root = useRef<HTMLDivElement>(null)

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

  /* The shader writes pixel width/height onto its canvas on resize. This component owns
     the box, so those inline lengths are dropped the moment they appear. */
  useEffect(() => {
    const host = root.current
    if (!host) return
    const syncCanvasBox = () => {
      for (const canvas of host.querySelectorAll('canvas')) {
        canvas.style.removeProperty('width')
        canvas.style.removeProperty('height')
      }
    }
    syncCanvasBox()
    const observer = new ResizeObserver(syncCanvasBox)
    observer.observe(host)
    return () => observer.disconnect()
  }, [])

  // A zero speed stops the shader loop, so reduced-motion users get a still frame
  // instead of a surface that animates for the whole session.
  const speed = still ? 0 : 1.7

  return (
    <div ref={root} className={cn('brand-metaballs', className)} aria-hidden="true">
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
