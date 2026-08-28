export type PositionTemplate = {
  id: number
  name: string
  systemPrompt?: string
  editable?: boolean
}

export type CreatePositionPayload = {
  name: string
  systemPrompt: string
}
