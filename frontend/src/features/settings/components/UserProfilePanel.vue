<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { storeToRefs } from 'pinia'
import { usePageNotice } from '@/shared/ui/sonner/usePageNotice'
import { getErrorMessage } from '@/shared/lib/errors'
import { withMinDelay } from '@/shared/lib/utils'
import { Input } from '@/shared/ui/input'
import { Button } from '@/shared/ui/button'
import { Label } from '@/shared/ui/label'
import { InlineAsyncError } from '@/shared/ui/inline-async-error'
import { Skeleton } from '@/shared/ui/skeleton'
import { UserAvatar } from '@/shared/ui/avatar'
import { Eye, EyeOff } from '@lucide/vue'
import { useUserProfileStore } from '../stores/userProfileStore'
import { useAvatarUploadPreview } from '../composables/useAvatarUploadPreview'

const profileStore = useUserProfileStore()
const {
  profile: storedProfile,
  status,
  error,
  refreshing,
  activeAccountScope,
} = storeToRefs(profileStore)
const { showNotice } = usePageNotice()
const preview = useAvatarUploadPreview()
const { previewUrl } = preview

const loading = computed(() => status.value === 'idle' || status.value === 'loading')
const saving = ref(false)
const uploadingAvatar = ref(false)
const avatarInput = ref<HTMLInputElement | null>(null)
const avatarError = ref<string | null>(null)
const awaitingCanonicalAvatar = ref(false)
const canonicalAvatarToConfirm = ref<string | null>(null)
const lastLoadedCanonicalAvatar = ref<string | null>(null)
const initial = reactive({
  username: '',
  email: '',
  revision: 0,
})
const draft = reactive({
  username: '',
  email: '',
  oldPassword: '',
  newPassword: '',
})

const showOldPassword = ref(false)
const showNewPassword = ref(false)
const dirty = computed(
  () =>
    draft.username.trim() !== initial.username.trim() ||
    draft.email.trim() !== initial.email.trim() ||
    Boolean(draft.oldPassword || draft.newPassword),
)

function syncDraftFromStore() {
  if (status.value === 'success' && storedProfile.value && !dirty.value) {
    draft.username = storedProfile.value.username || ''
    draft.email = storedProfile.value.email || ''
    initial.username = draft.username
    initial.email = draft.email
    initial.revision = profileStore.mutationRevision
    return
  }
  if (!activeAccountScope.value || !storedProfile.value) {
    draft.username = ''
    draft.email = ''
    draft.oldPassword = ''
    draft.newPassword = ''
    initial.username = ''
    initial.email = ''
    initial.revision = 0
    avatarError.value = null
    awaitingCanonicalAvatar.value = false
    canonicalAvatarToConfirm.value = null
    lastLoadedCanonicalAvatar.value = null
    preview.clear()
  }
}

async function loadProfile() {
  try {
    await profileStore.ensureLoaded()
  } catch {
    // The inline error surface owns the initial-load failure.
  }
}

function retry() {
  void profileStore.ensureLoaded(true).catch(() => undefined)
}

async function saveProfile() {
  const username = draft.username.trim()
  const email = draft.email.trim()
  const oldPassword = draft.oldPassword.trim()
  const newPassword = draft.newPassword.trim()
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
    const result = await withMinDelay(
      profileStore.updateProfile({
        username: username || undefined,
        email: email || undefined,
        oldPassword: oldPassword || undefined,
        newPassword: newPassword || undefined,
      }),
    )
    draft.username = result.username || username
    draft.email = result.email || email
    initial.username = draft.username
    initial.email = draft.email
    initial.revision = profileStore.mutationRevision
    draft.oldPassword = ''
    draft.newPassword = ''
    showNotice('资料已保存', 'success')
  } catch (error) {
    showNotice(getErrorMessage(error), 'error')
  } finally {
    saving.value = false
  }
}

