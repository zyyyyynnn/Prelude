<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useLlmSettings } from '../../composables/useLlmSettings'
import { Input } from '@/components/ui/input'
import { Button } from '@/components/ui/button'
import {
  Combobox,
  ComboboxAnchor,
  ComboboxContent,
  ComboboxInput,
  ComboboxItem,
} from '@/components/ui/combobox'
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select'
import { Eye, EyeOff, Trash2 } from '@lucide/vue'
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
  [OPENAI_COMPATIBLE_PROVIDER]: 'OpenAI 兼容协议',
}

const {
  loading, saving, testing, discovering,
  providerOptions, selectedProviderKey, selectedModel,
  baseUrlInput, apiKeyInput, apiKeyMasked, maxTokens, thinkingDepth,
  modelOptions, modelDiscoveryHint, isOpenAiCompatible,
  loadSettings, saveSettings, clearApiKey, testSettings, discoverModels,
} = useLlmSettings()

const showApiKey = ref(false)
const modelComboboxOpen = ref(false)
const modelCandidateIndex = ref(-1)

const { handleSubmit, setValues, submitCount } = useForm({
  validationSchema: toTypedSchema(llmSettingsSchema),
})

function providerDisplayName(key: string, fallback: string): string {
  return DISPLAY_NAME_MAP[key] ?? fallback
}

const apiKeyStatusLabel = computed(() => apiKeyMasked.value ? `已保存：${apiKeyMasked.value}` : '未保存')
const canShowModelCombobox = computed(() => isOpenAiCompatible.value && modelOptions.value.length > 0)

function selectModelCandidate(model: string) {
  selectedModel.value = model
  modelComboboxOpen.value = false
  modelCandidateIndex.value = -1
}

function moveModelCandidate(delta: number) {
  if (!canShowModelCombobox.value) {
    return
  }
  modelComboboxOpen.value = true
  const count = modelOptions.value.length
  if (count === 0) {
    return
  }
  if (modelCandidateIndex.value < 0) {
    modelCandidateIndex.value = delta > 0 ? 0 : count - 1
    return
  }
  modelCandidateIndex.value = (modelCandidateIndex.value + delta + count) % count
}

function selectHighlightedModelCandidate() {
  if (!modelComboboxOpen.value || !canShowModelCombobox.value) {
    return
  }
  const model = modelOptions.value[modelCandidateIndex.value]
  if (model) {
    selectModelCandidate(model)
  }
}

