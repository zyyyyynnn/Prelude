import { useState, type ReactNode } from 'react'
import { SettingsModal } from './SettingsModal'
import {
  SettingsContext,
  type SettingsOpenRequest,
  type SettingsRequest,
} from './settings-context'

export function SettingsProvider({ children }: { children: ReactNode }) {
  const [open, setOpen] = useState(false)
  const [request, setRequest] = useState<SettingsRequest>({ section: 'profile', requestId: 0 })

  function openSettings(next: SettingsOpenRequest = {}) {
    setRequest({
      section: next.section ?? 'profile',
      providerKey: next.providerKey,
      intent: next.intent,
      requestId: Date.now(),
    })
    setOpen(true)
  }

  return (
    <SettingsContext.Provider value={{ openSettings }}>
      {children}
      <SettingsModal
        open={open}
        section={request.section}
        providerKey={request.providerKey}
        intent={request.intent}
        requestId={request.requestId}
        onSectionChange={(section) =>
          setRequest((current) => ({ ...current, section, intent: undefined }))
        }
        onOpenChange={setOpen}
      />
    </SettingsContext.Provider>
  )
}
