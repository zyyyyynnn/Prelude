export { default as StructuredReportPanel } from './components/StructuredReportPanel.vue'
export { renderMarkdown } from './lib/markdown'
export { parseInterviewReport } from './lib/parseInterviewReport'
export type { ParsedInterviewReport, StructuredInterviewReport } from './model/types'

export async function exportInterviewReportToPdf(element: HTMLElement, filename?: string) {
  const { exportToPdf } = await import('./lib/pdf')
  return exportToPdf(element, filename)
}
