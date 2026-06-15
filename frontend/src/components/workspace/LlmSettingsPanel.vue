<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useLlmSettings } from '../../composables/useLlmSettings'
import { Input } from '@/components/ui/input'
import { Button } from '@/components/ui/button'
import { Badge } from '@/components/ui/badge'
import {
  PopoverAnchor,
  PopoverContent,
  PopoverPortal,
  PopoverRoot,
} from 'reka-ui'
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select'
import { Check, Eye, EyeOff, Trash2 } from '@lucide/vue'
import {
  FormControl,
  FormField,
  FormItem,
  FormLabel,
  FormMessage,
} from '@/components/ui/form'
import { useForm } from 'vee-validate'
import { toTypedSchema } from '@vee-validate/zod'
import { llmSettingsSchema } from '../../schemas/llm'

const OPENAI_COMPATIBLE_PROVIDER = 'openai-compatible'

// 前端展示映射：后端 providerKey 仍为 openai-compatible，仅展示层映射为对普通用户友好的名称。
const DISPLAY_NAME_MAP: Record<string, string> = {
  [OPENAI_COMPATIBLE_PROVIDER]: '自定义 OpenAI 兼容接口',
}

const {
  loading, saving, testing, discovering, testStatus,
  providerOptions, selectedProviderKey, selectedModel,
  baseUrlInput, apiKeyInput, apiKeyMasked, maxTokens, thinkingDepth,
  modelOptions, modelDiscoveryHint, isOpenAiCompatible,
  loadSettings, saveSettings, clearApiKey, testSettings, discoverModels,
} = useLlmSettings()

const showApiKey = ref(false)
const modelPopoverOpen = ref(false)

const { handleSubmit, setValues } = useForm({
  validationSchema: toTypedSchema(llmSettingsSchema),
})

function providerDisplayName(key: string, fallback: string): string {
  return DISPLAY_NAME_MAP[key] ?? fallback
}

const testBadgeVariant = computed(() => {
  switch (testStatus.value.state) {
    case 'testing': return 'secondary'
    case 'success': return 'default'
    case 'error': return 'destructive'
    default: return 'outline'
  }
})
const testBadgeText = computed(() => {
  switch (testStatus.value.state) {
    case 'testing': return '测试中'
    case 'success': return '已通过'
    case 'error': return '失败'
    default: return '未测试'
  }
})
const apiKeyStatusLabel = computed(() => apiKeyMasked.value ? `已保存：${apiKeyMasked.value}` : '未保存')
const canShowModelPopover = computed(() => isOpenAiCompatible.value && modelOptions.value.length > 0)

function openModelPopover() {
  if (canShowModelPopover.value) {
    modelPopoverOpen.value = true
  }
}

function selectModelCandidate(model: string) {
  selectedModel.value = model
  modelPopoverOpen.value = false
}

watch([selectedProviderKey, baseUrlInput, selectedModel, apiKeyInput, maxTokens, thinkingDepth], () => {
  setValues({
    providerKey: selectedProviderKey.value,
    baseUrl: baseUrlInput.value,
    model: selectedModel.value,
    apiKey: apiKeyInput.value,
    maxTokens: maxTokens.value,
    thinkingDepth: thinkingDepth.value,
  })
}, { immediate: true })

watch(canShowModelPopover, (canShow) => {
  if (!canShow) {
    modelPopoverOpen.value = false
  }
})

const onSubmit = handleSubmit(async () => {
  await saveSettings()
})

onMounted(() => {
  void loadSettings()
})

defineExpose({ submit: onSubmit, test: testSettings, saving, testing, loading })
</script>

