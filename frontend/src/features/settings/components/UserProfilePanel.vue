<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { usePageNotice } from '@/composables/usePageNotice'
import { fetchUserProfile, updateUserProfile, uploadUserAvatar } from '../api/user'
import { getErrorMessage } from '@/utils/errors'
import { withMinDelay } from '@/lib/utils'
import { Input } from '@/components/ui/input'
import { Button } from '@/components/ui/button'
import { Label } from '@/components/ui/label'
import { Eye, EyeOff } from '@lucide/vue'

const loading = ref(false)
const saving = ref(false)
const uploadingAvatar = ref(false)
const avatarInput = ref<HTMLInputElement | null>(null)
const { showNotice } = usePageNotice()
const initial = reactive({
  username: '',
  email: '',
})
const profile = reactive({
  username: '',
  email: '',
  avatarUrl: '',
  oldPassword: '',
  newPassword: '',
})

const showOldPassword = ref(false)
const showNewPassword = ref(false)

const initials = computed(() => (profile.username.trim().slice(0, 1) || 'P').toUpperCase())

async function loadProfile() {
  loading.value = true
  try {
    const result = await withMinDelay(fetchUserProfile())
    profile.username = result.username || ''
    profile.email = result.email || ''
    profile.avatarUrl = result.avatarUrl || ''
    initial.username = profile.username
    initial.email = profile.email
  } catch (error) {
    showNotice(getErrorMessage(error), 'error')
  } finally {
    loading.value = false
  }
}

async function saveProfile() {
  const username = profile.username.trim()
  const email = profile.email.trim()
  const oldPassword = profile.oldPassword.trim()
  const newPassword = profile.newPassword.trim()
  const usernameChanged = username !== initial.username.trim()
  const emailChanged = email !== initial.email.trim()
  const passwordChanged = Boolean(oldPassword || newPassword)

  if (!usernameChanged && !emailChanged && !passwordChanged) {
    showNotice('未检测到资料变更', 'warning')
    return
  }
  if (!username) {
    showNotice('用户名不能为空', 'warning')
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
    const result = await withMinDelay(updateUserProfile({
      username: username || undefined,
      email: email || undefined,
      oldPassword: oldPassword || undefined,
      newPassword: newPassword || undefined,
    }))
    profile.username = result.username || profile.username
    profile.email = result.email || profile.email
    profile.avatarUrl = result.avatarUrl || profile.avatarUrl
    initial.username = profile.username
    initial.email = profile.email
    profile.oldPassword = ''
    profile.newPassword = ''
    showNotice('资料已保存', 'success')
  } catch (error) {
    showNotice(getErrorMessage(error), 'error')
  } finally {
    saving.value = false
  }
}

async function handleAvatarChange(event: Event) {
  const file = (event.target as HTMLInputElement).files?.[0]
  ;(event.target as HTMLInputElement).value = ''
  if (!file) return
  uploadingAvatar.value = true
  try {
    const result = await withMinDelay(uploadUserAvatar(file))
    profile.avatarUrl = result.avatarUrl || ''
    showNotice('头像已更新', 'success')
  } catch (error) {
    showNotice(getErrorMessage(error), 'error')
  } finally {
    uploadingAvatar.value = false
  }
}

onMounted(() => {
  void loadProfile()
})

defineExpose({ submit: saveProfile, saving, loading })
</script>

<template>
  <div class="panel-content-wrapper">
    <form class="flex flex-col gap-6" @submit.prevent>
      <section class="profile-avatar-row">
        <div class="profile-avatar">
          <img v-if="profile.avatarUrl" :src="profile.avatarUrl" alt="" class="profile-avatar__image">
          <span v-else>{{ initials }}</span>
        </div>
        <div class="profile-avatar__actions">
          <input
            ref="avatarInput"
            class="upload-field__native"
            type="file"
            accept="image/png,image/jpeg,image/webp,image/gif"
            @change="handleAvatarChange"
          >
          <Button type="button" variant="secondary" size="sm" :loading="uploadingAvatar" @click="avatarInput?.click()">
            上传头像
          </Button>
        </div>
      </section>

      <div class="field-grid">
        <div class="flex flex-col gap-2">
          <Label>用户名</Label>
          <Input v-model="profile.username" autocomplete="username" placeholder="请输入用户名" />
        </div>

        <div class="flex flex-col gap-2">
          <Label>邮箱</Label>
          <Input v-model="profile.email" autocomplete="email" placeholder="请输入邮箱" />
        </div>
      </div>

      <div class="form-section">
        <div class="form-section__title">修改密码</div>
        <div class="field-grid">
          <div class="flex flex-col gap-2 relative">
            <Label>旧密码</Label>
            <div class="relative w-full">
              <Input v-model="profile.oldPassword" autocomplete="current-password" placeholder="留空表示不修改密码" :type="showOldPassword ? 'text' : 'password'" class="pr-10" />
              <button type="button" class="password-toggle" aria-label="切换旧密码可见性" @click="showOldPassword = !showOldPassword">
                <Eye v-if="showOldPassword" class="h-4 w-4" />
                <EyeOff v-else class="h-4 w-4" />
              </button>
            </div>
          </div>

          <div class="flex flex-col gap-2 relative">
            <Label>新密码</Label>
            <div class="relative w-full">
              <Input v-model="profile.newPassword" autocomplete="new-password" placeholder="请输入新密码" :type="showNewPassword ? 'text' : 'password'" class="pr-10" />
              <button type="button" class="password-toggle" aria-label="切换新密码可见性" @click="showNewPassword = !showNewPassword">
                <Eye v-if="showNewPassword" class="h-4 w-4" />
                <EyeOff v-else class="h-4 w-4" />
              </button>
            </div>
          </div>
        </div>
      </div>
    </form>
  </div>
</template>

<style scoped>
.panel-content-wrapper {
  --profile-avatar-size: 82px;
  display: flex;
  flex-direction: column;
  gap: var(--spacing-md);
}
.profile-avatar-row {
  display: grid;
  justify-items: center;
  align-items: center;
  gap: var(--spacing-sm);
  padding: var(--spacing-md) 0;
}
.profile-avatar {
  display: grid;
  place-items: center;
  inline-size: var(--profile-avatar-size);
  block-size: var(--profile-avatar-size);
  border-radius: var(--radius-full);
  background: var(--color-surface-muted);
  color: var(--color-brand);
  font-family: var(--font-serif);
  font-size: var(--font-size-lg);
  font-weight: 600;
  overflow: hidden;
  box-shadow: var(--shadow-ring);
}
.profile-avatar__image {
  width: 100%;
  height: 100%;
  object-fit: cover;
}
.profile-avatar__actions {
  display: grid;
  justify-items: center;
  gap: var(--spacing-xs);
}
.form-section {
  margin-top: var(--spacing-md);
  padding-top: var(--spacing-md);
  border-top: 1px dashed var(--color-border);
}
.form-section__title {
  margin: var(--spacing-xs) 0 var(--spacing-md);
  font-size: var(--font-size-md);
  font-weight: 500;
  color: var(--color-text-primary);
  font-family: var(--font-serif);
}
.password-toggle {
  position: absolute;
  top: 0;
  right: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  height: 100%;
  padding: 0 var(--spacing-sm);
  color: var(--color-text-tertiary);
  border-radius: var(--radius-md);
}
.password-toggle:hover,
.password-toggle:focus-visible {
  background: var(--color-surface-hover);
  color: var(--color-text-primary);
  outline: none;
  box-shadow: var(--shadow-icon-action-focus);
}
</style>
