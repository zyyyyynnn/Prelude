import {
  ChevronDown,
  ChevronRight,
  Settings,
} from 'lucide-react'
import type { LlmConfigResponse, LlmProviderResponse } from '@/features/settings'
import {
  DropdownMenu,
  DropdownMenuGroup,
  DropdownMenuItem,
  DropdownMenuRadioGroup,
  DropdownMenuRadioItem,
  DropdownMenuSeparator,
  DropdownMenuSubmenu,
} from '@/shared/ui'

const thinkingOptions = [
  { value: 'default', label: '默认' },
  { value: 'low', label: '低' },
  { value: 'medium', label: '中' },
  { value: 'high', label: '高' },
  { value: 'xhigh', label: '极高' },
]

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
  onThinkingDepthChange: (depth: string | null) => void
  onManage: () => void
}) {
  const provider = providers.find((item) => item.providerKey === config.providerKey)
  const models = [...new Set([config.model, ...(provider?.availableModels ?? [])])].filter(Boolean)
  const thinkingValue = config.thinkingDepth || 'default'
  const thinkingLabel =
    thinkingOptions.find((item) => item.value === thinkingValue)?.label ?? config.thinkingDepth ?? '默认'

  return (
    <DropdownMenu
      side="top"
      className="prelude-menu--structured prelude-menu--model"
      trigger={
        <button
          type="button"
          className="prompt-bar__control prompt-bar__model ui-action"
          aria-label={`模型：${config.model}，思考深度：${thinkingLabel}`}
          disabled={saving}
        >
          <span className="prompt-bar__control-label">
            {config.model} · {thinkingLabel}
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
        <DropdownMenuSubmenu
          trigger={
            <MenuRow label="思考深度" value={thinkingLabel} submenu />
          }
        >
          <DropdownMenuRadioGroup
            value={thinkingValue}
            onValueChange={(value) => onThinkingDepthChange(value === 'default' ? null : value)}
          >
            {thinkingOptions.map((option) => (
              <DropdownMenuRadioItem key={option.value} value={option.value}>
                <span className="prelude-menu__item-label">{option.label}</span>
              </DropdownMenuRadioItem>
            ))}
          </DropdownMenuRadioGroup>
        </DropdownMenuSubmenu>
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
