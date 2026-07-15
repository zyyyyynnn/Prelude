import { http, unwrapResult, type ApiResult } from '@/shared/api'
import type {
  AnalyticsRadarResponse,
  AnalyticsTrendPoint,
  AnalyticsWeaknessItem,
} from '../model/types'

export async function fetchRadarAnalytics() {
  const response = await http.get<ApiResult<AnalyticsRadarResponse>>('/analytics/radar')
  return unwrapResult(response.data)
}

export async function fetchTrendAnalytics() {
  const response = await http.get<ApiResult<AnalyticsTrendPoint[]>>('/analytics/trend')
  return unwrapResult(response.data)
}

export async function fetchWeaknessAnalytics() {
  const response = await http.get<ApiResult<AnalyticsWeaknessItem[]>>('/analytics/weaknesses')
  return unwrapResult(response.data)
}
