/**
 * The demo harness is a second implementation of server policy. Its Chinese copy must
 * stay aligned with the backend literals it mirrors, or a smoke test can pass against
 * wording the product would never show.
 */
const fs = require('node:fs')
const path = require('node:path')

const root = path.resolve(__dirname, '..', '..')
const harness = fs.readFileSync(path.join(root, 'frontend', 'tests', 'demo-harness.ts'), 'utf8')
const backendSources = [
  'backend/src/main/java/com/prelude/position/application/PositionServiceImpl.java',
  'backend/src/main/java/com/prelude/position/api/CreatePositionRequest.java',
  'backend/src/main/java/com/prelude/resume/application/ImportResumePdf.java',
  'backend/src/main/java/com/prelude/resume/application/DeleteResume.java',
  'backend/src/main/java/com/prelude/documents/DocumentExtractorImpl.java',
  'backend/src/main/java/com/prelude/identity/application/ProfileService.java',
  'backend/src/main/java/com/prelude/identity/application/AvatarPublication.java',
]
  .map((relative) => fs.readFileSync(path.join(root, relative), 'utf8'))
  .join('\n')

const mirrored = [
  '同名岗位已存在',
  '岗位不存在或不可编辑',
  '岗位名称不能为空',
  '面试侧重点不能为空',
  '仅支持 PDF 文件',
  'PDF 文本提取失败，请检查文件格式',
  '简历不存在或无权访问',
  '资料已被其他操作更新，请刷新后重试',
]

const violations = []
for (const copy of mirrored) {
  if (!harness.includes(copy)) violations.push(`demo-harness.ts: missing mirrored copy "${copy}"`)
  if (!backendSources.includes(copy))
    violations.push(`backend: missing mirrored copy "${copy}" — update the harness with it`)
}

if (violations.length) {
  console.error(`Demo copy verification: FAIL (${violations.length})`)
  for (const violation of violations) console.error(`  ${violation}`)
  process.exit(1)
}
console.log(`Demo copy verification: PASS (${mirrored.length} mirrored strings)`)
