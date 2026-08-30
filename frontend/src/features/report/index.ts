export { ReportPanel } from './ReportPanel'

export async function printInterviewReport(title = '面试训练报告') {
  const { printReport } = await import('./print-report')
  return printReport(title)
}