async function handleAvatarChange(event: Event) {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  input.value = ''
  if (!file) return

  avatarError.value = null
  let selection
  try {
    selection = await preview.prepare(file)
  } catch (error) {
    avatarError.value = getErrorMessage(error)
    return
  }
  if (!selection || !preview.isCurrent(selection)) return

  uploadingAvatar.value = true
  awaitingCanonicalAvatar.value = false
  canonicalAvatarToConfirm.value = null
  try {
    const result = await withMinDelay(profileStore.uploadAvatar(file))
    if (!preview.isCurrent(selection)) return
    canonicalAvatarToConfirm.value = result.avatarUrl?.trim() || null
    awaitingCanonicalAvatar.value = true
    avatarError.value = null
    if (
      canonicalAvatarToConfirm.value &&
      lastLoadedCanonicalAvatar.value === canonicalAvatarToConfirm.value
    ) {
      confirmCanonicalAvatar()
    }
  } catch (error) {
    if (preview.isCurrent(selection)) {
      avatarError.value = getErrorMessage(error)
      canonicalAvatarToConfirm.value = null
      awaitingCanonicalAvatar.value = false
      preview.clear()
    }
  } finally {
    if (preview.isCurrent(selection)) uploadingAvatar.value = false
  }
}

function confirmCanonicalAvatar() {
  awaitingCanonicalAvatar.value = false
  canonicalAvatarToConfirm.value = null
  preview.clear()
  avatarError.value = null
  uploadingAvatar.value = false
}

function handleCanonicalImageLoaded(src: string) {
  lastLoadedCanonicalAvatar.value = src
  if (src !== canonicalAvatarToConfirm.value) return
  confirmCanonicalAvatar()
}

function handleAvatarImageError(src: string) {
  if (awaitingCanonicalAvatar.value && src !== canonicalAvatarToConfirm.value) return
  if (preview.previewUrl.value && !awaitingCanonicalAvatar.value) {
    avatarError.value = '头像原图暂不可用，当前仍显示本地预览。'
    return
  }
  awaitingCanonicalAvatar.value = false
  canonicalAvatarToConfirm.value = null
  preview.clear()
  uploadingAvatar.value = false
  avatarError.value = storedProfile.value?.avatarUrl
    ? '头像文件暂不可用，已显示默认头像。'
    : '头像显示失败，已显示默认头像。'
}

watch([storedProfile, status, activeAccountScope], syncDraftFromStore, { immediate: true })

onMounted(() => {
  void loadProfile()
})

defineExpose({ submit: saveProfile, saving, loading, dirty })
</script>

