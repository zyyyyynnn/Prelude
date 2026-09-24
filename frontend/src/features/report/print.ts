const printingClass = 'is-printing-report'

async function printReport(title: string) {
  const previousTitle = document.title
  document.title = title
  document.body.classList.add(printingClass)

  try {
    await new Promise<void>((resolve) => requestAnimationFrame(() => resolve()))
    window.print()
  } finally {
    document.body.classList.remove(printingClass)
    document.title = previousTitle
  }
}

export async function printInterviewReport(title = '面试训练报告') {
  return printReport(title)
}
