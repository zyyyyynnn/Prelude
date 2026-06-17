<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { login as loginRequest, register as registerRequest } from '../api/auth'
import BrandMetaballs from '../components/BrandMetaballs.vue'
import { usePageNotice } from '../composables/usePageNotice'
import { useAuthStore } from '../stores/auth'
import { getErrorMessage } from '../utils/errors'
import { withMinDelay } from '../lib/utils'
import SegmentedControl from '../components/ui/segmented-control/SegmentedControl.vue'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Eye, EyeOff } from '@lucide/vue'
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
      await withMinDelay(registerRequest(v.username.trim(), v.password, v.email?.trim() || undefined))
      showNotice('注册成功，请继续登录。', 'success')
      authMode.value = 'login'
      setValues({ username: v.username })
    } else {
      const response = await withMinDelay(loginRequest(values.username.trim(), values.password))
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

          <div class="mb-6">
            <SegmentedControl
              :items="['登录', '注册']"
              :model-value="isRegisterMode ? '注册' : '登录'"
              @update:model-value="(val) => switchMode(val === '注册' ? 'register' : 'login')"
            />
          </div>

          <form class="flex flex-col gap-lg w-full" @submit.prevent="submitAuth">
            <FormField name="username" v-slot="{ componentField }">
              <FormItem>
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
              <FormItem class="relative">
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
                      :aria-label="showPassword ? '隐藏密码' : '显示密码'"
                      class="absolute right-0 top-0 h-full px-3 py-2 hover:bg-transparent text-muted-foreground flex items-center justify-center focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-focus"
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
              :class="['transition-opacity [transition-duration:var(--motion-duration-base)] [transition-timing-function:var(--motion-ease-standard)]', isRegisterMode ? 'opacity-100 pointer-events-auto' : 'opacity-0 pointer-events-none']"
              :aria-hidden="!isRegisterMode"
            >
              <FormField name="email" v-slot="{ componentField }">
                <FormItem>
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
                class="w-full font-serif"
                :disabled="loading"
                :loading="loading"
              >
                {{ submitLabel }}
              </Button>
            </div>
          </form>
        </div>
      </div>
    </div>
  </section>
</template>
