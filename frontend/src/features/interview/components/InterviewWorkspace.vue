<script setup lang="ts">
import { useTemplateRef } from 'vue'
import { StructuredReportPanel } from '@/features/report'
import { InlineAsyncError } from '@/shared/ui/inline-async-error'
import { Skeleton } from '@/shared/ui/skeleton'
import { useInterviewPageController } from '../composables/useInterviewPageController'
import WorkspaceHeader from './WorkspaceHeader.vue'
import MessageThread from './MessageThread.vue'
import InterviewComposer from './InterviewComposer.vue'
import RoseThree from '@/shared/ui/loader/RoseThree.vue'

const emit = defineEmits<{
  (event: 'open-global-settings', tab?: 'profile' | 'theme' | 'llm'): void
}>()

const {
  activeSessionId,
  replay,
  dashboardStatus,
  dashboardError,
  dashboardRefreshing,
  sessionDetailStatus,
  sessionDetailError,
  sessionDetailRefreshing,
  resumes,
  positions,
  selectedResumeId,
  selectedPositionId,
  answer,
  uploading,
  uploadDisplayName,
  sending,
  creating,
  finishing,
  showingReport,
  messages,
  jdText,
  reconnectingStatus,
  currentStage,
  targetPosition,
  sessionStatus,
  hasReport,
  isFinished,
  isGenerating,
  llmProvider,
  llmModel,
  parsedReport,
  renderedReport,
  isVoiceMode,
  voiceStatus,
  incomingAudioChunk,
  exportingPdf,
  improvementBusyId,
  handleUpload,
  createNewInterview,
  handleSend,
  handleFinish,
  handleExportPdf,
  handleAcceptImprovement,
  handleRejectImprovement,
  retryDashboard,
  retryActiveSession,
  handleAudioChunk,
  handleStartRecording,
  handleStopRecording,
  handlePlayStatus,
} = useInterviewPageController()

const reportRef = useTemplateRef<HTMLElement>('reportRef')

function exportReport() {
  return handleExportPdf(reportRef.value)
}
</script>

