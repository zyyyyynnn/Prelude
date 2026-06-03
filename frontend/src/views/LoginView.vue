<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { login as loginRequest, register as registerRequest } from '../api/auth'
import BrandMetaballs from '../components/BrandMetaballs.vue'
import { usePageNotice } from '../composables/usePageNotice'
import { useAuthStore } from '../stores/auth'
import { getErrorMessage } from '../utils/errors'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Eye, EyeOff, Loader2 } from 'lucide-vue-next'
import {
  FormControl,
  FormField,
  FormItem,
  FormLabel,
  FormMessage,
} from '@/components/ui/form'
import { useForm } from 'vee-validate'
import { toTypedSchema } from '@vee-validate/zod'
import { loginSchema, registerSchema } from '../schemas/auth'

type AuthMode = 'login' | 'register'

const router = useRouter()
const route = useRoute()
const authStore = useAuthStore()
const { showNotice } = usePageNotice()

const authMode = ref<AuthMode>('login')
const loading = ref(false)
const showPassword = ref(false)

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

const currentSchema = computed(() => isRegisterMode.value ? toTypedSchema(registerSchema) : toTypedSchema(loginSchema))

const { handleSubmit, setValues, resetForm } = useForm({
  validationSchema: currentSchema,
})

function switchMode(mode: AuthMode) {
  authMode.value = mode
  resetForm()
}

const submitAuth = handleSubmit(async (values) => {
  loading.value = true

  try {
    if (isRegisterMode.value) {
      const v = values as any;
      await registerRequest(v.username.trim(), v.password, v.email?.trim() || undefined)
      showNotice('注册成功，请继续登录。', 'success')
      authMode.value = 'login'
      setValues({ username: v.username })
    } else {
      const response = await loginRequest(values.username.trim(), values.password)
      authStore.setToken(response.token)
      await router.replace(redirectTarget.value)
    }
  } catch (error) {
    showNotice(getErrorMessage(error), 'error')
  } finally {
    loading.value = false
  }
})

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
          <BrandMetaballs class="login-card__logo" />
          <p class="login-card__brand-caption">AI Mock Interview</p>
        </aside>

        <div class="login-card__form-panel">
          <div class="page__header login-card__header">
            <p class="eyebrow">{{ authEyebrow }}</p>
            <h2 class="page__title">{{ authTitle }}</h2>
          </div>

          <div class="auth-switch" role="tablist" aria-label="认证模式">
            <button
              :class="['auth-switch__item', { 'is-active': !isRegisterMode }]"
              type="button"
              role="tab"
              :aria-selected="!isRegisterMode"
              @click="switchMode('login')"
            >
              登录
            </button>
            <button
              :class="['auth-switch__item', { 'is-active': isRegisterMode }]"
              type="button"
              role="tab"
              :aria-selected="isRegisterMode"
              @click="switchMode('register')"
            >
              注册
            </button>
          </div>

          <form class="flex flex-col gap-lg w-full" @submit.prevent="submitAuth">
            <FormField name="username" v-slot="{ componentField }">
              <FormItem class="flex flex-col gap-2">
                <FormLabel>用户名</FormLabel>
                <FormControl>
                  <Input
                    v-bind="componentField"
                    autocomplete="username"
                    placeholder="请输入用户名"
                  />
                </FormControl>
                <FormMessage />
              </FormItem>
            </FormField>

            <FormField name="password" v-slot="{ componentField }">
              <FormItem class="flex flex-col gap-2 relative">
                <FormLabel>密码</FormLabel>
                <FormControl>
                  <div class="relative w-full">
                    <Input
                      v-bind="componentField"
                      autocomplete="current-password"
                      placeholder="请输入密码"
                      :type="showPassword ? 'text' : 'password'"
                      class="pr-10"
                    />
                    <button
                      type="button"
                      class="absolute right-0 top-0 h-full px-3 py-2 hover:bg-transparent text-muted-foreground flex items-center justify-center"
                      @click="showPassword = !showPassword"
                    >
                      <Eye v-if="showPassword" class="h-4 w-4" />
                      <EyeOff v-else class="h-4 w-4" />
                    </button>
                  </div>
                </FormControl>
                <FormMessage />
              </FormItem>
            </FormField>

            <div
              :class="['transition-all duration-300', { 'hidden': !isRegisterMode }]"
              :aria-hidden="!isRegisterMode"
            >
              <FormField name="email" v-slot="{ componentField }">
                <FormItem class="flex flex-col gap-2">
                  <FormLabel>邮箱</FormLabel>
                  <FormControl>
                    <Input
                      v-bind="componentField"
                      autocomplete="email"
                      :disabled="!isRegisterMode"
                      placeholder="请输入邮箱"
                    />
                  </FormControl>
                  <FormMessage />
                </FormItem>
              </FormField>
            </div>

            <div class="pt-2">
              <Button
                type="submit"
                class="w-full"
                :disabled="loading"
              >
                <Loader2 v-if="loading" class="w-4 h-4 mr-2 animate-spin" />
                {{ submitLabel }}
              </Button>
            </div>
          </form>
        </div>
      </div>
    </div>
  </section>
</template>
