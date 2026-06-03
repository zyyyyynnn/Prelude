import { computed, ref, watch } from 'vue'
import { fetchProviders, fetchUserLlmConfig, saveUserLlmConfig, testUserLlmConfig } from '../api/llm'
import type { LlmProviderOption } from '../api/contracts'
import { usePageNotice } from './usePageNotice'
import { getErrorMessage } from '../utils/errors'

export function useLlmSettings() {
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
  const maxTokens = ref<number | undefined>(undefined)
  const thinkingDepth = ref<string | undefined>(undefined)

  const currentProvider = computed(
    () => providerOptions.value.find((item) => item.providerKey === selectedProviderKey.value) ?? null,
  )

  const modelOptions = computed(() => currentProvider.value?.models ?? [])

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

  return {
    loading,
    saving,
    testing,
    lastTestMessage,
    providerOptions,
    selectedProviderKey,
    selectedModel,
    apiKeyInput,
    apiKeyMasked,
    maxTokens,
    thinkingDepth,
    currentProvider,
    modelOptions,
    loadSettings,
    saveSettings,
    clearApiKey,
    testSettings,
  }
}
