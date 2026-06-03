<script setup lang="ts">
import { onMounted } from 'vue'
import {
  ElButton,
  ElForm,
  ElFormItem,
  ElInput,
  ElOption,
  ElSelect,
} from 'element-plus'
import { useLlmSettings } from '../../composables/useLlmSettings'
import { usePopperMatchTrigger } from '../../composables/usePopperMatchTrigger'

const {
  loading, saving, testing,
  providerOptions, selectedProviderKey, selectedModel,
  apiKeyInput, apiKeyMasked, maxTokens, thinkingDepth,
  modelOptions,
  loadSettings, saveSettings, clearApiKey, testSettings,
} = useLlmSettings()

const providerSelect = usePopperMatchTrigger()
const modelSelect = usePopperMatchTrigger()
const maxTokensSelect = usePopperMatchTrigger()
const thinkingDepthSelect = usePopperMatchTrigger()

onMounted(() => {
  void loadSettings()
})
</script>

<template>
  <div class="panel-content-wrapper">
    <ElForm class="form-grid" label-position="top" @submit.prevent>


    <div class="field-grid">
      <ElFormItem label="Provider">
        <ElSelect
          v-model="selectedProviderKey"
          class="ui-select full-width"
          popper-class="custom-select-popper"
          placeholder="请选择 Provider"
          fit-input-width
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
          class="ui-select full-width"
          popper-class="custom-select-popper"
          :disabled="modelOptions.length === 0"
          placeholder="请选择模型"
          fit-input-width
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
            class="icon-action-btn clear-key-btn"
            @click="clearApiKey"
            title="清除密钥"
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

    <div class="form-section">
      <div class="form-section__title">高级设置</div>
        <div class="advanced-grid">
          <ElFormItem label="最大回复长度 (Max Tokens)">
            <ElSelect
              v-model="maxTokens"
              class="ui-select full-width"
              popper-class="custom-select-popper"
              placeholder="留空使用模型默认"
              filterable
              allow-create
              clearable
              fit-input-width
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
              class="ui-select full-width"
              popper-class="custom-select-popper"
              placeholder="默认 (Default)"
              clearable
              fit-input-width
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
    </div>

    <div class="button-row">
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
.full-width {
  width: 100%;
}
.clear-key-btn {
  cursor: pointer;
  display: flex;
  align-items: center;
  color: var(--color-text-tertiary);
}
</style>
