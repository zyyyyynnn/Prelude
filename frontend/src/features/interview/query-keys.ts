export const interviewQueryKeys = {
  sessions: () => ['interview-sessions'] as const,
  session: (sessionId: number) => ['interview-session', sessionId] as const,
}
