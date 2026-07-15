<script setup lang="ts">
import { onBeforeMount, ref, toRaw, watch } from 'vue'
import { Plus, Trash2 } from '@lucide/vue'
import { Button } from '@/shared/ui/button'
import { Input } from '@/shared/ui/input'
import { Textarea } from '@/shared/ui/textarea'
import type {
  ResumeDocument,
  ResumeEducation,
  ResumeExperience,
  ResumeProfile,
  ResumeProject,
  ResumeSkill,
} from '../model/types'

const props = defineProps<{
  fileName: string
  documentVersion: number
  document: ResumeDocument
  saving?: boolean
}>()

const emit = defineEmits<{
  (event: 'save', document: ResumeDocument): void
  (event: 'cancel'): void
}>()

const draft = ref<ResumeDocument>()

function cloneDocument(document: ResumeDocument): ResumeDocument {
  const cloned = structuredClone(toRaw(document))
  cloned.profile ??= emptyProfile()
  return cloned
}

function emptyProfile(): ResumeProfile {
  return { fullName: '', email: '', phone: '', targetRole: '' }
}

function emptySkill(): ResumeSkill {
  return { name: '', level: '' }
}

function emptyExperience(): ResumeExperience {
  return { company: '', title: '', start: '', end: '', bullets: [''] }
}

function emptyProject(): ResumeProject {
  return { name: '', role: '', techStack: [], bullets: [''], outcome: '' }
}

function emptyEducation(): ResumeEducation {
  return { school: '', degree: '', end: '' }
}

function removeAt<T>(items: T[], index: number) {
  items.splice(index, 1)
}

function addBullet(items: string[]) {
  items.push('')
}

function submit() {
  if (draft.value) emit('save', structuredClone(toRaw(draft.value)))
}

onBeforeMount(() => {
  draft.value = cloneDocument(props.document)
})

watch(
  () => props.document,
  (document) => {
    draft.value = cloneDocument(document)
  },
)
</script>

