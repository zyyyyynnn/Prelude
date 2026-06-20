<script setup lang="ts">
/**
 * Component Lab — dev-only playground for inspecting component states.
 *
 * This view is NOT registered in production builds (see router/index.ts).
 * It exists so designers / engineers can review how a component behaves
 * across variants, sizes, loading / disabled / error states without
 * running the real flow. Use Phase 1 visual regression and Phase 2 a11y
 * checks to keep this surface honest.
 */
import { ref } from 'vue'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Badge } from '@/components/ui/badge'
import { Card, CardHeader, CardTitle, CardContent } from '@/components/ui/card'
import SegmentedControl from '@/components/ui/segmented-control/SegmentedControl.vue'
import {
  DropdownMenu,
  DropdownMenuTrigger,
  DropdownMenuContent,
  DropdownMenuItem,
} from '@/components/ui/dropdown-menu'
import {
  Select,
  SelectTrigger,
  SelectValue,
  SelectContent,
  SelectItem,
} from '@/components/ui/select'
import {
  Dialog,
  DialogTrigger,
  DialogContent,
  DialogHeader,
  DialogTitle,
  DialogDescription,
} from '@/components/ui/dialog'
import { TooltipProvider, Tooltip, TooltipTrigger, TooltipContent } from '@/components/ui/tooltip'
import EmptyState from '@/components/ui/empty-state/EmptyState.vue'
import ComponentLabSection from '@/components/lab/ComponentLabSection.vue'
import { Bell } from '@lucide/vue'

const buttonVariantChoices = [
  'default',
  'secondary',
  'outline',
  'ghost',
  'destructive',
  'link',
] as const

const buttonSizeChoices = ['default', 'sm', 'compact'] as const

const segmentedItems = ['概览', '设置', '历史', '评价']
const segmentedValue = ref(segmentedItems[0])

const dropdownValue = ref('Java 后端工程师')
const dropdownOptions = ['Java 后端工程师', '前端工程师', '算法工程师']

const selectValue = ref('openai-compatible')
const selectOptions = [
  { value: 'openai-compatible', label: 'OpenAI 兼容协议' },
  { value: 'deepseek', label: 'DeepSeek' },
]

const inputValue = ref('')
const inputDisabled = ref(true)
const inputErrorLike = ref('')

const dialogOpen = ref(false)
</script>

