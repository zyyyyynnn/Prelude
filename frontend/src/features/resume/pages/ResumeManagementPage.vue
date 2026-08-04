<script setup lang="ts">
import { computed, onMounted, onBeforeUnmount, ref } from 'vue'
import { deleteResume, fetchResumes, uploadResume } from '../api/resume'
import { getErrorMessage } from '@/shared/lib/errors'
import axios from 'axios'
import type { ResumeItem } from '../model/types'
import { usePageNotice } from '@/shared/ui/sonner/usePageNotice'
import { useConfirmDialog } from '@/shared/ui/confirm-dialog/useConfirmDialog'
import { Button } from '@/shared/ui/button'
import { Card } from '@/shared/ui/card'
import { Badge } from '@/shared/ui/badge'
import EmptyState from '@/shared/ui/empty-state/EmptyState.vue'
import { TooltipText } from '@/shared/ui/tooltip'
import { withMinDelay } from '@/shared/lib/utils'
import type { AsyncStatus } from '@/shared/lib/async-status'
import { InlineAsyncError } from '@/shared/ui/inline-async-error'
import { Skeleton } from '@/shared/ui/skeleton'

const { showNotice } = usePageNotice()
const confirmDialog = useConfirmDialog()

const status = ref<AsyncStatus>('idle')
const error = ref<string | null>(null)
const refreshing = ref(false)
const loading = computed(() => status.value === 'idle' || status.value === 'loading')
const uploading = ref(false)
const uploadInput = ref<HTMLInputElement | null>(null)
const items = ref<ResumeItem[]>([])
const uploadAbortController = ref<AbortController | null>(null)
const loadAbortController = ref<AbortController | null>(null)

const inUseCount = computed(() => items.value.filter((item) => item.inUse).length)

async function loadResumes(mode: 'initial' | 'refresh' = 'initial') {
  loadAbortController.value?.abort()
  const controller = new AbortController()
  loadAbortController.value = controller
  if (mode === 'refresh' && items.value.length) {
    refreshing.value = true
  } else {
    status.value = 'loading'
  }
  error.value = null
  try {
    const result = await fetchResumes(controller.signal)
    if (controller.signal.aborted) return
    items.value = result
    status.value = 'success'
  } catch (requestError) {
    if (controller.signal.aborted) return
    error.value = getErrorMessage(requestError)
    status.value = items.value.length ? 'success' : 'error'
  } finally {
    if (loadAbortController.value === controller) {
      loadAbortController.value = null
    }
    refreshing.value = false
  }
}

function openUpload() {
  if (!uploading.value) {
    uploadInput.value?.click()
  }
}

async function handleUpload(event: Event) {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  input.value = ''
  if (!file) return

  uploadAbortController.value?.abort()
  const controller = new AbortController()
  uploadAbortController.value = controller

  uploading.value = true
  try {
    await withMinDelay(uploadResume(file, controller.signal))
    await loadResumes('refresh')
    if (!error.value) showNotice('简历已上传', 'success')
  } catch (error) {
    if ((error instanceof DOMException && error.name === 'AbortError') || axios.isCancel(error)) {
      return // 静默处理主动取消的请求
    }
    showNotice(getErrorMessage(error), 'error')
  } finally {
    uploading.value = false
    if (uploadAbortController.value === controller) {
      uploadAbortController.value = null
    }
  }
}

async function removeResume(item: ResumeItem) {
  if (item.inUse) {
    showNotice('该简历已被面试使用，无法删除', 'warning')
    return
  }

  const confirmed = await confirmDialog.confirm({
    title: '删除简历',
    message: `确认删除简历《${item.fileName}》吗？`,
    confirmText: '删除',
    cancelText: '取消',
    variant: 'destructive',
  })

  if (confirmed) {
    try {
      await deleteResume(item.id)
      items.value = items.value.filter((resume) => resume.id !== item.id)
      showNotice('简历已删除', 'success')
    } catch (error) {
      if (error instanceof Error && error.message !== 'cancel') {
        showNotice(getErrorMessage(error), 'error')
      }
    }
  }
}

onMounted(() => {
  void loadResumes()
})

onBeforeUnmount(() => {
  uploadAbortController.value?.abort()
  loadAbortController.value?.abort()
})

function retry() {
  void loadResumes(items.value.length ? 'refresh' : 'initial')
}
</script>

