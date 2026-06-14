import { z } from 'zod'

export const llmSettingsSchema = z.object({
  providerKey: z.string().min(1, '请选择 Provider'),
  baseUrl: z.string().optional(),
  model: z.string().min(1, '请选择模型'),
  apiKey: z.string().optional(),
  maxTokens: z.union([z.string(), z.number()]).optional(),
  thinkingDepth: z.string().optional(),
})
