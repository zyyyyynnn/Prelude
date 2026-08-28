import { apiRequest } from '@/shared/api/client'
import type { AnalyticsRadarResponse, AnalyticsTrendPoint, AnalyticsWeaknessItem } from './types'

export const fetchRadar = () => apiRequest<AnalyticsRadarResponse>('/analytics/radar')
export const fetchTrend = () => apiRequest<AnalyticsTrendPoint[]>('/analytics/trend')
export const fetchWeaknesses = () => apiRequest<AnalyticsWeaknessItem[]>('/analytics/weaknesses')
