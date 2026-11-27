import { lazy } from 'react'

export const ProfilePanel = lazy(() =>
  import('./ProfilePanel').then((module) => ({ default: module.ProfilePanel })),
)
