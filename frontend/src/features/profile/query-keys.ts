export const profileQueryKeys = {
  detail: (accountId: number) => ['profile', accountId] as const,
}
