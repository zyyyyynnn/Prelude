<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElButton, ElCard, ElForm, ElFormItem, ElInput, ElTag } from 'element-plus'
import { usePageNotice } from '../composables/usePageNotice'
import { fetchUserProfile, updateUserProfile } from '../api/user'

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

function getErrorMessage(error: unknown) {
  return error instanceof Error ? error.message : '请求失败'
}

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
  <section class="workspace-page">
    <header class="workspace-header">
      <div class="workspace-header__main">
        <div class="workspace-header__title-area">
          <h2 class="workspace-header__title">用户设置</h2>
        </div>
      </div>
    </header>

    <div class="workspace-page__content scrollable">
      <div class="page-grid page-grid--single">
        <ElCard class="ui-card panel">
          <div class="panel__head">
            <div>
              <p class="panel__eyebrow">资料层</p>
              <h3 class="panel__title">账号资料</h3>
              <p class="panel__lead">更新邮箱，或填写旧密码和新密码完成密码修改。</p>
            </div>
            <ElTag class="ui-badge" effect="light">邮箱与密码维护</ElTag>
          </div>

          <ElForm class="form-grid" label-position="top" @submit.prevent>
            <div class="detail-grid">
              <article class="detail-card">
                <p class="panel__eyebrow">账号</p>
                <h4 class="detail-card__title">{{ profile.username || '未加载' }}</h4>
                <p class="detail-card__meta">用户名不可修改。</p>
              </article>
              <article class="detail-card">
                <p class="panel__eyebrow">资料状态</p>
                <h4 class="detail-card__title">{{ profile.email || '未配置邮箱' }}</h4>
                <p class="detail-card__meta">密码修改需要同时填写两项。</p>
              </article>
            </div>

            <div class="field-grid">
              <ElFormItem label="用户名">
                <ElInput v-model="profile.username" class="ui-input" disabled size="large" />
              </ElFormItem>

              <ElFormItem label="邮箱">
                <ElInput
                  v-model="profile.email"
                  class="ui-input"
                  autocomplete="email"
                  placeholder="请输入邮箱"
                  size="large"
                />
              </ElFormItem>
            </div>

            <div class="form-section">
              <div>
                <p class="panel__eyebrow">密码修改</p>
                <h4 class="form-section__title">可选更新</h4>
              </div>

              <div class="field-grid">
                <ElFormItem label="旧密码">
                  <ElInput
                    v-model="profile.oldPassword"
                    class="ui-input"
                    autocomplete="current-password"
                    placeholder="留空表示不修改密码"
                    show-password
                    type="password"
                    size="large"
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
                    size="large"
                  />
                </ElFormItem>
              </div>
            </div>

            <div class="button-row panel__footer-actions">
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
        </ElCard>
      </div>
    </div>
  </section>
</template>

<style scoped>
.workspace-page {
  flex: 1;
  display: flex;
  flex-direction: column;
  background: var(--color-bg);
  overflow: hidden;
  height: 100vh;
}
.workspace-header {
  display: flex;
  align-items: center;
  padding: 16px 24px;
  border-bottom: 1px solid var(--color-border);
  background: rgba(250, 249, 245, 0.85);
  backdrop-filter: blur(12px);
  position: sticky;
  top: 0;
  z-index: 100;
  flex-shrink: 0;
  height: 72px;
  box-sizing: border-box;
}
.workspace-header__main {
  display: flex;
  align-items: center;
  justify-content: space-between;
  width: 100%;
}
.workspace-header__title-area {
  display: flex;
  align-items: center;
  gap: 12px;
}
.workspace-header__title {
  margin: 0;
  font-family: var(--font-serif);
  font-size: 20px;
  font-weight: 500;
  color: var(--color-text-primary);
}
.workspace-page__content {
  flex: 1;
  padding: 24px 40px;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  gap: 24px;
}
.page-grid {
  display: flex;
  flex-direction: column;
  gap: 24px;
}
.detail-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
  gap: 16px;
  margin-bottom: 24px;
}
.detail-card {
  padding: 16px;
  background: var(--color-sand);
  border-radius: var(--radius-lg);
  border: 1px solid var(--color-border);
}
.detail-card__title {
  margin: 4px 0;
  font-size: 16px;
  font-weight: 500;
  color: var(--color-text-primary);
}
.detail-card__meta {
  margin: 0;
  font-size: 13px;
  color: var(--color-text-tertiary);
}
.field-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(280px, 1fr));
  gap: 24px;
}
.form-section {
  margin-top: 32px;
  padding-top: 24px;
  border-top: 1px dashed var(--color-border);
}
.form-section__title {
  margin: 4px 0 16px;
  font-size: 15px;
  font-weight: 500;
  color: var(--color-text-primary);
}
.button-row {
  display: flex;
  gap: 12px;
  margin-top: 32px;
}
</style>
