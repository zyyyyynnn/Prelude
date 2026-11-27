export const llmQueryKeys = {
  config: (accountId: number) => ['llm-config', accountId] as const,
  providers: () => ['llm-providers'] as const,
}
