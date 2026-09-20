import { ChevronDown, Settings } from 'lucide-react'
import { REASONING_LABELS } from '@/features/settings'
import type { ReasoningLevel } from '@/features/settings'
import {
  DropdownMenu,
  DropdownMenuGroup,
  DropdownMenuItem,
  DropdownMenuRadioGroup,
  DropdownMenuRadioItem,
  DropdownMenuSeparator,
  DropdownMenuSubmenu,
  MenuLabel,
} from '@/shared/ui/menu'
import type { InterviewModelConfig, InterviewModelProvider } from '../types'

export function InterviewModelMenu({
  config,
  providers,
  saving,
  onModelChange,
  onThinkingDepthChange,
  onManage,
}: {
  config: InterviewModelConfig
  providers: InterviewModelProvider[]
  saving: boolean
  onModelChange: (model: string) => void
  onThinkingDepthChange: (level: ReasoningLevel | null) => void
  onManage: () => void
}) {
  const provider = providers.find((item) => item.providerKey === config.provider)
  const models = [
    ...new Set([config.model, ...(provider?.models.map((item) => item.model) ?? [])]),
  ].filter(Boolean)
  const capability =
    provider?.models.find((item) => item.model === config.model) ??
    (config.capability.model === config.model ? config.capability : undefined)
  const reasoningSupported = capability?.reasoning ?? false
  const thinkingValue = config.reasoningLevel
  const ariaThinking = reasoningSupported ? `，思考深度：${REASONING_LABELS[thinkingValue]}` : ''

  return (
    <DropdownMenu
      side="top"
      layout="model"
      trigger={
        <button
          type="button"
          className="prompt-bar-control prompt-bar-control-text ui-action"
          aria-label={`模型：${config.model}${ariaThinking}`}
          disabled={saving}
        >
          <span className="min-w-0 flex-1 truncate text-start">
            {config.model}
            {reasoningSupported ? ` · ${REASONING_LABELS[thinkingValue]}` : ''}
          </span>
          <ChevronDown aria-hidden="true" />
        </button>
      }
    >
      <DropdownMenuGroup>
        <DropdownMenuSubmenu trigger={<MenuLabel label="模型" detail={config.model} submenu />}>
          <DropdownMenuRadioGroup value={config.model} onValueChange={onModelChange}>
            {models.length ? (
              models.map((model) => (
                <DropdownMenuRadioItem key={model} value={model}>
                  {model}
                </DropdownMenuRadioItem>
              ))
            ) : (
              <DropdownMenuItem disabled>请先在模型管理中配置模型</DropdownMenuItem>
            )}
          </DropdownMenuRadioGroup>
        </DropdownMenuSubmenu>
        {reasoningSupported ? (
          <DropdownMenuSubmenu
            trigger={
              <MenuLabel label="思考深度" detail={REASONING_LABELS[thinkingValue]} submenu />
            }
          >
            <DropdownMenuRadioGroup
              value={thinkingValue}
              onValueChange={(value) => onThinkingDepthChange(value as ReasoningLevel)}
            >
              {(capability?.supportedReasoningLevels ?? []).map((level) => (
                <DropdownMenuRadioItem key={level} value={level}>
                  {REASONING_LABELS[level]}
                </DropdownMenuRadioItem>
              ))}
            </DropdownMenuRadioGroup>
          </DropdownMenuSubmenu>
        ) : null}
      </DropdownMenuGroup>
      <DropdownMenuSeparator />
      <DropdownMenuGroup>
        <DropdownMenuItem icon={<Settings />} onClick={onManage}>
          管理模型
        </DropdownMenuItem>
      </DropdownMenuGroup>
    </DropdownMenu>
  )
}
