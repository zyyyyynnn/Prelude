export function getErrorMessage(error: unknown): string {
  return error instanceof Error ? error.message : '请求失败'
}
