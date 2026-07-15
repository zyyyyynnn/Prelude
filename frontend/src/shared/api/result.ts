export type ApiResult<T> = {
  code: number
  message: string
  data: T
}

export function unwrapResult<T>(result: ApiResult<T>): T {
  if (result.code !== 200) {
    throw new Error(result.message || '请求失败')
  }

  return result.data
}
