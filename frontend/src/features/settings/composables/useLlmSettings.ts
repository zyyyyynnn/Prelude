import { computed, ref, watch } from 'vue'
import {
  discoverLlmModels,
  fetchProviders,
  fetchUserLlmConfig,
  saveUserLlmConfig,
  testUserLlmConfig,
} from '../api/llm'
import { withMinDelay } from '@/lib/utils'
import type { LlmProviderOption } from '@/api/contracts'
import { usePageNotice } from '@/composables/usePageNotice'
import { getErrorMessage } from '@/utils/errors'

const OPENAI_COMPATIBLE_PROVIDER = 'openai-compatible'

export type LlmTestState = 'idle' | 'testing' | 'success' | 'error'

function normalizeBaseUrl(baseUrl: string): string {
  // 轻量归一化用于 scope 比对：去尾部斜杠，去 /chat/completions 后缀。后端仍会做权威校验。
  let v = (baseUrl || '').trim()
  v = v.replace(/\/+$/, '')
  if (v.endsWith('/chat/completions')) {
    v = v.slice(0, -'/chat/completions'.length)
  }
  v = v.replace(/\/+$/, '')
  return v
}

export function useLlmSettings() {
  const loading = ref(false)
  const saving = ref(false)
  const testing = ref(false)
  const discovering = ref(false)
  const testStatus = ref<{ state: LlmTestState; message: string }>({
    state: 'idle',
    message: '未测试',
  })
  const { showNotice } = usePageNotice()

  const providerOptions = ref<LlmProviderOption[]>([])
  const selectedProviderKey = ref('')
  const baseUrlInput = ref('')
  const selectedModel = ref('')
  const discoveredModels = ref<string[]>([])
  const modelDiscoveryHint = ref('')
  const apiKeyInput = ref('')
  const apiKeyMasked = ref('')
  const maxTokens = ref<number | undefined>(undefined)
  const thinkingDepth = ref<string | undefined>(undefined)
  const changeTrackingReady = ref(false)
  const lastConfirmedDraft = ref('')
  const discoveredModelScope = ref<{ providerKey: string; baseUrl: string }>({
    providerKey: '',
    baseUrl: '',
  })

  // scope 快照：loadSettings 成功后记录，用于判断「表单 scope 是否相对已保存配置变化」。
  const initialScope = ref<{ providerKey: string; baseUrl: string }>({
    providerKey: '',
    baseUrl: '',
  })

  const currentProvider = computed(
    () =>
      providerOptions.value.find((item) => item.providerKey === selectedProviderKey.value) ?? null,
  )

  const isOpenAiCompatible = computed(
    () => selectedProviderKey.value === OPENAI_COMPATIBLE_PROVIDER,
  )

  const modelOptions = computed(() => {
    if (!isOpenAiCompatible.value) {
      return currentProvider.value?.models ?? []
    }
    return discoveredModels.value
  })

  function currentDraftSignature(): string {
    return JSON.stringify({
      providerKey: selectedProviderKey.value,
      baseUrl: normalizeBaseUrl(baseUrlInput.value),
      model: selectedModel.value,
      apiKey: apiKeyInput.value,
      maxTokens: maxTokens.value ?? null,
      thinkingDepth: thinkingDepth.value ?? null,
    })
  }

  function markCurrentDraftConfirmed() {
    lastConfirmedDraft.value = currentDraftSignature()
  }

  function currentModelDiscoveryScope() {
    return {
      providerKey: selectedProviderKey.value,
      baseUrl: normalizeBaseUrl(baseUrlInput.value),
    }
  }

  function clearDiscoveredModels() {
    discoveredModels.value = []
    modelDiscoveryHint.value = ''
    discoveredModelScope.value = { providerKey: '', baseUrl: '' }
  }

  function isScopeChanged(): boolean {
    const providerChanged = selectedProviderKey.value !== initialScope.value.providerKey
    if (!isOpenAiCompatible.value) {
      return providerChanged
    }
    return (
      providerChanged ||
      normalizeBaseUrl(baseUrlInput.value) !== normalizeBaseUrl(initialScope.value.baseUrl)
    )
  }

  function applySelection(providerKey: string, model: string) {
    selectedProviderKey.value = providerKey
    selectedModel.value = model
  }

  async function loadSettings() {
    loading.value = true
    changeTrackingReady.value = false
    try {
      const [providers, config] = await Promise.all([fetchProviders(), fetchUserLlmConfig()])
      providerOptions.value = providers

      const providerKey = config.providerKey || providers[0]?.providerKey || ''
      const provider =
        providers.find((item) => item.providerKey === providerKey) ?? providers[0] ?? null
      applySelection(provider?.providerKey || '', config.model || '')
      baseUrlInput.value = config.baseUrl || ''
      apiKeyMasked.value = config.apiKeyMasked || ''
      maxTokens.value = config.maxTokens ?? undefined
      thinkingDepth.value = config.thinkingDepth ?? undefined
      initialScope.value = { providerKey: selectedProviderKey.value, baseUrl: baseUrlInput.value }
      markCurrentDraftConfirmed()
      testStatus.value = { state: 'idle', message: '未测试' }
      changeTrackingReady.value = true
      showNotice('配置已加载', 'success')
    } catch (error) {
      showNotice(getErrorMessage(error), 'error')
      changeTrackingReady.value = true
    } finally {
      loading.value = false
    }
  }

  async function saveSettings() {
    if (!selectedProviderKey.value || !selectedModel.value) {
      showNotice('请选择接入方式和模型', 'warning')
      return
    }
    if (isOpenAiCompatible.value && !baseUrlInput.value.trim()) {
      showNotice('请填写 Base URL', 'warning')
      return
    }

    saving.value = true
    const hadSavedApiKey = apiKeyMasked.value !== ''
    const scopeChangedBeforeSave = isScopeChanged()
    const hasNewApiKey = apiKeyInput.value.trim() !== ''

    try {
      const result = await withMinDelay(
        saveUserLlmConfig({
          providerKey: selectedProviderKey.value,
          baseUrl: isOpenAiCompatible.value ? baseUrlInput.value.trim() : undefined,
          model: selectedModel.value,
          apiKey: apiKeyInput.value === '' ? undefined : apiKeyInput.value,
          maxTokens: maxTokens.value ?? undefined,
          thinkingDepth: thinkingDepth.value ?? undefined,
        }),
      )

      changeTrackingReady.value = false
      selectedProviderKey.value = result.providerKey || selectedProviderKey.value
      baseUrlInput.value = result.baseUrl || baseUrlInput.value
      selectedModel.value = result.model || selectedModel.value
      apiKeyMasked.value = result.apiKeyMasked || ''
      maxTokens.value = result.maxTokens ?? undefined
      thinkingDepth.value = result.thinkingDepth ?? undefined
      initialScope.value = { providerKey: selectedProviderKey.value, baseUrl: baseUrlInput.value }
      if (apiKeyInput.value && !result.apiKeyMasked) {
        apiKeyInput.value = ''
        markCurrentDraftConfirmed()
        testStatus.value = { state: 'idle', message: '未测试' }
        changeTrackingReady.value = true
        showNotice('配置已保存，但接口未返回脱敏 Key', 'warning')
        return
      }
      apiKeyInput.value = ''
      markCurrentDraftConfirmed()
      testStatus.value = { state: 'idle', message: '未测试' }
      changeTrackingReady.value = true
      if (scopeChangedBeforeSave && !hasNewApiKey && hadSavedApiKey && !result.apiKeyMasked) {
        showNotice('接入方式或 Base URL 已变更，旧 API Key 已清空，请重新填写后测试。', 'warning')
        return
      }
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
      const result = await withMinDelay(
        saveUserLlmConfig({
          providerKey: selectedProviderKey.value,
          baseUrl: isOpenAiCompatible.value ? baseUrlInput.value.trim() : undefined,
          model: selectedModel.value,
          apiKey: '__CLEAR__',
        }),
      )
      changeTrackingReady.value = false
      apiKeyMasked.value = result.apiKeyMasked || ''
      apiKeyInput.value = ''
      markCurrentDraftConfirmed()
      testStatus.value = { state: 'idle', message: '未测试' }
      changeTrackingReady.value = true
      showNotice('API Key 已清除', 'success')
    } catch (error) {
      showNotice(getErrorMessage(error), 'error')
    } finally {
      saving.value = false
    }
  }

  async function testSettings() {
    testing.value = true
    testStatus.value = { state: 'testing', message: '测试中…' }
    try {
      const result = await withMinDelay(
        testUserLlmConfig({
          providerKey: selectedProviderKey.value,
          baseUrl: isOpenAiCompatible.value ? baseUrlInput.value.trim() : undefined,
          model: selectedModel.value,
          apiKey: apiKeyInput.value === '' ? undefined : apiKeyInput.value,
          maxTokens: maxTokens.value ?? undefined,
          thinkingDepth: thinkingDepth.value ?? undefined,
        }),
      )
      const message = result.message || '模型配置测试通过'
      markCurrentDraftConfirmed()
      testStatus.value = { state: result.ok ? 'success' : 'error', message }
      showNotice(message, result.ok ? 'success' : 'warning')
    } catch (error) {
      const message = getErrorMessage(error)
      markCurrentDraftConfirmed()
      testStatus.value = { state: 'error', message }
      showNotice(message, 'error')
    } finally {
      testing.value = false
    }
  }

  async function discoverModels() {
    if (!baseUrlInput.value.trim()) {
      showNotice('请填写 Base URL', 'warning')
      return
    }
    // Key 选择规则：新 Key > 同 scope 已保存 Key > 否则提示重新填 Key。
    const hasNewKey = apiKeyInput.value.trim() !== ''
    if (!hasNewKey) {
      const scopeChanged = isScopeChanged()
      const hasSavedKey = apiKeyMasked.value !== ''
      if (scopeChanged || !hasSavedKey) {
        showNotice('更换接入方式或 Base URL 后，请重新填写 API Key 再检测。', 'warning')
        return
      }
    }

    discovering.value = true
    try {
      const result = await withMinDelay(
        discoverLlmModels({
          baseUrl: baseUrlInput.value.trim(),
          apiKey: hasNewKey ? apiKeyInput.value.trim() : undefined,
        }),
      )
      baseUrlInput.value = result.baseUrl
      discoveredModels.value = result.models
      discoveredModelScope.value = {
        providerKey: OPENAI_COMPATIBLE_PROVIDER,
        baseUrl: normalizeBaseUrl(result.baseUrl),
      }
      modelDiscoveryHint.value =
        result.models.length === 0 ? '未能读取模型列表，可手动填写模型 ID。' : ''
      showNotice(
        result.models.length > 0 ? '模型列表已更新' : modelDiscoveryHint.value,
        result.models.length > 0 ? 'success' : 'warning',
      )
    } catch (error) {
      modelDiscoveryHint.value = '未能读取模型列表，可手动填写模型 ID。'
      showNotice(getErrorMessage(error), 'error')
    } finally {
      discovering.value = false
    }
  }

  watch([selectedProviderKey, baseUrlInput], () => {
    const scope = currentModelDiscoveryScope()
    if (
      scope.providerKey !== discoveredModelScope.value.providerKey ||
      scope.baseUrl !== discoveredModelScope.value.baseUrl
    ) {
      clearDiscoveredModels()
    }
  })

  watch(selectedProviderKey, (providerKey, previousProviderKey) => {
    if (!changeTrackingReady.value || providerKey === previousProviderKey) {
      return
    }
    selectedModel.value = ''
  })

  watch(
    [selectedProviderKey, baseUrlInput, selectedModel, apiKeyInput, maxTokens, thinkingDepth],
    () => {
      if (!changeTrackingReady.value || testStatus.value.state === 'testing') {
        return
      }
      if (currentDraftSignature() !== lastConfirmedDraft.value) {
        testStatus.value = { state: 'idle', message: '配置已变更，建议重新测试' }
      }
    },
    { flush: 'sync' },
  )

  return {
    loading,
    saving,
    testing,
    discovering,
    testStatus,
    providerOptions,
    selectedProviderKey,
    baseUrlInput,
    selectedModel,
    modelDiscoveryHint,
    apiKeyInput,
    apiKeyMasked,
    maxTokens,
    thinkingDepth,
    currentProvider,
    modelOptions,
    isOpenAiCompatible,
    loadSettings,
    saveSettings,
    clearApiKey,
    testSettings,
    discoverModels,
  }
}