<template>
  <div
    class="interview-workspace"
    :aria-busy="
      dashboardStatus === 'loading' || dashboardRefreshing || sessionDetailStatus === 'loading'
    "
  >
    <div v-if="dashboardRefreshing" class="workspace-dashboard-refreshing" aria-live="polite">
      正在刷新工作区…
    </div>
    <div v-if="dashboardStatus === 'loading' && !activeSessionId" class="workspace-loading-shell">
      <Skeleton class="workspace-loading-shell__title" />
      <Skeleton v-for="index in 4" :key="index" class="workspace-loading-shell__line" />
    </div>

    <InlineAsyncError
      v-else-if="dashboardStatus === 'error' && !activeSessionId"
      class="workspace-page-error"
      :message="dashboardError || '工作区加载失败，请重试。'"
      @retry="retryDashboard"
    />

    <!-- Empty State -->
    <div v-else-if="dashboardStatus === 'success' && !activeSessionId" class="workspace-empty">
      <div class="workspace-empty__content">
        <h1 class="workspace-empty__title">准备开始一场沉浸式模拟面试</h1>
        <InterviewComposer
          :is-centered="true"
          :resumes="resumes"
          :positions="positions"
          :selected-resume-id="selectedResumeId"
          :selected-position-id="selectedPositionId"
          v-model="answer"
          :uploading="uploading"
          :upload-display-name="uploadDisplayName"
          :sending="sending"
          :creating="creating"
          @update:selected-resume-id="(id) => (selectedResumeId = id)"
          @update:selected-position-id="(id) => (selectedPositionId = id)"
          @upload="handleUpload"
          @start="createNewInterview"
          @send="handleSend"
          @open-global-settings="(tab) => emit('open-global-settings', tab)"
        />
      </div>
    </div>

    <!-- Active Session View -->
    <div v-else-if="activeSessionId" class="workspace-active">
      <InlineAsyncError
        v-if="dashboardError"
        class="workspace-refresh-error"
        :message="dashboardError"
        @retry="retryDashboard"
      />
      <WorkspaceHeader
        :active-session-id="activeSessionId"
        :target-position="targetPosition"
        :current-stage="currentStage"
        :session-status="sessionStatus"
        :sending="sending"
        :finishing="finishing"
        :has-report="hasReport"
        :showing-report="showingReport"
        :is-finished="isFinished"
        :exporting="exportingPdf"
        @finish="handleFinish"
        @toggle-report="showingReport = $event"
        @export-pdf="exportReport"
      />

      <div class="workspace-active__main">
        <div v-if="sessionDetailStatus === 'loading' && !replay" class="workspace-detail-loading">
          <Skeleton v-for="index in 6" :key="index" class="workspace-detail-loading__line" />
        </div>
        <InlineAsyncError
          v-else-if="sessionDetailError && !replay"
          class="workspace-detail-error"
          :message="sessionDetailError"
          @retry="retryActiveSession"
        />
        <div
          v-else-if="sessionDetailRefreshing"
          class="workspace-detail-refreshing"
          aria-live="polite"
        >
          正在刷新会话内容…
        </div>

        <template v-if="sessionDetailStatus === 'success' || replay">
          <div v-if="isGenerating" class="workspace-generating scrollable">
            <div class="generating-card">
              <RoseThree class="generating-rose" :speed-multiplier="0.9" />
              <h3 class="generating-title">AI 评估报告生成中...</h3>
              <p class="generating-subtitle">
                我们正在整理您的答题表现，并调用 LLM-as-Judge 进行深度诊断，请稍候（约需 10-15 秒）
              </p>
              <div class="generating-progress">
                <div class="progress-bar-ind"></div>
              </div>
            </div>
          </div>

          <div v-else-if="showingReport" class="workspace-report scrollable">
            <div class="report-content">
              <div class="report-export-surface" ref="reportRef">
                <StructuredReportPanel
                  v-if="parsedReport.kind === 'structured'"
                  :report="parsedReport.report"
                  :improvement-busy-id="improvementBusyId"
                  @accept-improvement="handleAcceptImprovement"
                  @reject-improvement="handleRejectImprovement"
                />
                <div v-else class="markdown-surface markdown-surface--paper">
                  <div class="markdown-body" v-html="renderedReport" />
                </div>
              </div>
            </div>
          </div>

          <template v-else>
            <MessageThread :messages="messages" :reconnecting-status="reconnectingStatus" />

            <div class="workspace-composer-fixed">
              <InterviewComposer
                :disabled="isFinished"
                :is-centered="false"
                :active-session-id="activeSessionId"
                :resumes="resumes"
                :positions="positions"
                :selected-resume-id="selectedResumeId"
                :selected-position-id="selectedPositionId"
                :llm-provider="llmProvider"
                :llm-model="llmModel"
                :jd-text="jdText"
                v-model="answer"
                :uploading="uploading"
                :upload-display-name="uploadDisplayName"
                :sending="sending"
                :creating="creating"
                :is-voice-mode="isVoiceMode"
                :voice-status="voiceStatus"
                :incoming-audio="incomingAudioChunk"
                @update:is-voice-mode="(v) => (isVoiceMode = v)"
                @update:selected-resume-id="(id) => (selectedResumeId = id)"
                @update:selected-position-id="(id) => (selectedPositionId = id)"
                @upload="handleUpload"
                @start="createNewInterview"
                @send="handleSend"
                @voice-audio-chunk="handleAudioChunk"
                @voice-start-recording="handleStartRecording"
                @voice-stop-recording="handleStopRecording"
                @voice-play-status="handlePlayStatus"
                @open-global-settings="(tab) => emit('open-global-settings', tab)"
              />
            </div>
          </template>
        </template>
      </div>
    </div>

    <div v-else class="workspace-loading-shell">
      <Skeleton class="workspace-loading-shell__title" />
      <Skeleton v-for="index in 4" :key="index" class="workspace-loading-shell__line" />
    </div>
  </div>
</template>

