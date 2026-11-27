import { describe, expect, it } from 'vite-plus/test'
import { llmQueryKeys } from './query-keys'

describe('llmQueryKeys', () => {
  it('isolates account configuration while sharing provider discovery', () => {
    expect(llmQueryKeys.config(1)).not.toEqual(llmQueryKeys.config(2))
  })
})
