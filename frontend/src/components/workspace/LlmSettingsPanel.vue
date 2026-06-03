<script setup lang="ts">
import { onMounted, ref, watch } from 'vue'
import { useLlmSettings } from '../../composables/useLlmSettings'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select'
import { Eye, EyeOff, Loader2, Trash2 } from 'lucide-vue-next'
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

const {
  loading, saving, testing,
  providerOptions, selectedProviderKey, selectedModel,
  apiKeyInput, apiKeyMasked, maxTokens, thinkingDepth,
  modelOptions,
  loadSettings, saveSettings, clearApiKey, testSettings,
} = useLlmSettings()

const showApiKey = ref(false)

const { handleSubmit, setValues } = useForm({
  validationSchema: toTypedSchema(llmSettingsSchema),
})

watch([selectedProviderKey, selectedModel, apiKeyInput, maxTokens, thinkingDepth], () => {
  setValues({
    providerKey: selectedProviderKey.value,
    model: selectedModel.value,
    apiKey: apiKeyInput.value,
    maxTokens: maxTokens.value,
    thinkingDepth: thinkingDepth.value,
  })
}, { immediate: true })

const onSubmit = handleSubmit(async () => {
  await saveSettings()
})

onMounted(() => {
  void loadSettings()
})
</script>

<template>
  <div class="panel-content-wrapper">
    <form class="flex flex-col gap-6" @submit.prevent="onSubmit">

      <div class="field-grid">
        <FormField name="providerKey" v-slot="{ componentField }">
          <FormItem class="flex flex-col gap-2">
            <FormLabel>Provider</FormLabel>
            <Select v-model="selectedProviderKey" v-bind="componentField">
              <SelectTrigger>
                <SelectValue placeholder="请选择 Provider" />
              </SelectTrigger>
              <SelectContent>
                <SelectItem
                  v-for="provider in providerOptions"
                  :key="provider.providerKey"
                  :value="provider.providerKey"
                >
                  {{ provider.displayName }}
                </SelectItem>
              </SelectContent>
            </Select>
            <FormMessage />
          </FormItem>
        </FormField>

        <FormField name="model" v-slot="{ componentField }">
          <FormItem class="flex flex-col gap-2">
            <FormLabel>模型</FormLabel>
            <Select v-model="selectedModel" :disabled="modelOptions.length === 0" v-bind="componentField">
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
            <FormMessage />
          </FormItem>
        </FormField>
      </div>

      <FormField name="apiKey" v-slot="{ componentField }">
        <FormItem class="flex flex-col gap-2 relative">
          <FormLabel>新 API Key / 清空</FormLabel>
          <div class="relative w-full flex items-center">
            <FormControl>
              <Input
                v-model="apiKeyInput"
                v-bind="componentField"
                autocomplete="off"
                placeholder="留空表示清空当前用户 Key"
                :type="showApiKey ? 'text' : 'password'"
                class="pr-20"
              />
            </FormControl>
            <div class="absolute right-0 top-0 h-full flex items-center pr-1">
              <button
                v-if="apiKeyMasked"
                type="button"
                class="px-2 py-2 hover:bg-transparent text-muted-foreground hover:text-destructive flex items-center justify-center transition-colors"
                @click="clearApiKey"
                title="清除密钥"
              >
                <Trash2 class="h-4 w-4" />
              </button>
              <button
                type="button"
                class="px-2 py-2 hover:bg-transparent text-muted-foreground flex items-center justify-center"
                @click="showApiKey = !showApiKey"
              >
                <Eye v-if="showApiKey" class="h-4 w-4" />
                <EyeOff v-else class="h-4 w-4" />
              </button>
            </div>
          </div>
          <FormMessage />
        </FormItem>
      </FormField>

      <div class="form-section">
        <div class="form-section__title">高级设置</div>
        <div class="advanced-grid">
          <FormField name="maxTokens" v-slot="{ componentField }">
            <FormItem class="flex flex-col gap-2">
              <FormLabel>最大回复长度 (Max Tokens)</FormLabel>
              <FormControl>
                <Input
                  v-model="maxTokens"
                  v-bind="componentField"
                  type="number"
                  placeholder="留空使用模型默认 (如 8192 或 32768)"
                />
              </FormControl>
              <FormMessage />
            </FormItem>
          </FormField>

          <FormField name="thinkingDepth" v-slot="{ componentField }">
            <FormItem class="flex flex-col gap-2">
              <FormLabel>思考深度 (Thinking Depth)</FormLabel>
              <Select v-model="thinkingDepth" v-bind="componentField">
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
              <FormMessage />
            </FormItem>
          </FormField>
        </div>
      </div>

      <div class="button-row flex gap-3 mt-4">
        <Button
          type="submit"
          :disabled="saving"
        >
          <Loader2 v-if="saving" class="w-4 h-4 mr-2 animate-spin" />
          保存设置
        </Button>
        <Button
          type="button"
          variant="secondary"
          :disabled="saving || loading || testing"
          @click="testSettings"
        >
          <Loader2 v-if="testing" class="w-4 h-4 mr-2 animate-spin" />
          测试连接
        </Button>
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
</style>
