import { http } from './http'
import type {
  AnalyticsRadarResponse,
  AnalyticsTrendPoint,
  AnalyticsWeaknessItem,
  ApiResult,
} from './contracts'
import { unwrapResult } from './contracts'

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
