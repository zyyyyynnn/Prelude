import { computed, onMounted, ref, watch, type Ref } from 'vue'
import { fetchProviders, fetchUserLlmConfig, type LlmProviderOption } from '@/features/settings'

type ComposerModelOptions = {
  activeSessionId: Ref<number | null | undefined>
  llmProvider: Ref<string | undefined>
  llmModel: Ref<string | undefined>
}

function providerDisplayName(provider: LlmProviderOption | undefined, providerKey = '') {
  return provider?.displayName || providerKey || '未配置'
}

export function useComposerModelOptions(options: ComposerModelOptions) {
  const providerModels = ref<string[]>([])
  const providerOptions = ref<LlmProviderOption[]>([])
  const currentProviderName = ref('')
  const selectedComposerModel = ref('')
  const canSelectModel = computed(() => providerModels.value.length > 0)

  async function load() {
    try {
      const [providers, config] = await Promise.all([fetchProviders(), fetchUserLlmConfig()])
      providerOptions.value = providers
      const providerKey = options.activeSessionId.value
        ? options.llmProvider.value || config.providerKey
        : config.providerKey
      const provider = providers.find((item) => item.providerKey === providerKey)
      const sessionModel = options.activeSessionId.value ? options.llmModel.value : ''
      const models = provider?.models.length
        ? [...provider.models]
        : config.model
          ? [config.model]
          : []
      if (sessionModel && !models.includes(sessionModel)) models.unshift(sessionModel)
      currentProviderName.value = providerDisplayName(provider, providerKey)
      providerModels.value = models
      selectedComposerModel.value = sessionModel || config.model || models[0] || ''
    } catch {
      providerModels.value = options.llmModel.value ? [options.llmModel.value] : []
      selectedComposerModel.value = options.llmModel.value || ''
      currentProviderName.value = options.llmProvider.value || '未配置'
    }
  }

  watch(
    [options.activeSessionId, options.llmModel, options.llmProvider],
    ([activeSessionId, llmModel, llmProvider]) => {
      if (!activeSessionId) return
      if (llmModel) {
        selectedComposerModel.value = llmModel
        if (!providerModels.value.includes(llmModel)) {
          providerModels.value = [llmModel, ...providerModels.value]
        }
      }
      if (llmProvider) {
        const provider = providerOptions.value.find((item) => item.providerKey === llmProvider)
        currentProviderName.value = providerDisplayName(provider, llmProvider)
      }
    },
    { immediate: true },
  )

  onMounted(() => void load())

  return { providerModels, currentProviderName, selectedComposerModel, canSelectModel }
}