<template>
  <section class="workspace-page">
    <header class="workspace-header">
      <div class="workspace-header__main">
        <div class="workspace-header__title-area">
          <h2 class="workspace-header__title">简历管理</h2>
        </div>
        <div class="workspace-header__actions">
          <Button
            :disabled="uploading"
            :loading="uploading"
            class="!font-serif"
            @click="openUpload"
          >
            上传新简历
          </Button>
        </div>
      </div>
    </header>

    <div class="workspace-page__content scrollable" :aria-busy="loading || refreshing">
      <div v-if="loading" class="resume-loading" aria-busy="true">
        <div class="insight-strip insight-strip--compact">
          <Skeleton v-for="index in 3" :key="index" class="resume-loading__stat" />
        </div>
        <Skeleton class="resume-loading__list" />
      </div>
      <InlineAsyncError v-else-if="status === 'error'" :message="error" @retry="retry" />
      <template v-else-if="status === 'success'">
        <InlineAsyncError v-if="error" :message="error" @retry="retry" />
        <div class="insight-strip insight-strip--compact">
          <article class="insight-card">
            <p class="panel__eyebrow">总数</p>
            <h3 class="insight-card__value">{{ items.length }}</h3>
            <p class="insight-card__meta">当前账号下的简历数量</p>
          </article>
          <article class="insight-card">
            <p class="panel__eyebrow">已占用</p>
            <h3 class="insight-card__value">{{ inUseCount }}</h3>
            <p class="insight-card__meta">被会话引用，暂不可删除</p>
          </article>
          <article class="insight-card">
            <p class="panel__eyebrow">可清理</p>
            <h3 class="insight-card__value">{{ items.length - inUseCount }}</h3>
            <p class="insight-card__meta">未被占用，可直接删除</p>
          </article>
        </div>

        <div class="page-grid page-grid--single">
          <Card class="flex flex-col border-none shadow-none bg-surface p-5 rounded-xl">
            <div class="flex justify-between items-start mb-4">
              <div>
                <p class="text-xs text-muted-foreground uppercase tracking-wider mb-1">列表</p>
                <h3 class="text-lg font-medium font-serif">上传与清理</h3>
                <p class="text-sm text-muted-foreground">查看文件信息、使用次数和可执行操作。</p>
              </div>
              <div class="flex gap-2">
                <Badge variant="secondary">{{ items.length }} 份</Badge>
                <Badge variant="secondary">{{ inUseCount }} 份占用</Badge>
              </div>
            </div>

            <input
              ref="uploadInput"
              class="upload-field__native"
              accept="application/pdf"
              type="file"
              @change="handleUpload"
            />

            <div v-if="items.length" class="resume-catalog">
              <article v-for="item in items" :key="item.id" class="resume-row">
                <div class="resume-row__main">
                  <div class="resume-row__title-wrap">
                    <TooltipText as="h4" class="resume-item__title" :text="item.fileName" />
                    <p class="resume-item__hint">
                      {{ item.createdAt ? new Date(item.createdAt).toLocaleString() : '未知时间' }}
                    </p>
                  </div>
                  <div class="resume-item__badges">
                    <Badge variant="secondary"> {{ item.sessionCount || 0 }} 场使用 </Badge>
                    <Badge variant="secondary">
                      {{ item.inUse ? '已占用' : '可删除' }}
                    </Badge>
                  </div>
                </div>

                <div class="resume-row__actions">
                  <Button
                    variant="secondary"
                    size="sm"
                    :disabled="Boolean(item.inUse)"
                    @click="removeResume(item)"
                  >
                    删除
                  </Button>
                </div>
              </article>
            </div>

            <EmptyState v-else description="暂时还没有上传简历。" />
          </Card>
        </div>
      </template>
    </div>
  </section>
</template>

<style scoped>
.resume-loading {
  --resume-loading-stat-block-size: calc(var(--layout-settings-dialog-min-block-size) / 5);
  --resume-loading-list-block-size: var(--layout-settings-dialog-min-block-size);

  display: flex;
  flex-direction: column;
  gap: var(--spacing-lg);
}
.resume-loading__stat {
  flex: 1;
  min-block-size: var(--resume-loading-stat-block-size);
  border-radius: var(--radius-lg);
}
.resume-loading__list {
  min-block-size: var(--resume-loading-list-block-size);
  border-radius: var(--radius-xl);
}
.resume-catalog {
  display: flex;
  flex-direction: column;
  gap: var(--spacing-sm);
  margin-top: var(--spacing-md);
}
.resume-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: var(--spacing-sm);
  border-radius: var(--radius-lg);
  border: 1px solid var(--color-border);
  background: var(--color-surface);
  transition:
    border-color var(--motion-duration-base) var(--motion-ease-standard),
    background-color var(--motion-duration-base) var(--motion-ease-standard);
}
.resume-row:hover {
  border-color: var(--color-border-warm);
  background: var(--color-surface-hover);
}
.resume-row__main {
  display: flex;
  align-items: center;
  gap: var(--spacing-md);
  flex: 1;
}
.resume-row__title-wrap {
  display: flex;
  flex-direction: column;
  gap: var(--spacing-xs);
  min-inline-size: var(--layout-list-title-min-inline-size);
}
.resume-item__title {
  margin: 0;
  font-size: var(--font-size-md);
  font-weight: 500;
  color: var(--color-text-primary);
}
.resume-item__hint {
  margin: 0;
  font-size: var(--font-size-xs);
  color: var(--color-text-tertiary);
}
.resume-item__badges {
  display: flex;
  gap: var(--spacing-sm);
}
</style>
