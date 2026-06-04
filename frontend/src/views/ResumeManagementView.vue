<script setup lang="ts">
import { computed, onMounted, onBeforeUnmount, ref } from 'vue'
import { deleteResume, fetchResumes, uploadResume } from '../api/resume'
import { getErrorMessage } from '../utils/errors'
import axios from 'axios'
import type { ResumeItem } from '../api/contracts'
import { usePageNotice } from '../composables/usePageNotice'
import { useConfirmDialog } from '@/composables/useConfirmDialog'
import { Button } from '@/components/ui/button'
import { Card } from '@/components/ui/card'
import { Badge } from '@/components/ui/badge'
import EmptyState from '@/components/ui/empty-state/EmptyState.vue'
import { Loader2 } from 'lucide-vue-next'

const { showNotice } = usePageNotice()
const confirmDialog = useConfirmDialog()

const loading = ref(false)
const uploading = ref(false)
const uploadInput = ref<HTMLInputElement | null>(null)
const items = ref<ResumeItem[]>([])
const uploadAbortController = ref<AbortController | null>(null)

const inUseCount = computed(() => items.value.filter((item) => item.inUse).length)

async function loadResumes() {
  loading.value = true
  try {
    items.value = await fetchResumes()
  } catch (error) {
    showNotice(getErrorMessage(error), 'error')
  } finally {
    loading.value = false
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
    await uploadResume(file, controller.signal)
    await loadResumes()
    showNotice('简历已上传', 'success')
  } catch (error) {
    if (
      (error instanceof DOMException && error.name === 'AbortError') ||
      axios.isCancel(error)
    ) {
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
})
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
            class="!font-serif"
            @click="openUpload"
          >
            <Loader2 v-if="uploading" class="w-4 h-4 mr-2 animate-spin" />
            上传新简历
          </Button>
        </div>
      </div>
    </header>

    <div class="workspace-page__content scrollable">
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
            style="display: none;"
          />

          <div v-if="items.length" class="resume-catalog">
            <article v-for="item in items" :key="item.id" class="resume-row">
              <div class="resume-row__main">
                <div class="resume-row__title-wrap">
                  <h4 class="resume-item__title">{{ item.fileName }}</h4>
                  <p class="resume-item__hint">
                    {{ item.createdAt ? new Date(item.createdAt).toLocaleString() : '未知时间' }}
                  </p>
                </div>
                <div class="resume-item__badges">
                  <Badge variant="secondary">
                    {{ item.sessionCount || 0 }} 场使用
                  </Badge>
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

          <EmptyState v-else :description="loading ? '正在加载简历列表…' : '暂时还没有上传简历。'" />
        </Card>
      </div>
    </div>
  </section>
</template>

<style scoped>
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
  transition: all 0.2s;
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
  min-width: 200px;
}
.resume-item__title {
  margin: 0;
  font-size: 15px;
  font-weight: 500;
  color: var(--color-text-primary);
}
.resume-item__hint {
  margin: 0;
  font-size: 13px;
  color: var(--color-text-tertiary);
}
.resume-item__badges {
  display: flex;
  gap: var(--spacing-sm);
}
</style>
