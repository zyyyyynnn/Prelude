import { apiRequest } from '@/shared/api/client'
import type { CreatePositionPayload, Position } from './types'

export const fetchPositions = () => apiRequest<Position[]>('/position/list')

export const createPosition = (payload: CreatePositionPayload) =>
  apiRequest<Position>('/position', {
    method: 'POST',
    body: JSON.stringify(payload),
  })

export const updatePosition = (id: number, payload: CreatePositionPayload) =>
  apiRequest<Position>(`/position/${id}`, {
    method: 'PUT',
    body: JSON.stringify(payload),
  })

export const deletePosition = (id: number) =>
  apiRequest<void>(`/position/${id}`, { method: 'DELETE' })

export { PositionRow } from './PositionRow'
export type { CreatePositionPayload, Position } from './types'