function handleModelCandidateKeydown(event: KeyboardEvent) {
  if (event.isComposing) {
    return
  }
  if (event.key === 'ArrowDown') {
    event.preventDefault()
    event.stopPropagation()
    moveModelCandidate(1)
    return
  }
  if (event.key === 'ArrowUp') {
    event.preventDefault()
    event.stopPropagation()
    moveModelCandidate(-1)
    return
  }
  if (event.key === 'Enter' && modelComboboxOpen.value) {
    event.preventDefault()
    event.stopPropagation()
    selectHighlightedModelCandidate()
    return
  }
  if (event.key === 'Escape') {
    event.preventDefault()
    event.stopPropagation()
    modelComboboxOpen.value = false
  }
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

watch(canShowModelCombobox, (canShow) => {
  if (!canShow) {
    modelComboboxOpen.value = false
    modelCandidateIndex.value = -1
  }
})

watch(modelOptions, () => {
  modelCandidateIndex.value = -1
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
          <FormField name="providerKey">
            <FormItem>
              <FormLabel>接入方式</FormLabel>
              <Select
                :model-value="selectedProviderKey"
                @update:model-value="(value) => { selectedProviderKey = String(value) }"
              >
                <SelectTrigger aria-label="接入方式">
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
            <FormMessage v-if="submitCount > 0" />
          </FormItem>
        </FormField>

        <FormField name="model" v-slot="{ componentField }">
          <FormItem>
            <FormLabel>模型</FormLabel>
              <Select
                v-if="!isOpenAiCompatible"
                :disabled="modelOptions.length === 0"
                :model-value="selectedModel"
                @update:model-value="(value) => { selectedModel = String(value) }"
              >
                <SelectTrigger aria-label="模型">
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
              <Combobox
                v-model:open="modelComboboxOpen"
                :model-value="selectedModel"
                :open-on-focus="canShowModelCombobox"
                :open-on-click="canShowModelCombobox"
                :reset-search-term-on-select="false"
                ignore-filter
                @update:model-value="(value) => { selectModelCandidate(String(value)) }"
              >
                <ComboboxAnchor as-child>
                  <div class="model-combobox">
                    <FormControl>
                      <ComboboxInput
                        :model-value="selectedModel"
                        autocomplete="off"
                        placeholder="请选择模型"
                        @update:model-value="(value) => { selectedModel = String(value) }"
                        @blur="componentField.onBlur"
                        @keydown.capture="handleModelCandidateKeydown"
                      />
                    </FormControl>
                  </div>
                </ComboboxAnchor>
                <ComboboxContent
                  v-if="canShowModelCombobox"
                  data-byok-model-combobox-content
                  align="start"
                  side="bottom"
                  :side-offset="4"
                  @escape-key-down.prevent="modelComboboxOpen = false"
                >
                  <ComboboxItem
                    v-for="(model, index) in modelOptions"
                    :key="model"
                    :value="model"
                    :text-value="model"
                    :class="{ 'bg-accent text-accent-foreground': modelCandidateIndex === index }"
                    data-byok-model-combobox-item
                    @pointermove="modelCandidateIndex = index"
                  >
                    {{ model }}
                  </ComboboxItem>
                </ComboboxContent>
              </Combobox>
              <p v-if="modelDiscoveryHint" class="helper-text text-sm">{{ modelDiscoveryHint }}</p>
            </template>
            <FormMessage v-if="submitCount > 0" />
          </FormItem>
        </FormField>
      </div>

      <FormField v-if="isOpenAiCompatible" name="baseUrl" v-slot="{ componentField }">
        <FormItem>
          <FormLabel>Base URL</FormLabel>
          <div class="endpoint-row">
            <FormControl>
              <Input
                :model-value="baseUrlInput"
                autocomplete="off"
                placeholder="例如：https://api.deepseek.com/v1"
                @update:model-value="(value) => { baseUrlInput = String(value) }"
                @blur="componentField.onBlur"
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
              检测模型
            </Button>
          </div>
          <p class="helper-text text-sm">填写接口根地址，通常以 /v1 结尾；不要填写 /chat/completions。</p>
          <FormMessage v-if="submitCount > 0" />
        </FormItem>
      </FormField>

      <FormField name="apiKey" v-slot="{ componentField }">
        <FormItem class="relative">
          <FormLabel>API Key</FormLabel>
          <div class="relative w-full flex items-center">
            <FormControl>
              <Input
                :model-value="apiKeyInput"
                autocomplete="off"
                placeholder="留空表示不修改当前 Key"
                :type="showApiKey ? 'text' : 'password'"
                class="pr-20"
                @update:model-value="(value) => { apiKeyInput = String(value) }"
                @blur="componentField.onBlur"
              />
            </FormControl>
            <div class="absolute right-0 top-0 h-full flex items-center pr-1">
              <button
                v-if="apiKeyMasked"
                type="button"
                aria-label="清除 API Key"
                class="px-2 py-2 hover:bg-transparent text-muted-foreground hover:text-destructive flex items-center justify-center transition-colors [transition-duration:var(--motion-duration-base)] [transition-timing-function:var(--motion-ease-standard)]"
                @click="clearApiKey"
              >
                <Trash2 class="h-4 w-4" />
              </button>
              <button
                type="button"
                :aria-label="showApiKey ? '隐藏 API Key' : '显示 API Key'"
                class="px-2 py-2 hover:bg-transparent text-muted-foreground flex items-center justify-center transition-colors [transition-duration:var(--motion-duration-base)] [transition-timing-function:var(--motion-ease-standard)]"
                @click="showApiKey = !showApiKey"
              >
                <Eye v-if="showApiKey" class="h-4 w-4" />
                <EyeOff v-else class="h-4 w-4" />
              </button>
            </div>
          </div>
          <p class="helper-text api-key-status text-sm">{{ apiKeyStatusLabel }}</p>
          <FormMessage v-if="submitCount > 0" />
        </FormItem>
      </FormField>

      <div class="form-section">
        <div class="form-section__title">高级设置</div>
        <div class="advanced-grid">
          <FormField name="maxTokens">
            <FormItem>
              <FormLabel>最大回复长度 (Max Tokens)</FormLabel>
              <FormControl>
                <Select
                  :model-value="maxTokens ? String(maxTokens) : 'auto'"
                  @update:model-value="(value) => { maxTokens = value === 'auto' ? undefined : Number(value) }"
                >
                  <SelectTrigger class="w-full" aria-label="最大回复 Token">
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
              <FormMessage v-if="submitCount > 0" />
            </FormItem>
          </FormField>

          <FormField name="thinkingDepth">
            <FormItem>
              <FormLabel>思考深度 (Thinking Depth)</FormLabel>
              <Select
                :model-value="thinkingDepth || 'default'"
                @update:model-value="(value) => { thinkingDepth = String(value) === 'default' ? undefined : String(value) }"
              >
                <SelectTrigger aria-label="思考深度">
                  <SelectValue placeholder="默认 (Default)" />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value="default">默认（Default）</SelectItem>
                  <SelectItem value="low">低 (Low)</SelectItem>
                  <SelectItem value="medium">中 (Medium)</SelectItem>
                  <SelectItem value="high">高 (High)</SelectItem>
                  <SelectItem value="xhigh">极高 (Extreme)</SelectItem>
                </SelectContent>
              </Select>
              <p class="helper-text text-sm">部分模型可能不支持，测试失败时请改回默认。</p>
              <FormMessage v-if="submitCount > 0" />
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
  font-family: var(--font-serif);
}
.form-section {
  margin-top: var(--spacing-md);
  padding-top: var(--spacing-md);
  border-top: 1px dashed var(--color-border);
}
.form-section__title {
  margin: var(--spacing-xs) 0 var(--spacing-md);
  font-size: var(--font-size-md);
  font-weight: 500;
  color: var(--color-text-primary);
  font-family: var(--font-serif);
}
.advanced-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(var(--layout-form-field-min-inline-size), 1fr));
  gap: var(--spacing-md);
}
.endpoint-row {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: var(--spacing-sm);
}
.endpoint-row__button {
  position: relative;
}
.helper-text {
  margin-top: var(--spacing-xs);
  color: var(--color-text-secondary);
}
.api-key-status {
  font-weight: 500;
}
.model-combobox {
  width: 100%;
}
</style>
