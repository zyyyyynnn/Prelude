import { http, unwrapResult, type ApiResult } from '@/shared/api'
import type {
  AnalyticsRadarResponse,
  AnalyticsTrendPoint,
  AnalyticsWeaknessItem,
} from '../model/types'

export async function fetchRadarAnalytics(signal?: AbortSignal) {
  const response = await http.get<ApiResult<AnalyticsRadarResponse>>('/analytics/radar', { signal })
  return unwrapResult(response.data)
}

export async function fetchTrendAnalytics(signal?: AbortSignal) {
  const response = await http.get<ApiResult<AnalyticsTrendPoint[]>>('/analytics/trend', { signal })
  return unwrapResult(response.data)
}

export async function fetchWeaknessAnalytics(signal?: AbortSignal) {
  const response = await http.get<ApiResult<AnalyticsWeaknessItem[]>>('/analytics/weaknesses', {
    signal,
  })
  return unwrapResult(response.data)
}
