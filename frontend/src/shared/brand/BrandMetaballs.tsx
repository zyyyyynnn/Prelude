import { Metaballs } from '@paper-design/shaders-react'
import { useEffect, useState } from 'react'

const colorNames = [
  '--brand-metaballs-1',
  '--brand-metaballs-2',
  '--brand-metaballs-3',
  '--brand-metaballs-4',
  '--brand-metaballs-5',
]

function cssColor(name: string) {
  return getComputedStyle(document.documentElement).getPropertyValue(name).trim()
}

export function BrandMetaballs({ className = '' }: { className?: string }) {
  const [revision, setRevision] = useState(0)

  useEffect(() => {
    const refresh = () => setRevision((value) => value + 1)
    window.addEventListener('prelude-theme-change', refresh)
    return () => window.removeEventListener('prelude-theme-change', refresh)
  }, [])

  if (revision < 0 || typeof document === 'undefined') return null
  const palette = {
    background: cssColor('--brand-metaballs-bg'),
    colors: colorNames.map(cssColor),
  }
  return (
    <div className={`brand-metaballs ${className}`} aria-hidden="true">
      <Metaballs
        colorBack={palette.background}
        colors={palette.colors}
        count={10}
        scale={1}
        size={1}
        speed={1.7}
        style={{
          width: '100%',
          height: '100%',
          backgroundColor: palette.background,
          borderRadius: 'var(--radius-3xl)',
          boxShadow: 'var(--brand-metaballs-shadow)',
        }}
      />
    </div>
  )
}
