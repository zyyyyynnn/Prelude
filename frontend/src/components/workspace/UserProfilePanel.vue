<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElButton, ElForm, ElFormItem, ElInput } from 'element-plus'
import { usePageNotice } from '../../composables/usePageNotice'
import { fetchUserProfile, updateUserProfile } from '../../api/user'
import { getErrorMessage } from '../../utils/errors'

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
    <ElForm class="form-grid" label-position="top" @submit.prevent>

      <div class="field-grid">
        <ElFormItem label="用户名">
          <ElInput v-model="profile.username" class="ui-input" disabled />
        </ElFormItem>

        <ElFormItem label="邮箱">
          <ElInput
            v-model="profile.email"
            class="ui-input"
            autocomplete="email"
            placeholder="请输入邮箱"
          />
        </ElFormItem>
      </div>

      <div class="form-section">
        <div class="form-section__title">修改密码</div>
        <div class="field-grid">
          <ElFormItem label="旧密码">
            <ElInput
              v-model="profile.oldPassword"
              class="ui-input"
              autocomplete="current-password"
              placeholder="留空表示不修改密码"
              show-password
              type="password"
            />
          </ElFormItem>

          <ElFormItem label="新密码">
            <ElInput
              v-model="profile.newPassword"
              class="ui-input"
              autocomplete="new-password"
              placeholder="请输入新密码"
              show-password
              type="password"
            />
          </ElFormItem>
        </div>
      </div>

      <div class="button-row">
        <ElButton
          class="ui-button ui-button--primary ui-button--compact"
          :loading="saving || loading"
          type="primary"
          @click="saveProfile"
        >
          保存设置
        </ElButton>
        <ElButton
          v-if="hasPasswordChange"
          class="ui-button ui-button--secondary ui-button--compact"
          :disabled="saving"
          @click="profile.oldPassword = ''; profile.newPassword = ''"
        >
          清空密码输入
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
</style>
