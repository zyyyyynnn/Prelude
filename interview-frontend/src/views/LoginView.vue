<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElButton, ElForm, ElFormItem, ElInput } from 'element-plus'
import { login as loginRequest, register as registerRequest } from '../api/auth'
import projectLogo from '../assets/brand-logo.png'
import { usePageNotice } from '../composables/usePageNotice'
import { useAuthStore } from '../stores/auth'

type AuthMode = 'login' | 'register'

const router = useRouter()
const route = useRoute()
const authStore = useAuthStore()
const { showNotice } = usePageNotice()

const authMode = ref<AuthMode>('login')
const loading = ref(false)
const form = reactive({
  username: '',
  password: '',
  email: '',
})

const redirectTarget = computed(() => {
  return typeof route.query.redirect === 'string' && route.query.redirect
    ? route.query.redirect
    : '/interview'
})

const expiredNotice = computed(() => route.query.reason === 'expired')
const isRegisterMode = computed(() => authMode.value === 'register')
const authEyebrow = computed(() => (isRegisterMode.value ? '注册' : '登录'))
const authTitle = computed(() => (isRegisterMode.value ? '创建工作台账号' : '进入面试工作台'))
const submitLabel = computed(() => (isRegisterMode.value ? '完成注册' : '登录'))
const switchLabel = computed(() => (isRegisterMode.value ? '登录' : '注册'))

function getErrorMessage(error: unknown) {
  if (error instanceof Error) {
    return error.message
  }
  return '请求失败'
}

function switchMode(mode: AuthMode) {
  authMode.value = mode
}

async function handleLogin() {
  loading.value = true

  try {
    const response = await loginRequest(form.username.trim(), form.password)
    authStore.setToken(response.token)
    await router.replace(redirectTarget.value)
  } catch (error) {
    showNotice(getErrorMessage(error), 'error')
  } finally {
    loading.value = false
  }
}

async function handleRegister() {
  loading.value = true

  try {
    await registerRequest(form.username.trim(), form.password, form.email.trim() || undefined)
    showNotice('注册成功，请继续登录。', 'success')
    authMode.value = 'login'
  } catch (error) {
    showNotice(getErrorMessage(error), 'error')
  } finally {
    loading.value = false
  }
}

async function submitAuth() {
  if (isRegisterMode.value) {
    await handleRegister()
    return
  }

  await handleLogin()
}

onMounted(() => {
  if (expiredNotice.value) {
    showNotice('登录已失效，请重新登录。', 'warning')
  }
})
</script>

<template>
  <section class="page page--center page--auth">
    <div class="login-card">
      <svg
        class="login-card-border"
        aria-hidden="true"
        focusable="false"
      >
        <rect
          class="login-card-border__inner"
          x="0.5"
          y="0.5"
          width="100%"
          height="100%"
          rx="8"
          ry="8"
          fill="none"
          stroke="currentColor"
          stroke-width="0.75"
          stroke-dasharray="4 4"
        />
      </svg>

      <div class="login-card__content">
        <aside class="login-card__brand-panel">
          <div class="login-card__brand">
            <span class="login-card__brand-mark">I</span>
            <div class="login-card__brand-copy">
              <p class="login-card__brand-eyebrow">INTERVIEW PLATFORM</p>
              <h1 class="login-card__brand-title">模拟面试系统</h1>
            </div>
          </div>

          <div class="login-card__logo-slot" aria-hidden="true">
            <img class="login-card__logo-image" :src="projectLogo" alt="" />
          </div>
        </aside>

        <div class="login-card__form-panel">
          <div class="page__header login-card__header">
            <p class="eyebrow">{{ authEyebrow }}</p>
            <h2 class="page__title">{{ authTitle }}</h2>
          </div>

          <ElForm class="form-grid" label-position="top" @submit.prevent="submitAuth">
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

            <div
              :class="['login-card__email-slot', { 'is-hidden': !isRegisterMode }]"
              :aria-hidden="!isRegisterMode"
            >
              <ElFormItem label="邮箱">
                <ElInput
                  v-model="form.email"
                  class="ui-input"
                  :disabled="!isRegisterMode"
                  autocomplete="email"
                  placeholder="请输入邮箱"
                  size="large"
                />
              </ElFormItem>
            </div>

            <div class="button-row">
              <ElButton
                class="ui-button ui-button--primary"
                :loading="loading"
                native-type="submit"
                size="large"
                type="primary"
              >
                {{ submitLabel }}
              </ElButton>
              <ElButton
                class="ui-button ui-button--secondary"
                :disabled="loading"
                size="large"
                @click="switchMode(isRegisterMode ? 'login' : 'register')"
              >
                {{ switchLabel }}
              </ElButton>
            </div>
          </ElForm>
        </div>
      </div>
    </div>
  </section>
</template>
