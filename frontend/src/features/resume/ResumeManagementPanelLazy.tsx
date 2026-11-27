import { lazy } from 'react'

export const ResumeManagementPanel = lazy(() =>
  import('./ResumeManagementPanel').then((module) => ({
    default: module.ResumeManagementPanel,
  })),
)
