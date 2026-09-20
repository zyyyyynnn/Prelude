export type AnalyticsRadarResponse = {
  technical: number
  expression: number
  logic: number
  sessionCount: number
}

export type AnalyticsTrendPoint = {
  sessionId: number
  createdAt: string
  technical: number
  expression: number
  logic: number
}

export type AnalyticsWeaknessItem = {
  category: string
  count: number
  descriptions: string[]
}
