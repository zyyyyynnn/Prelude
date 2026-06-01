<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import {
  ElButton,
  ElCard,
  ElCollapse,
  ElCollapseItem,
  ElForm,
  ElFormItem,
  ElInput,
  ElOption,
  ElSelect,
  ElTag,
} from 'element-plus'
import { fetchProviders, fetchUserLlmConfig, saveUserLlmConfig, testUserLlmConfig } from '../api/llm'
import type { LlmProviderOption } from '../api/contracts'
import { usePageNotice } from '../composables/usePageNotice'
import { usePopperMatchTrigger } from '../composables/usePopperMatchTrigger'

const loading = ref(false)
const saving = ref(false)
const testing = ref(false)
const lastTestMessage = ref('未测试')
const { showNotice } = usePageNotice()

const providerSelect = usePopperMatchTrigger()
const modelSelect = usePopperMatchTrigger()
const maxTokensSelect = usePopperMatchTrigger()
const thinkingDepthSelect = usePopperMatchTrigger()

const providerOptions = ref<LlmProviderOption[]>([])
const selectedProviderKey = ref('')
const selectedModel = ref('')
const apiKeyInput = ref('')
const apiKeyMasked = ref('')
const maxTokens = ref<number | undefined>(undefined)
const thinkingDepth = ref<string | undefined>(undefined)
const advancedOpen = ref<string[]>([])

const currentProvider = computed(
  () => providerOptions.value.find((item) => item.providerKey === selectedProviderKey.value) ?? null,
)

const modelOptions = computed(() => currentProvider.value?.models ?? [])

function getErrorMessage(error: unknown) {
  if (error instanceof Error) {
    return error.message
  }

  return '请求失败'
}

function applySelection(providerKey: string, model: string) {
  selectedProviderKey.value = providerKey
  selectedModel.value = model
}

watch(
  modelOptions,
  (models) => {
    if (!models.length) {
      selectedModel.value = ''
      return
    }

    if (!models.includes(selectedModel.value)) {
      selectedModel.value = models[0]
    }
  },
  { immediate: true },
)

async function loadSettings() {
  loading.value = true
  try {
    const [providers, config] = await Promise.all([fetchProviders(), fetchUserLlmConfig()])
    providerOptions.value = providers

    const providerKey = config.providerKey || providers[0]?.providerKey || ''
    const provider = providers.find((item) => item.providerKey === providerKey) ?? providers[0] ?? null
    applySelection(provider?.providerKey || '', config.model || provider?.models[0] || '')
    apiKeyMasked.value = config.apiKeyMasked || ''
    maxTokens.value = config.maxTokens ?? undefined
    thinkingDepth.value = config.thinkingDepth ?? undefined
    lastTestMessage.value = '未测试'
    showNotice('配置已加载', 'success')
  } catch (error) {
    showNotice(getErrorMessage(error), 'error')
  } finally {
    loading.value = false
  }
}

async function saveSettings() {
  if (!selectedProviderKey.value || !selectedModel.value) {
    showNotice('请选择 Provider 和模型', 'warning')
    return
  }

  saving.value = true

  try {
    const result = await saveUserLlmConfig({
      providerKey: selectedProviderKey.value,
      model: selectedModel.value,
      apiKey: apiKeyInput.value === '' ? undefined : apiKeyInput.value,
      maxTokens: maxTokens.value ?? undefined,
      thinkingDepth: thinkingDepth.value ?? undefined,
    })

    selectedProviderKey.value = result.providerKey || selectedProviderKey.value
    selectedModel.value = result.model || selectedModel.value
    apiKeyMasked.value = result.apiKeyMasked || ''
    maxTokens.value = result.maxTokens ?? undefined
    thinkingDepth.value = result.thinkingDepth ?? undefined
    lastTestMessage.value = '配置已变更，建议重新测试'
    if (apiKeyInput.value && !result.apiKeyMasked) {
      apiKeyInput.value = ''
      showNotice('配置已保存，但接口未返回脱敏 Key', 'warning')
      return
    }
    apiKeyInput.value = ''
    showNotice('LLM 配置已保存', 'success')
  } catch (error) {
    showNotice(getErrorMessage(error), 'error')
  } finally {
    saving.value = false
  }
}