<template>
  <div class="panel-content-wrapper" :aria-busy="loading || refreshing">
    <div v-if="loading" class="profile-loading" aria-busy="true">
      <Skeleton class="profile-loading__avatar" />
      <div class="profile-loading__fields">
        <Skeleton />
        <Skeleton />
      </div>
      <Skeleton class="profile-loading__passwords" />
    </div>
    <InlineAsyncError v-else-if="status === 'error'" :message="error" @retry="retry" />
    <template v-else-if="status === 'success' && storedProfile">
      <p v-if="refreshing" class="profile-refreshing" aria-live="polite">正在刷新资料…</p>
      <InlineAsyncError v-if="error" :message="error" @retry="retry" />
      <form class="flex flex-col gap-6" @submit.prevent>
        <section class="profile-avatar-row">
          <UserAvatar
            :src="storedProfile.avatarUrl"
            :preview-src="previewUrl"
            :name="draft.username"
            alt="当前用户头像"
            :size="82"
            @image-loaded="handleCanonicalImageLoaded"
            @image-error="handleAvatarImageError"
          />
          <div class="profile-avatar__actions">
            <input
              id="profile-avatar-input"
              ref="avatarInput"
              class="upload-field__native"
              type="file"
              accept="image/png,image/jpeg"
              @change="handleAvatarChange"
            />
            <Label for="profile-avatar-input" class="sr-only">选择头像文件</Label>
            <Button
              type="button"
              variant="secondary"
              size="sm"
              :loading="uploadingAvatar"
              @click="avatarInput?.click()"
            >
              上传头像
            </Button>
            <p v-if="avatarError" class="profile-avatar__error" role="alert">{{ avatarError }}</p>
          </div>
        </section>

        <div class="field-grid">
          <div class="flex flex-col gap-2">
            <Label for="profile-username">用户名</Label>
            <Input
              id="profile-username"
              v-model="draft.username"
              autocomplete="username"
              placeholder="请输入用户名"
            />
          </div>

          <div class="flex flex-col gap-2">
            <Label for="profile-email">邮箱</Label>
            <Input
              id="profile-email"
              v-model="draft.email"
              autocomplete="email"
              placeholder="请输入邮箱"
            />
          </div>
        </div>

        <div class="form-section">
          <div class="form-section__title">修改密码</div>
          <div class="field-grid">
            <div class="flex flex-col gap-2 relative">
              <Label for="profile-old-password">旧密码</Label>
              <div class="relative w-full">
                <Input
                  id="profile-old-password"
                  v-model="draft.oldPassword"
                  autocomplete="current-password"
                  placeholder="留空表示不修改密码"
                  :type="showOldPassword ? 'text' : 'password'"
                  class="pr-10"
                />
                <button
                  type="button"
                  class="password-toggle ui-action ui-action-icon"
                  aria-label="切换旧密码可见性"
                  @click="showOldPassword = !showOldPassword"
                >
                  <Eye v-if="showOldPassword" class="h-4 w-4" />
                  <EyeOff v-else class="h-4 w-4" />
                </button>
              </div>
            </div>

            <div class="flex flex-col gap-2 relative">
              <Label for="profile-new-password">新密码</Label>
              <div class="relative w-full">
                <Input
                  id="profile-new-password"
                  v-model="draft.newPassword"
                  autocomplete="new-password"
                  placeholder="请输入新密码"
                  :type="showNewPassword ? 'text' : 'password'"
                  class="pr-10"
                />
                <button
                  type="button"
                  class="password-toggle ui-action ui-action-icon"
                  aria-label="切换新密码可见性"
                  @click="showNewPassword = !showNewPassword"
                >
                  <Eye v-if="showNewPassword" class="h-4 w-4" />
                  <EyeOff v-else class="h-4 w-4" />
                </button>
              </div>
            </div>
          </div>
        </div>
      </form>
    </template>
  </div>
</template>

<style scoped>
.panel-content-wrapper {
  --profile-loading-avatar-size: 82px;
  --profile-loading-password-block-size: 160px;
  --profile-avatar-error-max-inline-size: 260px;

  display: flex;
  flex-direction: column;
  gap: var(--spacing-md);
}

.profile-loading {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: var(--spacing-lg);
}

.profile-loading__avatar {
  inline-size: var(--profile-loading-avatar-size);
  block-size: var(--profile-loading-avatar-size);
  min-block-size: 0;
  border-radius: var(--radius-full);
}

.profile-loading__fields {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: var(--spacing-md);
  inline-size: 100%;
}

.profile-loading__fields > * {
  min-block-size: var(--ui-height-md);
}

.profile-loading__passwords {
  inline-size: 100%;
  min-block-size: var(--profile-loading-password-block-size);
}

.profile-avatar-row {
  display: grid;
  justify-items: center;
  align-items: center;
  gap: var(--spacing-sm);
  padding: var(--spacing-md) 0;
}

.profile-refreshing {
  margin: 0;
  color: var(--color-text-tertiary);
  font-size: var(--font-size-xs);
}

.profile-avatar__actions {
  display: grid;
  justify-items: center;
  gap: var(--spacing-xs);
}

.profile-avatar__error {
  max-inline-size: var(--profile-avatar-error-max-inline-size);
  margin: 0;
  color: var(--color-error);
  font-size: var(--font-size-xs);
  text-align: center;
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
  border: 1px solid transparent;
  border-radius: var(--radius-md);
}

.password-toggle:hover {
  background: var(--color-surface-hover);
  color: var(--color-text-primary);
}
</style>
