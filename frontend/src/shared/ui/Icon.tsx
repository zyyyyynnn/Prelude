import type { ComponentType, SVGProps } from 'react'
import { cn } from '@/shared/lib/cn'

/**
 * The only place call sites take a glyph. Sizes follow `--ui-glyph-*`; colour
 * inherits so an icon never invents its own palette. Glyphs are imported from
 * RemixIcon freely, but a glyph is rendered through this component and never as
 * a JSX element of its own — `verify:architecture` rejects a bare `<Ri… />`.
 */
export type IconGlyph = ComponentType<Record<string, unknown>>

export type IconProps = SVGProps<SVGSVGElement> & {
  as: IconGlyph
  size?: 'sm' | 'md' | 'lg'
}

const GLYPH_CLASS = {
  sm: 'size-(--ui-glyph-sm)',
  md: 'size-(--ui-glyph-md)',
  lg: 'size-(--ui-glyph-lg)',
} as const

export function Icon({ as: Glyph, size = 'md', className, ...rest }: IconProps) {
  return (
    <Glyph
      aria-hidden="true"
      className={cn('shrink-0', GLYPH_CLASS[size], className)}
      size="1em"
      {...rest}
    />
  )
}
