import { http, unwrapResult, type ApiResult } from '@/shared/api'
import type { PositionTemplate } from '../model/types'

export async function fetchPositions(signal?: AbortSignal) {
  const response = await http.get<ApiResult<PositionTemplate[]>>('/position/list', { signal })
  return unwrapResult(response.data)
}