<template>
  <div class="panel-content-wrapper">
    <form class="flex flex-col gap-6" @submit.prevent="onSubmit">

      <div class="field-grid">
        <FormField name="providerKey" v-slot="{ componentField }">
          <FormItem>
            <FormLabel>接入方式</FormLabel>
            <Select v-bind="componentField" v-model="selectedProviderKey">
              <SelectTrigger>
                <SelectValue placeholder="请选择接入方式" />
              </SelectTrigger>
              <SelectContent>
                <SelectItem
                  v-for="provider in providerOptions"
                  :key="provider.providerKey"
                  :value="provider.providerKey"
                >
                  {{ providerDisplayName(provider.providerKey, provider.displayName) }}
                </SelectItem>
              </SelectContent>
            </Select>
            <FormMessage />
          </FormItem>
        </FormField>

        <FormField name="model" v-slot="{ componentField }">
          <FormItem>
            <FormLabel>模型</FormLabel>
            <Select
              v-if="!isOpenAiCompatible"
              :disabled="modelOptions.length === 0"
              v-bind="componentField"
              v-model="selectedModel"
            >
              <SelectTrigger>
                <SelectValue placeholder="请选择模型" />
              </SelectTrigger>
              <SelectContent>
                <SelectItem
                  v-for="model in modelOptions"
                  :key="model"
                  :value="model"
                >
                  {{ model }}
                </SelectItem>
              </SelectContent>
            </Select>
            <template v-else>
              <PopoverRoot v-model:open="modelPopoverOpen">
                <PopoverAnchor as-child>
                  <div class="model-combobox">
                    <FormControl>
                      <Input
                        v-model="selectedModel"
                        v-bind="componentField"
                        autocomplete="off"
                        placeholder="填写或选择模型 ID"
                        @focus="openModelPopover"
                        @click="openModelPopover"
                        @keydown.down.prevent="openModelPopover"
                        @keydown.esc="modelPopoverOpen = false"
                      />
                    </FormControl>
                  </div>
                </PopoverAnchor>
                <PopoverPortal>
                  <PopoverContent
                    v-if="canShowModelPopover"
                    align="start"
                    side="bottom"
                    :side-offset="4"
                    class="model-combobox__content z-[105] w-[var(--reka-popover-trigger-width)] rounded-md border border-border bg-surface shadow-md"
                  >
                    <div class="model-combobox__viewport">
                      <button
                        v-for="model in modelOptions"
                        :key="model"
                        type="button"
                        class="model-combobox__item"
                        @mousedown.prevent
                        @click="selectModelCandidate(model)"
                      >
                        <span class="model-combobox__check">
                          <Check v-if="selectedModel === model" class="h-4 w-4" />
                        </span>
                        <span class="model-combobox__item-text">{{ model }}</span>
                      </button>
                    </div>
                  </PopoverContent>
                </PopoverPortal>
              </PopoverRoot>
              <p v-if="modelDiscoveryHint" class="helper-text">{{ modelDiscoveryHint }}</p>
            </template>
            <FormMessage />
          </FormItem>
        </FormField>
      </div>

      <FormField v-if="isOpenAiCompatible" name="baseUrl" v-slot="{ componentField }">
        <FormItem>
          <FormLabel>Base URL</FormLabel>
          <div class="endpoint-row">
            <FormControl>
              <Input
                v-model="baseUrlInput"
                v-bind="componentField"
                autocomplete="off"
                placeholder="例如：https://api.deepseek.com/v1"
              />
            </FormControl>
            <Button
              type="button"
              variant="secondary"
              class="endpoint-row__button"
              :disabled="discovering"
              :loading="discovering"
              @click="discoverModels"
            >
              自动检测模型
            </Button>
          </div>
          <p class="helper-text">填写接口根地址，通常以 /v1 结尾；不要填写 /chat/completions。</p>
          <FormMessage />
        </FormItem>
      </FormField>

      <FormField name="apiKey" v-slot="{ componentField }">
        <FormItem class="relative">
          <FormLabel>API Key</FormLabel>
          <div class="relative w-full flex items-center">
            <FormControl>
              <Input
                v-model="apiKeyInput"
                v-bind="componentField"
                autocomplete="off"
                placeholder="留空表示不修改当前 Key"
                :type="showApiKey ? 'text' : 'password'"
                class="pr-20"
              />
            </FormControl>
            <div class="absolute right-0 top-0 h-full flex items-center pr-1">
              <button
                v-if="apiKeyMasked"
                type="button"
                aria-label="清除 API Key"
                class="px-2 py-2 hover:bg-transparent text-muted-foreground hover:text-destructive flex items-center justify-center transition-colors duration-300 ease-in-out"
                @click="clearApiKey"
              >
                <Trash2 class="h-4 w-4" />
              </button>
              <button
                type="button"
                :aria-label="showApiKey ? '隐藏 API Key' : '显示 API Key'"
                class="px-2 py-2 hover:bg-transparent text-muted-foreground flex items-center justify-center transition-colors duration-300 ease-in-out"
                @click="showApiKey = !showApiKey"
              >
                <Eye v-if="showApiKey" class="h-4 w-4" />
                <EyeOff v-else class="h-4 w-4" />
              </button>
            </div>
          </div>
          <p class="helper-text api-key-status">{{ apiKeyStatusLabel }}</p>
          <FormMessage />
        </FormItem>
      </FormField>

      <div class="test-status-row">
        <span class="test-status-row__label">连接测试</span>
        <Badge :variant="testBadgeVariant">{{ testBadgeText }}</Badge>
        <span class="test-status-row__message" :class="{ 'test-status-row__message--error': testStatus.state === 'error' }">
          {{ testStatus.message }}
        </span>
      </div>

      <div class="form-section">
        <div class="form-section__title">高级设置</div>
        <div class="advanced-grid">
          <FormField name="maxTokens" v-slot="{ componentField }">
            <FormItem>
              <FormLabel>最大回复长度 (Max Tokens)</FormLabel>
              <FormControl>
                <Select v-bind="componentField" :model-value="maxTokens ? String(maxTokens) : 'auto'" @update:model-value="v => maxTokens = v === 'auto' ? undefined : Number(v)">
                  <SelectTrigger class="w-full">
                    <SelectValue placeholder="模型默认 (Auto)" />
                  </SelectTrigger>
                  <SelectContent>
                    <SelectItem value="auto">模型默认 (Auto)</SelectItem>
                    <SelectItem value="4096">常规 (4096)</SelectItem>
                    <SelectItem value="8192">长回复 (8192)</SelectItem>
                    <SelectItem value="32768">深度分析 (32768)</SelectItem>
                  </SelectContent>
                </Select>
              </FormControl>
              <FormMessage />
            </FormItem>
          </FormField>

          <FormField name="thinkingDepth" v-slot="{ componentField }">
            <FormItem>
              <FormLabel>思考深度 (Thinking Depth)</FormLabel>
              <Select v-bind="componentField" v-model="thinkingDepth">
                <SelectTrigger>
                  <SelectValue placeholder="默认 (Default)" />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value="low">低 (Low)</SelectItem>
                  <SelectItem value="medium">中 (Medium)</SelectItem>
                  <SelectItem value="high">高 (High)</SelectItem>
                  <SelectItem value="xhigh">极高 (Extreme)</SelectItem>
                </SelectContent>
              </Select>
              <p class="helper-text">部分模型可能不支持，测试失败时请改回默认。</p>
              <FormMessage />
            </FormItem>
          </FormField>
        </div>
      </div>

    </form>
  </div>
