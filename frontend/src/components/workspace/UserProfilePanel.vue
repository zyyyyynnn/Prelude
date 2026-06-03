<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { usePageNotice } from '../../composables/usePageNotice'
import { fetchUserProfile, updateUserProfile } from '../../api/user'
import { getErrorMessage } from '../../utils/errors'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Eye, EyeOff, Loader2 } from 'lucide-vue-next'

const loading = ref(false)
const saving = ref(false)
const { showNotice } = usePageNotice()
const initialEmail = ref('')
const profile = reactive({
  username: '',
  email: '',
  oldPassword: '',
  newPassword: '',
})

const showOldPassword = ref(false)
const showNewPassword = ref(false)

const hasPasswordChange = computed(() => Boolean(profile.oldPassword.trim() || profile.newPassword.trim()))

async function loadProfile() {
  loading.value = true
  try {
    const result = await fetchUserProfile()
    profile.username = result.username || ''
    profile.email = result.email || ''
    initialEmail.value = profile.email
  } catch (error) {
    showNotice(getErrorMessage(error), 'error')
  } finally {
    loading.value = false
  }
}

async function saveProfile() {
  const email = profile.email.trim()
  const oldPassword = profile.oldPassword.trim()
  const newPassword = profile.newPassword.trim()
  const emailChanged = email !== initialEmail.value.trim()
  const passwordChanged = Boolean(oldPassword || newPassword)

  if (!emailChanged && !passwordChanged) {
    showNotice('未检测到资料变更', 'warning')
    return
  }

  if (Boolean(oldPassword) !== Boolean(newPassword)) {
    showNotice('修改密码时必须同时填写旧密码和新密码', 'warning')
    return
  }

  if (oldPassword && newPassword && oldPassword === newPassword) {
    showNotice('新密码不能与旧密码相同', 'warning')
    return
  }

  saving.value = true
  try {
    const result = await updateUserProfile({
      email: email || undefined,
      oldPassword: oldPassword || undefined,
      newPassword: newPassword || undefined,
    })
    profile.username = result.username || profile.username
    profile.email = result.email || profile.email
    initialEmail.value = profile.email
    profile.oldPassword = ''
    profile.newPassword = ''
    showNotice('资料已保存', 'success')
  } catch (error) {
    showNotice(getErrorMessage(error), 'error')
  } finally {
    saving.value = false
  }
}

onMounted(() => {
  void loadProfile()
})
</script>

<template>
  <div class="panel-content-wrapper">
    <form class="flex flex-col gap-6" @submit.prevent>
      <div class="field-grid">
        <div class="flex flex-col gap-2">
          <Label>用户名</Label>
          <Input v-model="profile.username" disabled />
        </div>

        <div class="flex flex-col gap-2">
          <Label>邮箱</Label>
          <Input
            v-model="profile.email"
            autocomplete="email"
            placeholder="请输入邮箱"
          />
        </div>
      </div>

      <div class="form-section">
        <div class="form-section__title">修改密码</div>
        <div class="field-grid">
          <div class="flex flex-col gap-2 relative">
            <Label>旧密码</Label>
            <div class="relative w-full">
              <Input
                v-model="profile.oldPassword"
                autocomplete="current-password"
                placeholder="留空表示不修改密码"
                :type="showOldPassword ? 'text' : 'password'"
                class="pr-10"
              />
              <button
                type="button"
                class="absolute right-0 top-0 h-full px-3 py-2 hover:bg-transparent text-muted-foreground flex items-center justify-center"
                @click="showOldPassword = !showOldPassword"
              >
                <Eye v-if="showOldPassword" class="h-4 w-4" />
                <EyeOff v-else class="h-4 w-4" />
              </button>
            </div>
          </div>

          <div class="flex flex-col gap-2 relative">
            <Label>新密码</Label>
            <div class="relative w-full">
              <Input
                v-model="profile.newPassword"
                autocomplete="new-password"
                placeholder="请输入新密码"
                :type="showNewPassword ? 'text' : 'password'"
                class="pr-10"
              />
              <button
                type="button"
                class="absolute right-0 top-0 h-full px-3 py-2 hover:bg-transparent text-muted-foreground flex items-center justify-center"
                @click="showNewPassword = !showNewPassword"
              >
                <Eye v-if="showNewPassword" class="h-4 w-4" />
                <EyeOff v-else class="h-4 w-4" />
              </button>
            </div>
          </div>
        </div>
      </div>

      <div class="button-row flex gap-3 mt-4">
        <Button
          type="submit"
          :disabled="saving || loading"
          @click="saveProfile"
        >
          <Loader2 v-if="saving || loading" class="w-4 h-4 mr-2 animate-spin" />
          保存设置
        </Button>
        <Button
          v-if="hasPasswordChange"
          variant="secondary"
          :disabled="saving"
          @click="profile.oldPassword = ''; profile.newPassword = ''"
        >
          清空密码输入
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
</style>
