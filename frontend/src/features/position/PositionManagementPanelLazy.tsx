import { lazy } from 'react'

export const PositionManagementPanel = lazy(() =>
  import('./PositionManagementPanel').then((module) => ({
    default: module.PositionManagementPanel,
  })),
)
