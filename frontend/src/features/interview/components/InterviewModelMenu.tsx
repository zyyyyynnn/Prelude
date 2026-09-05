import {
  ChevronDown,
  ChevronRight,
  Settings,
} from 'lucide-react'
import { REASONING_LABELS, type LlmConfigResponse, type LlmProviderResponse, type ReasoningLevel } from '@/features/settings'
import {
  DropdownMenu,
  DropdownMenuGroup,
  DropdownMenuItem,
  DropdownMenuRadioGroup,
  DropdownMenuRadioItem,
  DropdownMenuSeparator,
  DropdownMenuSubmenu,
} from '@/shared/ui'

function MenuRow({
  label,
  value,
  submenu,
}: {
  label: string
  value?: string
  submenu?: boolean
}) {
  return (
    <>
      <span className="prelude-menu__label">{label}</span>
      {value && <span className="prelude-menu__detail">{value}</span>}
      {submenu && <ChevronRight className="prelude-menu__chevron" aria-hidden="true" />}
    </>
  )
}

export function InterviewModelMenu({
  config,
  providers,
  saving,
  onModelChange,
  onThinkingDepthChange,
  onManage,
}: {
  config: LlmConfigResponse
  providers: LlmProviderResponse[]
  saving: boolean
  onModelChange: (model: string) => void
  onThinkingDepthChange: (level: ReasoningLevel | null) => void
  onManage: () => void
}) {
  const provider = providers.find((item) => item.providerKey === config.provider)
  const models = [...new Set([config.model, ...(provider?.models.map((item) => item.model) ?? [])])].filter(Boolean)
  const capability = provider?.models.find((item) => item.model === config.model)
    ?? (config.capability.model === config.model ? config.capability : undefined)
  const reasoningSupported = capability?.reasoning ?? false
  const thinkingValue = config.reasoningLevel
  const ariaThinking = reasoningSupported ? `，思考深度：${REASONING_LABELS[thinkingValue]}` : ''

  return (
    <DropdownMenu
      side="top"
      className="prelude-menu--structured prelude-menu--model"
      trigger={
        <button
          type="button"
          className="prompt-bar__control prompt-bar__model ui-action"
          aria-label={`模型：${config.model}${ariaThinking}`}
          disabled={saving}
        >
          <span className="prompt-bar__control-label">
            {config.model}
            {reasoningSupported ? ` · ${REASONING_LABELS[thinkingValue]}` : ''}
          </span>
          <ChevronDown aria-hidden="true" />
        </button>
      }
    >
      <DropdownMenuGroup>
        <DropdownMenuSubmenu
          trigger={<MenuRow label="模型" value={config.model} submenu />}
        >
          <DropdownMenuRadioGroup value={config.model} onValueChange={onModelChange}>
            {models.length ? (
              models.map((model) => (
                <DropdownMenuRadioItem key={model} value={model}>
                  <span className="prelude-menu__item-label">{model}</span>
                </DropdownMenuRadioItem>
              ))
            ) : (
              <DropdownMenuItem disabled>请先在模型管理中配置模型</DropdownMenuItem>
            )}
          </DropdownMenuRadioGroup>
        </DropdownMenuSubmenu>
        {reasoningSupported ? (
          <DropdownMenuSubmenu
            trigger={<MenuRow label="思考深度" value={REASONING_LABELS[thinkingValue]} submenu />}
          >
            <DropdownMenuRadioGroup
              value={thinkingValue}
              onValueChange={(value) => onThinkingDepthChange(value as ReasoningLevel)}
            >
              {(capability?.supportedReasoningLevels ?? []).map((level) => (
                <DropdownMenuRadioItem key={level} value={level}>
                  <span className="prelude-menu__item-label">{REASONING_LABELS[level]}</span>
                </DropdownMenuRadioItem>
              ))}
            </DropdownMenuRadioGroup>
          </DropdownMenuSubmenu>
        ) : null}
      </DropdownMenuGroup>
      <DropdownMenuSeparator />
      <DropdownMenuGroup>
        <DropdownMenuItem className="prelude-menu__manage-item" onClick={onManage}>
          <Settings className="prelude-menu__manage-icon" aria-hidden="true" />
          <span className="prelude-menu__item-label">管理模型</span>
        </DropdownMenuItem>
      </DropdownMenuGroup>
    </DropdownMenu>
  )
}
