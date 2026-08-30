import { apiRequest } from '@/shared/api/client'
import type { CreatePositionPayload, PositionTemplate } from './types'

export const fetchPositions = () => apiRequest<PositionTemplate[]>('/position/list')

export const createPosition = (payload: CreatePositionPayload) =>
  apiRequest<PositionTemplate>('/position', {
    method: 'POST',
    body: JSON.stringify(payload),
  })

export const updatePosition = (id: number, payload: CreatePositionPayload) =>
  apiRequest<PositionTemplate>(`/position/${id}`, {
    method: 'PUT',
    body: JSON.stringify(payload),
  })

export const deletePosition = (id: number) =>
  apiRequest<void>(`/position/${id}`, { method: 'DELETE' })