<template>
  <div v-if="draft" class="resume-editor">
    <header class="resume-editor__header">
      <div>
        <p class="panel__eyebrow">结构化简历 · v{{ documentVersion }}</p>
        <h3>{{ fileName }}</h3>
      </div>
      <div class="resume-editor__actions">
        <Button variant="secondary" :disabled="saving" @click="emit('cancel')">返回列表</Button>
        <Button :loading="saving" @click="submit">保存修改</Button>
      </div>
    </header>

    <section class="resume-editor__section">
      <div class="resume-editor__section-title">
        <h4>基本信息</h4>
      </div>
      <div class="resume-editor__grid">
        <label>
          <span>姓名</span>
          <Input v-model="draft.profile!.fullName" autocomplete="name" />
        </label>
        <label>
          <span>目标岗位</span>
          <Input v-model="draft.profile!.targetRole" />
        </label>
        <label>
          <span>邮箱</span>
          <Input v-model="draft.profile!.email" autocomplete="email" type="email" />
        </label>
        <label>
          <span>电话</span>
          <Input v-model="draft.profile!.phone" autocomplete="tel" />
        </label>
      </div>
      <label class="resume-editor__wide-field">
        <span>个人摘要</span>
        <Textarea v-model="draft.summary" rows="4" />
      </label>
    </section>

    <section class="resume-editor__section">
      <div class="resume-editor__section-title">
        <h4>技能</h4>
        <Button size="sm" variant="secondary" @click="draft.skills.push(emptySkill())">
          <Plus class="size-4" aria-hidden="true" />新增技能
        </Button>
      </div>
      <div class="resume-editor__rows">
        <div v-for="(skill, index) in draft.skills" :key="index" class="resume-editor__row">
          <Input v-model="skill.name" placeholder="技能名称" />
          <Input v-model="skill.level" placeholder="熟练度" />
          <Button
            size="icon"
            variant="ghost"
            :aria-label="`删除技能 ${index + 1}`"
            @click="removeAt(draft.skills, index)"
          >
            <Trash2 class="size-4" aria-hidden="true" />
          </Button>
        </div>
      </div>
    </section>

    <section class="resume-editor__section">
      <div class="resume-editor__section-title">
        <h4>工作经历</h4>
        <Button size="sm" variant="secondary" @click="draft.experiences.push(emptyExperience())">
          <Plus class="size-4" aria-hidden="true" />新增经历
        </Button>
      </div>
      <article
        v-for="(experience, experienceIndex) in draft.experiences"
        :key="experienceIndex"
        class="resume-editor__entry"
      >
        <div class="resume-editor__entry-header">
          <h5>经历 {{ experienceIndex + 1 }}</h5>
          <Button
            size="icon"
            variant="ghost"
            :aria-label="`删除经历 ${experienceIndex + 1}`"
            @click="removeAt(draft.experiences, experienceIndex)"
          >
            <Trash2 class="size-4" aria-hidden="true" />
          </Button>
        </div>
        <div class="resume-editor__grid">
          <label><span>公司</span><Input v-model="experience.company" /></label>
          <label><span>职位</span><Input v-model="experience.title" /></label>
          <label><span>开始时间</span><Input v-model="experience.start" /></label>
          <label><span>结束时间</span><Input v-model="experience.end" /></label>
        </div>
        <div class="resume-editor__bullets">
          <label v-for="(_, bulletIndex) in experience.bullets" :key="bulletIndex">
            <span>工作要点 {{ bulletIndex + 1 }}</span>
            <div class="resume-editor__bullet-row">
              <Textarea v-model="experience.bullets[bulletIndex]" rows="3" />
              <Button
                size="icon"
                variant="ghost"
                :aria-label="`删除工作要点 ${bulletIndex + 1}`"
                @click="removeAt(experience.bullets, bulletIndex)"
              >
                <Trash2 class="size-4" aria-hidden="true" />
              </Button>
            </div>
          </label>
          <Button size="sm" variant="secondary" @click="addBullet(experience.bullets)">
            <Plus class="size-4" aria-hidden="true" />新增工作要点
          </Button>
        </div>
      </article>
    </section>

    <section class="resume-editor__section">
      <div class="resume-editor__section-title">
        <h4>项目经历</h4>
        <Button size="sm" variant="secondary" @click="draft.projects.push(emptyProject())">
          <Plus class="size-4" aria-hidden="true" />新增项目
        </Button>
      </div>
      <article
        v-for="(project, projectIndex) in draft.projects"
        :key="projectIndex"
        class="resume-editor__entry"
      >
        <div class="resume-editor__entry-header">
          <h5>项目 {{ projectIndex + 1 }}</h5>
          <Button
            size="icon"
            variant="ghost"
            :aria-label="`删除项目 ${projectIndex + 1}`"
            @click="removeAt(draft.projects, projectIndex)"
          >
            <Trash2 class="size-4" aria-hidden="true" />
          </Button>
        </div>
        <div class="resume-editor__grid">
          <label><span>项目名称</span><Input v-model="project.name" /></label>
          <label><span>承担角色</span><Input v-model="project.role" /></label>
          <label class="resume-editor__grid-wide">
            <span>技术栈（逗号分隔）</span>
            <Input
              :model-value="project.techStack.join(', ')"
              @update:model-value="
                project.techStack = String($event)
                  .split(/[,，]/)
                  .map((item) => item.trim())
                  .filter(Boolean)
              "
            />
          </label>
        </div>
        <div class="resume-editor__bullets">
          <label v-for="(_, bulletIndex) in project.bullets" :key="bulletIndex">
            <span>项目要点 {{ bulletIndex + 1 }}</span>
            <div class="resume-editor__bullet-row">
              <Textarea v-model="project.bullets[bulletIndex]" rows="3" />
              <Button
                size="icon"
                variant="ghost"
                :aria-label="`删除项目要点 ${bulletIndex + 1}`"
                @click="removeAt(project.bullets, bulletIndex)"
              >
                <Trash2 class="size-4" aria-hidden="true" />
              </Button>
            </div>
          </label>
          <Button size="sm" variant="secondary" @click="addBullet(project.bullets)">
            <Plus class="size-4" aria-hidden="true" />新增项目要点
          </Button>
        </div>
        <label class="resume-editor__wide-field">
          <span>项目成果</span>
          <Textarea v-model="project.outcome" rows="3" />
        </label>
      </article>
    </section>

    <section class="resume-editor__section">
      <div class="resume-editor__section-title">
        <h4>教育经历</h4>
        <Button size="sm" variant="secondary" @click="draft.education.push(emptyEducation())">
          <Plus class="size-4" aria-hidden="true" />新增教育经历
        </Button>
      </div>
      <div class="resume-editor__rows">
        <div
          v-for="(education, index) in draft.education"
          :key="index"
          class="resume-editor__education-row"
        >
          <Input v-model="education.school" placeholder="学校" />
          <Input v-model="education.degree" placeholder="学历或专业" />
          <Input v-model="education.end" placeholder="毕业时间" />
          <Button
            size="icon"
            variant="ghost"
            :aria-label="`删除教育经历 ${index + 1}`"
            @click="removeAt(draft.education, index)"
          >
            <Trash2 class="size-4" aria-hidden="true" />
          </Button>
        </div>
      </div>
    </section>
  </div>