<template>
  <div class="lab">
    <header class="lab__header">
      <h1 class="lab__title">Component Lab</h1>
      <p class="lab__subtitle">
        开发态组件状态矩阵。仅 <code>import.meta.env.DEV</code> 为 true 时注册；生产构建会被 Vite tree-shake 掉。
      </p>
      <p class="lab__subtitle">
        用途：设计师 / 工程师在此 review Button / Input / SegmentedControl / Dropdown / Dialog
        / Tooltip 等组件在不同状态下的视觉与 a11y 表现。变更需同步通过 <code>verify:visual</code> /
        <code>verify:a11y</code> / <code>verify:ui</code>。
      </p>
    </header>

    <ComponentLabSection><template #heading>`Button`</template><template #description>`variant × size × loading × disabled`</template>
      <div v-for="variant in buttonVariantChoices" :key="variant" class="lab__row">
        <div class="lab__row-label">{{ variant }}</div>
        <div class="lab__row-cells">
          <Button v-for="size in buttonSizeChoices" :key="`${variant}-${size}`" :variant="variant" :size="size">
            {{ size }}
          </Button>
          <Button :variant="variant" size="compact" loading>
            loading
          </Button>
          <Button :variant="variant" size="compact" disabled>
            disabled
          </Button>
        </div>
      </div>
    </ComponentLabSection>

    <ComponentLabSection><template #heading>`Input`</template><template #description>`default / focus / disabled / error-like hint`</template>
      <div class="lab__row">
        <div class="lab__row-cells">
          <div class="lab__cell">
            <Label for="lab-input-default">default</Label>
            <Input id="lab-input-default" v-model="inputValue" placeholder="placeholder text" />
          </div>
          <div class="lab__cell">
            <Label for="lab-input-disabled">disabled</Label>
            <Input
              id="lab-input-disabled"
              :model-value="inputDisabled ? 'disabled field' : ''"
              disabled
            />
          </div>
          <div class="lab__cell">
            <Label for="lab-input-error">error-like hint</Label>
            <Input id="lab-input-error" v-model="inputErrorLike" placeholder="error style" class="border-error" />
            <span class="lab__hint">hint: API Key 格式不正确</span>
          </div>
        </div>
      </div>
    </ComponentLabSection>

    <ComponentLabSection><template #heading>`Select / DropdownMenu`</template><template #description>`closed / open · compact / default · long label`</template>
      <div class="lab__row">
        <div class="lab__row-cells">
          <div class="lab__cell">
            <Label>Select</Label>
            <Select v-model="selectValue">
              <SelectTrigger aria-label="实验室 Select">
                <SelectValue placeholder="Select 选择" />
              </SelectTrigger>
              <SelectContent>
                <SelectItem v-for="opt in selectOptions" :key="opt.value" :value="opt.value">
                  {{ opt.label }}
                </SelectItem>
              </SelectContent>
            </Select>
          </div>
          <div class="lab__cell">
            <Label>DropdownMenu</Label>
            <DropdownMenu>
              <DropdownMenuTrigger as-child>
                <Button variant="outline">{{ dropdownValue }}</Button>
              </DropdownMenuTrigger>
              <DropdownMenuContent>
                <DropdownMenuItem
                  v-for="opt in dropdownOptions"
                  :key="opt"
                  @click="dropdownValue = opt"
                >
                  {{ opt }}
                </DropdownMenuItem>
              </DropdownMenuContent>
            </DropdownMenu>
          </div>
          <div class="lab__cell">
            <Label>long label</Label>
            <Button variant="secondary" class="!max-w-[16rem]">
              非常长的按钮文案：会话结束后报告生成过程需要异步任务，请稍候
            </Button>
          </div>
        </div>
      </div>
    </ComponentLabSection>

    <ComponentLabSection><template #heading>`Dialog`</template><template #description>`settings-like modal · 与 GlobalSettingsModal 同源`</template>
      <div class="lab__row">
        <div class="lab__row-cells">
          <Dialog v-model:open="dialogOpen">
            <DialogTrigger as-child>
              <Button variant="default">打开示例 Dialog</Button>
            </DialogTrigger>
            <DialogContent class="!max-w-md">
              <DialogHeader>
                <DialogTitle>示例 Dialog</DialogTitle>
                <DialogDescription>
                  用于检查 Dialog 在 Phase 1 视觉回归 + Phase 2 a11y 验证中的表现。
                </DialogDescription>
              </DialogHeader>
              <p class="text-sm text-muted-foreground">
                按 Escape 关闭，或点击外部区域（默认 reka-ui 行为）。
              </p>
            </DialogContent>
          </Dialog>
        </div>
      </div>
    </ComponentLabSection>

    <ComponentLabSection><template #heading>`Tooltip`</template><template #description>`hover / focus trigger；不依赖原生 title`</template>
      <div class="lab__row">
        <div class="lab__row-cells">
          <TooltipProvider>
            <Tooltip>
              <TooltipTrigger as-child>
                <Button variant="outline" aria-label="通知">
                  <Bell class="size-4" />
                </Button>
              </TooltipTrigger>
              <TooltipContent>hover / focus 触发，3 行内容不溢出</TooltipContent>
            </Tooltip>
          </TooltipProvider>
          <TooltipProvider>
            <Tooltip>
              <TooltipTrigger as-child>
                <span class="lab__link" tabindex="0">长文本悬浮</span>
              </TooltipTrigger>
              <TooltipContent class="!max-w-xs">
                Tooltip 用于补充信息；不应承载关键操作。
              </TooltipContent>
            </Tooltip>
          </TooltipProvider>
        </div>
      </div>
    </ComponentLabSection>

    <ComponentLabSection><template #heading>`Badge`</template><template #description>`默认 / destructive / secondary / outline`</template>
      <div class="lab__row">
        <div class="lab__row-cells">
          <Badge>默认</Badge>
          <Badge variant="destructive">destructive</Badge>
          <Badge variant="secondary">secondary</Badge>
          <Badge variant="outline">outline</Badge>
        </div>
      </div>
    </ComponentLabSection>

    <ComponentLabSection><template #heading>`Card / EmptyState`</template><template #description>`包装容器 + 空态`</template>
      <div class="lab__row">
        <div class="lab__row-cells">
          <Card class="!w-72">
            <CardHeader>
              <CardTitle>Card 标题</CardTitle>
            </CardHeader>
            <CardContent>
              <p class="text-sm text-muted-foreground">Card 容器，展示标题 + 描述 + 操作的组合。</p>
            </CardContent>
          </Card>
          <Card class="!w-72">
            <EmptyState description="暂无数据：Phase 3 中用来检查空态的视觉与 a11y。" />
          </Card>
        </div>
      </div>
    </ComponentLabSection>

    <ComponentLabSection><template #heading>`SegmentedControl`</template><template #description>`item-count sizing model · 1 / 2 / 3 / long label`</template>
      <div class="lab__row">
        <div class="lab__row-cells">
          <div class="lab__cell">
            <Label>1 item</Label>
            <SegmentedControl :items="['独项']" :model-value="'独项'" />
          </div>
          <div class="lab__cell">
            <Label>2 items</Label>
            <SegmentedControl :items="['技术', '行为']" v-model="segmentedValue" />
          </div>
          <div class="lab__cell">
            <Label>3 items</Label>
            <SegmentedControl :items="['A', 'B', 'C']" v-model="segmentedValue" />
          </div>
          <div class="lab__cell">
            <Label>long label</Label>
            <SegmentedControl
              :items="['结构化输出', '异步队列', '可访问性', '视觉回归']"
              v-model="segmentedValue"
            />
          </div>
        </div>
      </div>
    </ComponentLabSection>

    <ComponentLabSection><template #heading>`Workspace excerpt`</template><template #description>`Sidebar expanded · collapsed`</template>
      <div class="lab__row">
        <div class="lab__row-cells">
          <div class="lab__cell">
            <Label>expanded</Label>
            <Button variant="outline" @click="$router.push('/interview')">
              打开工作区
            </Button>
          </div>
          <div class="lab__cell">
            <Label>collapsed</Label>
            <span class="lab__hint">在 /interview 路由点击 sidebar 折叠按钮即可切换</span>
          </div>
        </div>
      </div>
    </ComponentLabSection>

    <ComponentLabSection><template #heading>`Message bubble`</template><template #description>`user · assistant · judge feedback（语义化 token 引用）`</template>
      <div class="lab__row">
        <div class="lab__row-cells">
          <div class="lab__bubble lab__bubble--user">用户消息</div>
          <div class="lab__bubble lab__bubble--assistant">助手消息</div>
          <div class="lab__bubble lab__bubble--judge">
            <Badge variant="secondary" class="!mr-2">7/10</Badge>
            LLM-as-Judge 反馈
          </div>
        </div>
      </div>
    </ComponentLabSection>
  </div>
