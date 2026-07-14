import jsPDF from 'jspdf'
import html2canvas from 'html2canvas'

/**
 * High-definition A4 PDF exporter that avoids page-break cutting.
 *
 * @param originalElement The DOM element to export
 * @param filename Output filename
 */
export async function exportToPdf(
  originalElement: HTMLElement,
  filename: string = 'interview-report.pdf',
): Promise<void> {
  // A4 dimensions in points: 595.28 x 841.89
  const pdfWidth = 595.28
  const pdfHeight = 841.89

  // Clone original element so we can modify its layout without affecting the visible page
  const clone = originalElement.cloneNode(true) as HTMLElement
  const surfaceColor = getComputedStyle(document.documentElement)
    .getPropertyValue('--color-surface')
    .trim()

  // Set clone dimensions and move it out of the viewport
  clone.style.position = 'absolute'
  clone.style.left = '-9999px'
  clone.style.top = '0'
  clone.style.width = originalElement.offsetWidth + 'px'
  clone.style.height = 'auto'
  clone.style.backgroundColor = surfaceColor
  clone.classList.add('pdf-export-clone')

  // html2canvas 1.x cannot parse modern `color()` values emitted by Chromium
  // for color-mix(). Override only the detached export clone with equivalent
  // semantic colors so the live UI and theme tokens stay untouched.
  const rootStyle = getComputedStyle(document.documentElement)
  const exportTokens: Record<string, string> = {
    '--color-surface-hover': rootStyle.getPropertyValue('--color-surface').trim(),
    '--color-surface-muted': rootStyle.getPropertyValue('--color-bg').trim(),
    '--color-brand-light': rootStyle.getPropertyValue('--color-surface').trim(),
    '--shadow-whisper': 'none',
    '--shadow-ring': 'none',
    '--shadow-ring-deep': 'none',
  }
  Object.entries(exportTokens).forEach(([name, value]) => clone.style.setProperty(name, value))

  document.body.appendChild(clone)

  let canvas: HTMLCanvasElement
  try {
    normalizeExportColors(clone)

    const elementWidth = originalElement.offsetWidth
    // The target page height in terms of DOM element pixels
    const pageHeight = elementWidth * (pdfHeight / pdfWidth)

    // Selector for elements that must NOT be cut in the middle of a page
    const avoidSelector =
      '.panel, .weakness-item, .report-section__header, .report-score-item, .stage-performance, .question-review, .training-plan__group, .markdown-body h2, .markdown-body h3, .markdown-body p, .markdown-body ul, .markdown-body ol, .markdown-body pre, .markdown-body blockquote'

    let hasChanges = true
    let safetyCounter = 0

    // Keep recalculating offsets and inserting spacers until no avoid-split elements cross page boundaries
    while (hasChanges && safetyCounter < 150) {
      hasChanges = false
      safetyCounter++

      const targets = Array.from(clone.querySelectorAll(avoidSelector)) as HTMLElement[]

      for (const el of targets) {
        // Find element position relative to clone container
        const top = el.offsetTop
        const height = el.offsetHeight

        if (height === 0 || height >= pageHeight) {
          // Skip hidden elements or elements that are taller than a single page
          continue
        }

        const currentPage = Math.floor(top / pageHeight)
        const endPage = Math.floor((top + height - 2) / pageHeight) // 2px margin to handle float precision

        if (currentPage !== endPage) {
          // This element crosses page boundaries! Insert a spacer before it
          const spacerHeight = (currentPage + 1) * pageHeight - top
          const spacer = document.createElement('div')
          spacer.className = 'pdf-page-spacer'
          spacer.style.height = spacerHeight + 'px'
          spacer.style.width = '100%'
          spacer.style.pointerEvents = 'none'

          el.parentNode?.insertBefore(spacer, el)
          hasChanges = true
          break // Break the loop so we can measure elements with the updated offset tops
        }
      }
    }

    // Generate high resolution canvas using scale: 2
    canvas = await html2canvas(clone, {
      scale: 2,
      useCORS: true,
      logging: false,
      allowTaint: true,
      backgroundColor: surfaceColor,
    })
  } finally {
    clone.remove()
  }

  // Optimize image smoothness
  const ctx = canvas.getContext('2d')
  if (ctx) {
    ctx.imageSmoothingEnabled = true
    ctx.imageSmoothingQuality = 'high'
  }

  const canvasWidth = canvas.width
  const canvasHeight = canvas.height

  // Calculate page height in canvas scale
  const pageHeightInCanvasPx = Math.floor(canvasWidth * (pdfHeight / pdfWidth))

  const pdf = new jsPDF('p', 'pt', 'a4')
  let renderedHeight = 0
  let isFirstPage = true

  // Slice the canvas vertically and insert into PDF
  while (renderedHeight < canvasHeight) {
    const remainingHeight = canvasHeight - renderedHeight
    const sliceHeight = Math.min(pageHeightInCanvasPx, remainingHeight)

    const pageCanvas = document.createElement('canvas')
    pageCanvas.width = canvasWidth
    pageCanvas.height = sliceHeight

    const pageCtx = pageCanvas.getContext('2d')
    if (pageCtx) {
      pageCtx.imageSmoothingEnabled = true
      pageCtx.imageSmoothingQuality = 'high'
      pageCtx.drawImage(
        canvas,
        0,
        renderedHeight,
        canvasWidth,
        sliceHeight,
        0,
        0,
        canvasWidth,
        sliceHeight,
      )
    }

    if (!isFirstPage) {
      pdf.addPage()
    } else {
      isFirstPage = false
    }

    const imgData = pageCanvas.toDataURL('image/jpeg', 0.95)
    const printHeight = (sliceHeight / canvasWidth) * pdfWidth

    pdf.addImage(imgData, 'JPEG', 0, 0, pdfWidth, printHeight)
    renderedHeight += sliceHeight
  }

  pdf.save(filename)
}

const exportColorProperties = [
  'color',
  'background-color',
  'border-top-color',
  'border-right-color',
  'border-bottom-color',
  'border-left-color',
  'outline-color',
  'text-decoration-color',
  'box-shadow',
  'text-shadow',
  'fill',
  'stroke',
] as const

function normalizeExportColors(root: HTMLElement) {
  const elements = [root, ...Array.from(root.querySelectorAll<HTMLElement>('*'))]
  for (const element of elements) {
    const computed = getComputedStyle(element)
    for (const property of exportColorProperties) {
      const value = computed.getPropertyValue(property)
      if (value.includes('color(')) {
        element.style.setProperty(property, modernColorToRgba(value), 'important')
      }
    }
  }
}

function modernColorToRgba(value: string) {
  return value.replace(
    /color\((?:srgb|display-p3)\s+([\d.-]+)\s+([\d.-]+)\s+([\d.-]+)(?:\s*\/\s*([\d.-]+))?\)/g,
    (_match, red: string, green: string, blue: string, alpha?: string) => {
      const channel = (input: string) => Math.round(Math.max(0, Math.min(1, Number(input))) * 255)
      const opacity = alpha == null ? 1 : Math.max(0, Math.min(1, Number(alpha)))
      return `rgb(${channel(red)}, ${channel(green)}, ${channel(blue)}, ${opacity})`
    },
  )
}