</template>

<style scoped>
.resume-editor {
  display: grid;
  gap: var(--spacing-lg);
  padding: var(--spacing-xl);
  border-radius: var(--radius-lg);
  background: var(--color-surface);
}
.resume-editor__header,
.resume-editor__section-title,
.resume-editor__entry-header,
.resume-editor__actions {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--spacing-md);
}
.resume-editor h3,
.resume-editor h4,
.resume-editor h5 {
  margin: 0;
  color: var(--color-text-primary);
  font-family: var(--font-serif);
}
.resume-editor__actions {
  justify-content: flex-end;
}
.resume-editor__section {
  display: grid;
  gap: var(--spacing-md);
  padding-top: var(--spacing-lg);
  border-top: 1px solid var(--color-border);
}
.resume-editor__grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: var(--spacing-md);
}
.resume-editor label,
.resume-editor__bullets {
  display: grid;
  gap: var(--spacing-xs);
  min-inline-size: 0;
}
.resume-editor label > span {
  color: var(--color-text-secondary);
  font-size: var(--font-size-xs);
}
.resume-editor__wide-field,
.resume-editor__grid-wide {
  grid-column: 1 / -1;
}
.resume-editor__rows,
.resume-editor__bullets {
  gap: var(--spacing-sm);
}
.resume-editor__row,
.resume-editor__education-row,
.resume-editor__bullet-row {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(0, 1fr) auto;
  align-items: start;
  gap: var(--spacing-sm);
}
.resume-editor__education-row {
  grid-template-columns: repeat(3, minmax(0, 1fr)) auto;
}
.resume-editor__bullet-row {
  grid-template-columns: minmax(0, 1fr) auto;
}
.resume-editor__entry {
  display: grid;
  gap: var(--spacing-md);
  padding: var(--spacing-lg);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  background: var(--color-surface);
}
@media (max-width: 45rem) {
  .resume-editor,
  .resume-editor__entry {
    padding: var(--spacing-md);
  }
  .resume-editor__header,
  .resume-editor__section-title {
    align-items: flex-start;
    flex-direction: column;
  }
  .resume-editor__grid,
  .resume-editor__row,
  .resume-editor__education-row {
    grid-template-columns: minmax(0, 1fr) auto;
  }
  .resume-editor__row > :first-child,
  .resume-editor__education-row > :first-child {
    grid-column: 1 / -1;
  }
}
</style>
