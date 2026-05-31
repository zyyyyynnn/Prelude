<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { ElButton, ElCard, ElForm, ElFormItem, ElInput, ElOption, ElSelect, ElTag } from 'element-plus'
import { fetchProviders, fetchUserLlmConfig, saveUserLlmConfig, testUserLlmConfig } from '../api/llm'
import type { LlmProviderOption } from '../api/contracts'
import { usePageNotice } from '../composables/usePageNotice'

const loading = ref(false)
const saving = ref(false)
const testing = ref(false)
const lastTestMessage = ref('未测试')
const { showNotice } = usePageNotice()

const providerOptions = ref<LlmProviderOption[]>([])
const selectedProviderKey = ref('')
const selectedModel = ref('')
const apiKeyInput = ref('')
const apiKeyMasked = ref('')

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
      apiKey: apiKeyInput.value,
    })

    selectedProviderKey.value = result.providerKey || selectedProviderKey.value
    selectedModel.value = result.model || selectedModel.value
    apiKeyMasked.value = result.apiKeyMasked || ''
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
              />
            </ElFormItem>

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
.workspace-page {
  flex: 1;
  display: flex;
  flex-direction: column;
  background: var(--color-bg);
  overflow: hidden;
  height: 100vh;
}
.workspace-header {
  display: flex;
  align-items: center;
  padding: 16px 24px;
  border-bottom: 1px solid var(--color-border);
  background: rgba(250, 249, 245, 0.85);
  backdrop-filter: blur(12px);
  position: sticky;
  top: 0;
  z-index: 100;
  flex-shrink: 0;
  height: 72px;
  box-sizing: border-box;
}
.workspace-header__main {
  display: flex;
  align-items: center;
  justify-content: space-between;
  width: 100%;
}
.workspace-header__title-area {
  display: flex;
  align-items: center;
  gap: 12px;
}
.workspace-header__title {
  margin: 0;
  font-family: var(--font-serif);
  font-size: 20px;
  font-weight: 500;
  color: var(--color-text-primary);
}
.workspace-page__content {
  flex: 1;
  padding: 24px 40px;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  gap: 24px;
}
.page-grid {
  display: flex;
  flex-direction: column;
  gap: 24px;
}
.detail-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
  gap: 16px;
  margin-bottom: 24px;
}
.detail-card {
  padding: 16px;
  background: var(--color-sand);
  border-radius: var(--radius-lg);
  border: 1px solid var(--color-border);
}
.detail-card__title {
  margin: 4px 0;
  font-size: 16px;
  font-weight: 500;
  color: var(--color-text-primary);
}
.detail-card__meta {
  margin: 0;
  font-size: 13px;
  color: var(--color-text-tertiary);
}
.field-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(280px, 1fr));
  gap: 24px;
}
.button-row {
  display: flex;
  gap: 12px;
  margin-top: 32px;
}
</style>
