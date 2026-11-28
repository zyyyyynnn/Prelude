import fs from 'node:fs'
import { pathToFileURL } from 'node:url'

export function normalizeSbom(source) {
  const bom = JSON.parse(source)
  delete bom.serialNumber
  if (bom.metadata) delete bom.metadata.timestamp
  return `${JSON.stringify(bom, null, 2)}\n`
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  for (const file of process.argv.slice(2)) {
    fs.writeFileSync(file, normalizeSbom(fs.readFileSync(file, 'utf8')))
  }
}
