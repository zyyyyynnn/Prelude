<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { ElButton, ElCard, ElForm, ElFormItem, ElInput, ElOption, ElSelect, ElTag } from 'element-plus'
import { fetchProviders, fetchUserLlmConfig, saveUserLlmConfig } from '../api/llm'
import type { LlmProviderOption } from '../api/contracts'
import { usePageNotice } from '../composables/usePageNotice'

const loading = ref(false)
const saving = ref(false)
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

onMounted(() => {
  void loadSettings()
})
</script>

<template>
  <section class="page">
    <div class="page__header">
      <p class="eyebrow">设置</p>
      <h2 class="page__title">LLM 配置</h2>
      <p class="page__lead">选择 Provider、模型，并维护用户 API Key。</p>
    </div>

    <div class="page__grid page__grid--single">
      <ElCard class="ui-card panel">
        <div class="panel__head">
          <div>
            <h3 class="panel__title">Provider 抽象层</h3>
          </div>
          <ElTag class="ui-badge" effect="light">Provider 与模型</ElTag>
        </div>

        <ElForm class="form-grid" label-position="top" @submit.prevent>
          <ElFormItem label="Provider">
            <ElSelect
              v-model="selectedProviderKey"
              class="ui-select"
              placeholder="请选择 Provider"
              size="large"
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
              :disabled="modelOptions.length === 0"
              placeholder="请选择模型"
              size="large"
            >
              <ElOption v-for="model in modelOptions" :key="model" :label="model" :value="model" />
            </ElSelect>
          </ElFormItem>

          <ElFormItem label="当前 API Key">
            <div class="settings-summary">
              <ElTag v-if="apiKeyMasked" class="ui-badge" effect="light">
                {{ apiKeyMasked }}
              </ElTag>
              <span v-else class="field__hint">未配置</span>
            </div>
          </ElFormItem>

          <ElFormItem label="新 API Key / 清空">
            <ElInput
              v-model="apiKeyInput"
              class="ui-input"
              autocomplete="off"
              placeholder="留空表示清空当前用户 Key"
              show-password
              size="large"
            />
          </ElFormItem>

          <p class="field__hint">
            保存时会提交 `apiKey`。输入新值会更新密钥，留空会按规格清空自定义 Key。
          </p>

          <div class="button-row">
            <ElButton
              class="ui-button ui-button--primary"
              :loading="saving"
              size="large"
              type="primary"
              @click="saveSettings"
            >
              保存设置
            </ElButton>
          </div>
        </ElForm>
      </ElCard>
    </div>
  </section>
</template>