</template>

<style scoped>
.lab {
  padding: var(--spacing-2xl);
  max-inline-size: var(--layout-workspace-content-max-inline-size);
  margin: 0 auto;
  display: flex;
  flex-direction: column;
  gap: var(--spacing-2xl);
}
.lab__header {
  display: flex;
  flex-direction: column;
  gap: var(--spacing-sm);
}
.lab__title {
  margin: 0;
  font-family: var(--font-serif);
  font-size: var(--font-size-xl);
  font-weight: 500;
  color: var(--color-text-primary);
}
.lab__subtitle {
  margin: 0;
  color: var(--color-text-secondary);
  font-size: var(--font-size-sm);
  line-height: 1.6;
}
.lab__subtitle code {
  font-family: var(--font-mono);
  font-size: 0.95em;
  padding: 0 var(--spacing-0-5);
  border-radius: var(--radius-sm);
  background: var(--color-surface);
  color: var(--color-text-primary);
}
.lab__row {
  display: grid;
  grid-template-columns: 8rem 1fr;
  gap: var(--spacing-md);
  align-items: start;
}
.lab__row-label {
  font-family: var(--font-serif);
  color: var(--color-text-secondary);
  font-size: var(--font-size-sm);
  padding-block: var(--spacing-1-5);
}
.lab__row-cells {
  display: flex;
  flex-wrap: wrap;
  gap: var(--spacing-md);
  align-items: center;
}
.lab__cell {
  display: flex;
  flex-direction: column;
  gap: var(--spacing-xs);
  min-inline-size: 12rem;
}
.lab__hint {
  color: var(--color-text-tertiary);
  font-size: var(--font-size-xs);
}
.lab__link {
  color: var(--color-brand);
  cursor: pointer;
  text-decoration: underline;
  padding: var(--spacing-xs);
}
.lab__bubble {
  padding: var(--spacing-sm) var(--spacing-md);
  border-radius: var(--radius-lg);
  border: 1px solid var(--color-border);
  background: var(--color-surface);
  font-size: var(--font-size-sm);
  max-inline-size: min(80%, var(--content-message-max-inline-size));
}
.lab__bubble--user {
  background: var(--color-surface-muted);
  align-self: flex-end;
  margin-left: auto;
}
.lab__bubble--assistant {
  align-self: flex-start;
  margin-right: auto;
}
.lab__bubble--judge {
  border-color: var(--color-brand);
}
</style>
