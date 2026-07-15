import { http, unwrapResult, type ApiResult } from '@/shared/api'
import type { PositionTemplate } from '../model/types'

export async function fetchPositions() {
  const response = await http.get<ApiResult<PositionTemplate[]>>('/position/list')
  return unwrapResult(response.data)
}