</template>

<style scoped>
.panel-content-wrapper {
  display: flex;
  flex-direction: column;
  gap: var(--spacing-md);
}
.form-section {
  margin-top: var(--spacing-md);
  padding-top: var(--spacing-md);
  border-top: 1px dashed var(--color-border);
}
.form-section__title {
  margin: var(--spacing-xs) 0 var(--spacing-md);
  font-size: 16px;
  font-weight: 500;
  color: var(--color-text-primary);
  font-family: var(--font-serif);
}
.advanced-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(260px, 1fr));
  gap: var(--spacing-md);
}
.endpoint-row {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: var(--spacing-sm);
}
.endpoint-row__button {
  position: relative;
  min-width: calc(var(--ui-height-base) * 4);
}
.helper-text {
  margin-top: var(--spacing-xs);
  font-size: 13px;
  color: var(--color-text-secondary);
}
.api-key-status {
  font-weight: 500;
}
.model-combobox {
  width: 100%;
}
.model-combobox__content {
  max-height: calc(var(--ui-height-base) * 7);
  overflow: hidden;
}
.model-combobox__viewport {
  display: flex;
  flex-direction: column;
  gap: var(--spacing-xs);
  padding: var(--spacing-xs);
  max-height: calc(var(--ui-height-base) * 7);
  overflow-y: auto;
}
.model-combobox__item {
  appearance: none;
  position: relative;
  display: flex;
  align-items: center;
  width: 100%;
  min-height: var(--ui-height-base);
  border: 0;
  border-radius: var(--radius-md);
  padding: var(--spacing-sm) var(--spacing-sm) var(--spacing-sm) calc(var(--spacing-md) + var(--spacing-lg));
  background: transparent;
  color: var(--color-text-primary);
  text-align: left;
  cursor: default;
  transition: background-color 0.3s ease-in-out, color 0.3s ease-in-out, box-shadow 0.3s ease-in-out;
}
.model-combobox__item:hover {
  background: var(--color-surface-hover);
  color: var(--color-text-primary);
}
.model-combobox__item:focus-visible {
  background: var(--color-surface-hover);
  color: var(--color-text-primary);
  outline: none;
  box-shadow: var(--shadow-ring);
}
.model-combobox__check {
  position: absolute;
  left: var(--spacing-sm);
  display: flex;
  align-items: center;
  justify-content: center;
  width: var(--spacing-md);
  height: var(--spacing-md);
}
.model-combobox__item-text {
  min-width: 0;
  overflow-wrap: anywhere;
}
.test-status-row {
  display: flex;
  align-items: center;
  gap: var(--spacing-sm);
  flex-wrap: wrap;
}
.test-status-row__label {
  font-size: 14px;
  color: var(--color-text-secondary);
}
.test-status-row__message {
  font-size: 13px;
  color: var(--color-text-secondary);
}
.test-status-row__message--error {
  color: var(--color-error);
}
</style>
