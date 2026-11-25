import { REASONING_LABELS } from '@/features/settings'
import type { ReasoningLevel } from '@/features/settings'
import type { InterviewModelConfig, InterviewModelProvider } from '../types'
import { RiSettings3Line } from '@remixicon/react'
import {
  DropdownMenu,
  DropdownMenuGroup,
  DropdownMenuItem,
  DropdownMenuRadioGroup,
  DropdownMenuRadioItem,
  DropdownMenuSeparator,
  DropdownMenuSubmenu,
  Icon,
  MenuLabel,
  PromptBarModelTrigger,
} from '@/shared/ui'

export function InterviewModelMenu({
  config,
  providers,
  onModelChange,
  onThinkingDepthChange,
  onManage,
}: {
  config: InterviewModelConfig
  providers: InterviewModelProvider[]
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
  const thinkingLabel = reasoningSupported ? REASONING_LABELS[thinkingValue] : null
  const triggerText = `${config.model}${thinkingLabel ? ` · ${thinkingLabel}` : ''}`

  return (
    <DropdownMenu
      side="top"
      layout="model"
      trigger={<PromptBarModelTrigger text={triggerText} aria-label={`模型：${triggerText}`} />}
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
        {capability?.supportedReasoningLevels?.length ? (
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
        <DropdownMenuItem icon={<Icon as={RiSettings3Line} />} onClick={onManage}>
          管理模型
        </DropdownMenuItem>
      </DropdownMenuGroup>
    </DropdownMenu>
  )
}