async function clearApiKey() {
  saving.value = true
  try {
    const result = await saveUserLlmConfig({
      providerKey: selectedProviderKey.value,
      model: selectedModel.value,
      apiKey: '__CLEAR__',
    })
    apiKeyMasked.value = result.apiKeyMasked || ''
    apiKeyInput.value = ''
    showNotice('API Key 已清除', 'success')
  } catch (error) {
    showNotice(getErrorMessage(error), 'error')
  } finally {
    saving.value = false
  }
}

async function testSettings() {
  testing.value = true
  try {
    const result = await testUserLlmConfig()
    lastTestMessage.value = result.message || '模型配置测试通过'
    showNotice(lastTestMessage.value, result.ok ? 'success' : 'warning')
  } catch (error) {
    lastTestMessage.value = getErrorMessage(error)
    showNotice(lastTestMessage.value, 'error')
  } finally {
    testing.value = false
  }
}

onMounted(() => {
  void loadSettings()
})
</script>

<template>
  <section class="workspace-page">
    <header class="workspace-header">
      <div class="workspace-header__main">
        <div class="workspace-header__title-area">
          <h2 class="workspace-header__title">LLM 配置</h2>
        </div>
      </div>
    </header>

    <div class="workspace-page__content scrollable">
      <div class="page-grid page-grid--single">
        <ElCard class="ui-card panel">
          <div class="panel__head">
            <div>
              <p class="panel__eyebrow">模型层</p>
              <h3 class="panel__title">Provider 抽象层</h3>
              <p class="panel__lead">查看当前模型与已保存的脱敏 Key。</p>
            </div>
            <ElTag class="ui-badge" effect="light">Provider 与模型</ElTag>
          </div>

          <ElForm class="form-grid" label-position="top" @submit.prevent>
            <div class="detail-grid">
              <article class="detail-card">
                <p class="panel__eyebrow">当前 Provider</p>
                <h4 class="detail-card__title">{{ currentProvider?.displayName || '未选择' }}</h4>
                <p class="detail-card__meta">{{ modelOptions.length }} 个可选模型</p>
              </article>
              <article class="detail-card">
                <p class="panel__eyebrow">当前 API Key</p>
                <h4 class="detail-card__title">{{ apiKeyMasked || '未配置' }}</h4>
                <p class="detail-card__meta">保存新值会覆盖当前 Key。</p>
              </article>
              <article class="detail-card">
                <p class="panel__eyebrow">连通性测试</p>
                <h4 class="detail-card__title">{{ lastTestMessage }}</h4>
                <p class="detail-card__meta">保存配置后可测试当前模型服务。</p>
              </article>
            </div>

            <div class="field-grid">
              <ElFormItem label="Provider">
                <ElSelect
                  v-model="selectedProviderKey"
                  class="ui-select"
                  popper-class="custom-select-popper"
                  placeholder="请选择 Provider"
                  fit-input-width
                  style="width: 100%;"
                  :popper-style="providerSelect.popperStyle.value"
                  :ref="(el: any) => providerSelect.bind(el?.$el ?? null)"
                >
                  <ElOption
                    v-for="provider in providerOptions"
                    :key="provider.providerKey"
                    :label="provider.displayName"
                    :value="provider.providerKey"
                  />
                </ElSelect>
              </ElFormItem>

              <ElFormItem label="模型">
                <ElSelect
                  v-model="selectedModel"
                  class="ui-select"
                  popper-class="custom-select-popper"
                  :disabled="modelOptions.length === 0"
                  placeholder="请选择模型"
                  fit-input-width
                  style="width: 100%;"
                  :popper-style="modelSelect.popperStyle.value"
                  :ref="(el: any) => modelSelect.bind(el?.$el ?? null)"
                >
                  <ElOption v-for="model in modelOptions" :key="model" :label="model" :value="model" />
                </ElSelect>
              </ElFormItem>
            </div>

            <ElFormItem label="新 API Key / 清空">
              <ElInput
                v-model="apiKeyInput"
                class="ui-input"
                autocomplete="off"
                placeholder="留空表示清空当前用户 Key"
                show-password
              >
                <template #suffix>
                  <div
                    v-if="apiKeyMasked"
                    class="icon-action-btn"
                    @click="clearApiKey"
                    title="清除密钥"
                    style="cursor: pointer; display: flex; align-items: center; color: var(--color-text-tertiary);"
                  >
                    <svg xmlns="http://www.w3.org/2000/svg" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                      <path d="M3 6h18"></path>
                      <path d="M19 6v14c0 1-1 2-2 2H7c-1 0-2-1-2-2V6"></path>
                      <path d="M8 6V4c0-1 1-2 2-2h4c1 0 2 1 2 2v2"></path>
                    </svg>
                  </div>
                </template>
              </ElInput>
            </ElFormItem>

            <ElCollapse v-model="advancedOpen" class="advanced-collapse">
              <ElCollapseItem name="advanced" title="高级设置">
                <div class="advanced-grid">
                  <ElFormItem label="最大回复长度 (Max Tokens)">
                    <ElSelect
                      v-model="maxTokens"
                      class="ui-select"
                      popper-class="custom-select-popper"
                      placeholder="留空使用模型默认"
                      filterable
                      allow-create
                      clearable
                      fit-input-width
                      style="width: 100%;"
                      :popper-style="maxTokensSelect.popperStyle.value"
                      :ref="(el: any) => maxTokensSelect.bind(el?.$el ?? null)"
                    >
                      <ElOption label="标准输出 (8192 Tokens)" :value="8192" />
                      <ElOption label="满载输出 (32768 Tokens)" :value="32768" />
                    </ElSelect>
                  </ElFormItem>
                  <ElFormItem label="思考深度 (Thinking Depth)">
                    <ElSelect
                      v-model="thinkingDepth"
                      class="ui-select"
                      popper-class="custom-select-popper"
                      placeholder="默认 (Default)"
                      clearable
                      fit-input-width
                      style="width: 100%;"
                      :popper-style="thinkingDepthSelect.popperStyle.value"
                      :ref="(el: any) => thinkingDepthSelect.bind(el?.$el ?? null)"
                    >
                      <ElOption label="低 (Low)" value="low" />
                      <ElOption label="中 (Medium)" value="medium" />
                      <ElOption label="高 (High)" value="high" />
                      <ElOption label="极高 (Extreme)" value="xhigh" />
                    </ElSelect>
                  </ElFormItem>
                </div>
              </ElCollapseItem>
            </ElCollapse>

            <div class="button-row panel__footer-actions">
              <ElButton
                class="ui-button ui-button--primary ui-button--compact"
                :loading="saving"
                type="primary"
                @click="saveSettings"
              >
                保存设置
              </ElButton>
              <ElButton
                class="ui-button ui-button--secondary ui-button--compact"
                :disabled="saving || loading"
                :loading="testing"
                @click="testSettings"
              >
                测试连接
              </ElButton>
            </div>
          </ElForm>
        </ElCard>
      </div>
    </div>
  </section>
</template>

<style scoped>
.advanced-collapse {
  margin-top: var(--spacing-lg);
  border: none;
  background: transparent;
}
.advanced-collapse :deep(.el-collapse) {
  border-bottom: none;
}
.advanced-collapse :deep(.el-collapse-item__header) {
  font-size: 14px;
  font-weight: 500;
  color: var(--color-text-secondary);
  height: var(--spacing-2xl);
  line-height: var(--spacing-2xl);
  border-bottom: 1px solid var(--color-border);
  background: transparent;
}
.advanced-collapse :deep(.el-collapse-item__wrap) {
  border-bottom: none;
  background: transparent;
}
.advanced-collapse :deep(.el-collapse-item__content) {
  padding: var(--spacing-md) 0 0;
}
.advanced-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(260px, 1fr));
  gap: var(--spacing-md);
}
</style>
