import html2canvas from 'html2canvas'
import { jsPDF } from 'jspdf'

const pdfWidth = 595.28
const pdfHeight = 841.89
const avoidSplit =
  '.panel, .weakness-item, .report-section__header, .report-score-item, .stage-performance, .question-review, .training-plan__group, .markdown-body h2, .markdown-body h3, .markdown-body p, .markdown-body ul, .markdown-body ol, .markdown-body pre, .markdown-body blockquote'
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

export async function exportToPdf(original: HTMLElement, filename: string) {
  const rootStyle = getComputedStyle(document.documentElement)
  const surface = rootStyle.getPropertyValue('--color-surface').trim()
  const clone = original.cloneNode(true) as HTMLElement
  Object.assign(clone.style, {
    position: 'absolute',
    insetInlineStart: '-9999px',
    insetBlockStart: '0',
    inlineSize: `${original.offsetWidth}px`,
    blockSize: 'auto',
    backgroundColor: surface,
  })
  clone.classList.add('pdf-export-clone')
  clone.style.setProperty('--color-surface-hover', surface)
  clone.style.setProperty('--color-surface-muted', rootStyle.getPropertyValue('--color-bg').trim())
  clone.style.setProperty('--color-brand-light', surface)
  clone.style.setProperty('--shadow-whisper', 'none')
  clone.style.setProperty('--shadow-ring', 'none')
  clone.style.setProperty('--shadow-ring-deep', 'none')
  document.body.appendChild(clone)

  let canvas: HTMLCanvasElement
  try {
    normalizeExportColors(clone)
    insertPageSpacers(clone, original.offsetWidth * (pdfHeight / pdfWidth))
    canvas = await html2canvas(clone, {
      scale: 2,
      useCORS: true,
      logging: false,
      allowTaint: true,
      backgroundColor: surface,
    })
  } finally {
    clone.remove()
  }

  const context = canvas.getContext('2d')
  if (context) {
    context.imageSmoothingEnabled = true
    context.imageSmoothingQuality = 'high'
  }
  const pageHeight = Math.floor(canvas.width * (pdfHeight / pdfWidth))
  const pdf = new jsPDF('p', 'pt', 'a4')
  for (let offset = 0, page = 0; offset < canvas.height; offset += pageHeight, page += 1) {
    const height = Math.min(pageHeight, canvas.height - offset)
    const slice = document.createElement('canvas')
    slice.width = canvas.width
    slice.height = height
    const sliceContext = slice.getContext('2d')
    if (sliceContext) {
      sliceContext.imageSmoothingEnabled = true
      sliceContext.imageSmoothingQuality = 'high'
      sliceContext.drawImage(canvas, 0, offset, canvas.width, height, 0, 0, canvas.width, height)
    }
    if (page) pdf.addPage()
    pdf.addImage(
      slice.toDataURL('image/jpeg', 0.98),
      'JPEG',
      0,
      0,
      pdfWidth,
      (height / canvas.width) * pdfWidth,
    )
  }
  pdf.save(filename)
}

function insertPageSpacers(root: HTMLElement, pageHeight: number) {
  for (let pass = 0; pass < 150; pass += 1) {
    const crossing = Array.from(root.querySelectorAll<HTMLElement>(avoidSplit)).find((element) => {
      const height = element.offsetHeight
      return (
        height > 0 &&
        height < pageHeight &&
        Math.floor(element.offsetTop / pageHeight) !==
          Math.floor((element.offsetTop + height - 2) / pageHeight)
      )
    })
    if (!crossing) return
    const spacer = document.createElement('div')
    spacer.className = 'pdf-page-spacer'
    spacer.style.blockSize = `${(Math.floor(crossing.offsetTop / pageHeight) + 1) * pageHeight - crossing.offsetTop}px`
    spacer.style.inlineSize = '100%'
    spacer.style.pointerEvents = 'none'
    crossing.parentNode?.insertBefore(spacer, crossing)
  }
}

function normalizeExportColors(root: HTMLElement) {
  for (const element of [root, ...Array.from(root.querySelectorAll<HTMLElement>('*'))]) {
    const computed = getComputedStyle(element)
    for (const property of exportColorProperties) {
      const value = computed.getPropertyValue(property)
      if (value.includes('color('))
        element.style.setProperty(property, normalizeModernColor(value), 'important')
    }
  }
}

function normalizeModernColor(value: string) {
  const colorFunction = 'rgb'
  return value.replace(
    /color\((?:srgb|display-p3)\s+([\d.-]+)\s+([\d.-]+)\s+([\d.-]+)(?:\s*\/\s*([\d.-]+))?\)/g,
    (_match, red: string, green: string, blue: string, alpha?: string) => {
      const channel = (input: string) => Math.round(Math.max(0, Math.min(1, Number(input))) * 255)
      const opacity = alpha == null ? 1 : Math.max(0, Math.min(1, Number(alpha)))
      return `${colorFunction}(${channel(red)}, ${channel(green)}, ${channel(blue)}, ${opacity})`
    },
  )
}
