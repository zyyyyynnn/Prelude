export type ProfileWritePhase = 'idle' | 'writing' | 'revision-refresh'

export type ProfileWriteContext = {
  accountId: number
  generation: number
  sequence: number
}

export type ProfileWriteState = {
  accountId: number | null
  generation: number
  mounted: boolean
  phase: ProfileWritePhase
  sequence: number
  activeSequence: number | null
}

export function createProfileWriteState(): ProfileWriteState {
  return {
    accountId: null,
    generation: 0,
    mounted: false,
    phase: 'idle',
    sequence: 0,
    activeSequence: null,
  }
}

export function activateProfileAccount(
  state: ProfileWriteState,
  accountId: number | null,
): ProfileWriteState {
  const changed = state.accountId !== accountId
  return {
    ...state,
    accountId,
    generation: changed ? state.generation + 1 : state.generation,
    mounted: true,
    phase: 'idle',
    activeSequence: null,
  }
}

export function deactivateProfileAccount(state: ProfileWriteState): ProfileWriteState {
  return { ...state, mounted: false, phase: 'idle', activeSequence: null }
}

export function beginProfileWrite(
  state: ProfileWriteState,
): { state: ProfileWriteState; context: ProfileWriteContext } | null {
  if (!state.mounted || state.accountId == null || state.phase !== 'idle') return null
  const sequence = state.sequence + 1
  return {
    state: { ...state, sequence, phase: 'writing', activeSequence: sequence },
    context: { accountId: state.accountId, generation: state.generation, sequence },
  }
}

export function isCurrentProfileWrite(
  state: ProfileWriteState,
  context: ProfileWriteContext | undefined,
): context is ProfileWriteContext {
  return Boolean(
    context &&
    state.mounted &&
    context.accountId === state.accountId &&
    context.generation === state.generation &&
    context.sequence === state.sequence &&
    context.sequence === state.activeSequence,
  )
}

export function beginProfileRevisionRefresh(
  state: ProfileWriteState,
  context: ProfileWriteContext,
): ProfileWriteState {
  if (!isCurrentProfileWrite(state, context)) return state
  return { ...state, phase: 'revision-refresh' }
}

export function settleProfileRevisionRefresh(
  state: ProfileWriteState,
  context: ProfileWriteContext,
): ProfileWriteState {
  if (!isCurrentProfileWrite(state, context) || state.phase !== 'revision-refresh') return state
  return { ...state, phase: 'idle', activeSequence: null }
}

export function settleProfileWrite(
  state: ProfileWriteState,
  context: ProfileWriteContext | undefined,
): ProfileWriteState {
  if (!isCurrentProfileWrite(state, context) || state.phase !== 'writing') return state
  return { ...state, phase: 'idle', activeSequence: null }
}
