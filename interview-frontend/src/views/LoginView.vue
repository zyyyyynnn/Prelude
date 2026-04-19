<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElAlert, ElButton, ElCard, ElForm, ElFormItem, ElInput } from 'element-plus'
import { login as loginRequest, register as registerRequest } from '../api/auth'
import { useAuthStore } from '../stores/auth'

const router = useRouter()
const route = useRoute()
const authStore = useAuthStore()

const loading = ref(false)
const notice = ref('')
const form = reactive({
  username: '',
  password: '',
})

const redirectTarget = computed(() => {
  return typeof route.query.redirect === 'string' && route.query.redirect
    ? route.query.redirect
    : '/interview'
})

const expiredNotice = computed(() => route.query.reason === 'expired')

function getErrorMessage(error: unknown) {
  if (error instanceof Error) {
    return error.message
  }
  return '请求失败'
}

async function handleLogin() {
  loading.value = true
  notice.value = ''

  try {
    const response = await loginRequest(form.username.trim(), form.password)
    authStore.setToken(response.token)
    await router.replace(redirectTarget.value)
  } catch (error) {
    notice.value = getErrorMessage(error)
  } finally {
    loading.value = false
  }
}

async function handleRegister() {
  loading.value = true
  notice.value = ''

  try {
    await registerRequest(form.username.trim(), form.password)
    notice.value = '注册成功，请继续登录。'
  } catch (error) {
    notice.value = getErrorMessage(error)
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <section class="page page--center">
    <ElCard class="ui-card auth-card">
      <div class="page__header">
        <p class="eyebrow">登录</p>
        <h2 class="page__title">进入面试工作台</h2>
        <p class="page__lead">使用账号登录后继续面试和 LLM 配置。</p>
      </div>

      <ElAlert
        v-if="expiredNotice"
        class="status-banner"
        type="warning"
        :closable="false"
        title="登录已失效，请重新登录。"
      />

      <ElAlert
        v-if="notice"
        class="status-banner"
        :closable="false"
        :type="notice.includes('成功') ? 'success' : 'error'"
        :title="notice"
      />

      <ElForm class="form-grid" label-position="top" @submit.prevent>
        <ElFormItem label="用户名">
          <ElInput
            v-model="form.username"
            class="ui-input"
            autocomplete="username"
            placeholder="请输入用户名"
            size="large"
          />
        </ElFormItem>

        <ElFormItem label="密码">
          <ElInput
            v-model="form.password"
            class="ui-input"
            autocomplete="current-password"
            placeholder="请输入密码"
            show-password
            type="password"
            size="large"
          />
        </ElFormItem>

        <div class="button-row">
          <ElButton
            class="ui-button ui-button--primary"
            :loading="loading"
            size="large"
            type="primary"
            @click="handleLogin"
          >
            登录
          </ElButton>
          <ElButton
            class="ui-button ui-button--secondary"
            :loading="loading"
            size="large"
            @click="handleRegister"
          >
            注册
          </ElButton>
        </div>
      </ElForm>
    </ElCard>
  </section>
</template>