<style scoped>
.interview-workspace {
  /* 页面级语义变量：把本视图独有的尺寸集中声明，便于审查与未来重构。 */
  --workspace-content-max-inline-size: var(--layout-workspace-content-max-inline-size);
  --workspace-generating-card-max-inline-size: 480px;
  --workspace-generating-title-font-size: clamp(var(--font-size-md), 2vw, var(--font-size-lg));
  --workspace-generating-rose-size: calc(var(--ui-height-base) * 2);
  --workspace-progress-track-block-size: var(--spacing-xs);
  --workspace-progress-track-radius: var(--spacing-0-5);

  flex: 1;
  display: flex;
  flex-direction: column;
  block-size: 100%;
  min-block-size: 0;
}
.workspace-empty {
  flex: 1;
  display: flex;
  align-items: safe center;
  justify-content: center;
  padding: var(--spacing-2xl);
  overflow-y: auto;
}
.workspace-empty__content {
  inline-size: 100%;
  max-inline-size: var(--workspace-content-max-inline-size);
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: var(--spacing-lg);
}
.workspace-empty__title {
  font-family: var(--font-serif);
  font-size: clamp(var(--font-size-lg), 3vw, var(--font-size-xl));
  font-weight: 500;
  color: var(--color-text-primary);
  margin: 0;
  text-align: center;
}
.workspace-active {
  flex: 1;
  display: flex;
  flex-direction: column;
  height: 100%;
  overflow: hidden;
  background: var(--color-bg);
  min-height: 0;
}
.workspace-active__main {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  position: relative;
  min-height: 0;
}
.workspace-report {
  flex: 1;
  display: flex;
  padding-block: calc(var(--spacing-2xl) + var(--spacing-lg));
  padding-inline: var(--spacing-2xl);
  overflow-y: auto;
  scrollbar-gutter: stable;
  align-items: flex-start;
  justify-content: center;
  min-block-size: 0;
}
.report-content {
  flex: 1;
  max-inline-size: var(--workspace-content-max-inline-size);
}
.report-export-surface {
  inline-size: 100%;
  background: var(--color-surface);
}
.markdown-surface--paper {
  background: var(--color-surface);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  padding: var(--spacing-2xl);
  box-shadow: var(--shadow-whisper);
}
.workspace-composer-fixed {
  position: absolute;
  bottom: 0;
  left: 0;
  right: 0;
  padding: var(--spacing-md) var(--spacing-2xl) var(--spacing-lg);
  background: transparent;
  z-index: var(--z-index-workspace-composer);
  pointer-events: none;
}
.workspace-composer-fixed > * {
  pointer-events: auto;
}
.workspace-loading {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--color-text-tertiary);
}
.workspace-loading-shell,
.workspace-detail-loading {
  display: flex;
  flex-direction: column;
  gap: var(--spacing-md);
  padding: var(--spacing-2xl);
}
.workspace-loading-shell {
  inline-size: min(100%, var(--workspace-content-max-inline-size));
  margin: auto;
}
.workspace-loading-shell__title {
  inline-size: 42%;
  block-size: var(--ui-height-md);
}
.workspace-loading-shell__line {
  inline-size: 100%;
  block-size: var(--ui-height-sm);
}
.workspace-page-error,
.workspace-refresh-error,
.workspace-detail-error {
  margin: var(--spacing-xl) auto;
  inline-size: min(calc(100% - var(--spacing-2xl) * 2), var(--workspace-content-max-inline-size));
}
.workspace-refresh-error {
  margin-block: var(--spacing-sm);
}
.workspace-dashboard-refreshing {
  margin: var(--spacing-sm) var(--spacing-lg) 0;
  color: var(--color-text-tertiary);
  font-size: var(--font-size-xs);
}
.workspace-detail-loading {
  flex: 1;
  justify-content: center;
  inline-size: min(100%, var(--workspace-content-max-inline-size));
  margin: auto;
}
.workspace-detail-loading__line {
  inline-size: 100%;
  block-size: var(--ui-height-sm);
}
.workspace-detail-refreshing {
  position: absolute;
  inset-block-start: var(--spacing-sm);
  inset-inline-end: var(--spacing-lg);
  z-index: var(--z-index-workspace-composer);
  color: var(--color-text-tertiary);
  font-size: var(--font-size-xs);
}
.voice-mode-container {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: var(--spacing-sm);
  width: 100%;
}
.voice-close-btn {
  font-size: var(--font-size-sm);
  padding: 0 var(--spacing-md);
  height: var(--ui-height-sm);
  border-radius: var(--radius-md);
  border: 1px solid var(--color-border);
  background: var(--color-surface);
  color: var(--color-text-secondary);
  cursor: pointer;
  transition: var(--motion-transition-surface);
}
.voice-close-btn:hover {
  background-color: var(--color-surface-hover);
  color: var(--color-text-primary);
}

.workspace-generating {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: var(--spacing-xl);
  background: var(--color-surface);
}
.generating-card {
  display: flex;
  flex-direction: column;
  align-items: center;
  max-inline-size: var(--workspace-generating-card-max-inline-size);
  padding: var(--spacing-2xl);
  background: var(--color-surface-hover);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-whisper);
  text-align: center;
}
.generating-rose {
  inline-size: var(--workspace-generating-rose-size);
  block-size: var(--workspace-generating-rose-size);
  color: var(--rose-three-color);
  margin-bottom: var(--spacing-lg);
}
.generating-title {
  font-family: var(--font-serif);
  font-size: var(--workspace-generating-title-font-size);
  font-weight: 500;
  color: var(--color-text-primary);
  margin-bottom: var(--spacing-xs);
}
.generating-subtitle {
  font-size: var(--font-size-sm);
  color: var(--color-text-secondary);
  margin-bottom: var(--spacing-lg);
  line-height: 1.6;
}
.generating-progress {
  inline-size: 100%;
  block-size: var(--workspace-progress-track-block-size);
  background: var(--color-border);
  border-radius: var(--workspace-progress-track-radius);
  overflow: hidden;
  position: relative;
}
.progress-bar-ind {
  inline-size: 50%;
  block-size: 100%;
  background: var(--color-brand);
  border-radius: var(--workspace-progress-track-radius);
  position: absolute;
  left: -50%;
  animation: progress-ind-anim var(--motion-duration-slow) infinite var(--motion-ease-standard);
}
@keyframes progress-ind-anim {
  0% {
    left: -50%;
  }
  100% {
    left: 100%;
  }
}
</style>
