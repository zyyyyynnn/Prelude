<template>
  <svg
    viewBox="0 0 100 100"
    fill="none"
    aria-hidden="true"
    :class="['rose-three-loader', props.class]"
  >
    <g ref="groupRef">
      <path
        ref="pathRef"
        stroke="currentColor"
        stroke-linecap="round"
        stroke-linejoin="round"
        opacity="0.1"
      />
      <circle v-for="i in config.particleCount" :key="i" ref="particleRefs" fill="currentColor" />
    </g>
  </svg>
</template>

<script setup lang="ts">
import { ref, onMounted, onBeforeUnmount } from 'vue'

const props = withDefaults(
  defineProps<{
    class?: string
    /** * 速度乘数，用于适配统一的 Motion Token。
     * > 1 更快，< 1 更慢，默认 1
     */
    speedMultiplier?: number
  }>(),
  {
    class: '',
    speedMultiplier: 1,
  },
)

const groupRef = ref<SVGGElement | null>(null)
const pathRef = ref<SVGPathElement | null>(null)
const particleRefs = ref<SVGCircleElement[]>([])

// 核心参数配置
const config = {
  rotate: true,
  particleCount: 76,
  trailSpan: 0.31,
  durationMs: 5300,
  rotationDurationMs: 28000,
  pulseDurationMs: 4400,
  strokeWidth: 4.6,
  roseA: 9.2,
  roseABoost: 0.6,
  roseBreathBase: 0.72,
  roseBreathBoost: 0.28,
  roseScale: 3.25,
}

let animationFrameId: number
let startedAt: number

// 数学曲线与点位计算
function point(progress: number, detailScale: number) {
  const t = progress * Math.PI * 2
  const a = config.roseA + detailScale * config.roseABoost
  const r = a * (config.roseBreathBase + detailScale * config.roseBreathBoost) * Math.cos(3 * t)
  return {
    x: 50 + Math.cos(t) * r * config.roseScale,
    y: 50 + Math.sin(t) * r * config.roseScale,
  }
}

function normalizeProgress(progress: number) {
  return ((progress % 1) + 1) % 1
}

function getDetailScale(time: number) {
  const adjustedDuration = config.pulseDurationMs / props.speedMultiplier
  const pulseProgress = (time % adjustedDuration) / adjustedDuration
  const pulseAngle = pulseProgress * Math.PI * 2
  return 0.52 + ((Math.sin(pulseAngle + 0.55) + 1) / 2) * 0.48
}

function getRotation(time: number) {
  if (!config.rotate) return 0
  const adjustedDuration = config.rotationDurationMs / props.speedMultiplier
  return -((time % adjustedDuration) / adjustedDuration) * 360
}

function buildPath(detailScale: number, steps = 240) {
  let d = ''
  for (let index = 0; index <= steps; index++) {
    const p = point(index / steps, detailScale)
    d += `${index === 0 ? 'M' : 'L'} ${p.x.toFixed(2)} ${p.y.toFixed(2)} `
  }
  return d
}

function getParticle(index: number, progress: number, detailScale: number) {
  const tailOffset = index / (config.particleCount - 1)
  const p = point(normalizeProgress(progress - tailOffset * config.trailSpan), detailScale)
  const fade = Math.pow(1 - tailOffset, 0.56)
  return {
    x: p.x,
    y: p.y,
    radius: 0.9 + fade * 2.7,
    opacity: 0.04 + fade * 0.96,
  }
}

// 渲染循环（直接操作 DOM 绕过 Vue 响应式以保障 60fps）
function render(now: number) {
  if (!startedAt) startedAt = now
  const time = now - startedAt

  const adjustedDuration = config.durationMs / props.speedMultiplier
  const progress = (time % adjustedDuration) / adjustedDuration
  const detailScale = getDetailScale(time)

  if (groupRef.value) {
    groupRef.value.setAttribute('transform', `rotate(${getRotation(time).toFixed(1)} 50 50)`)
  }
  if (pathRef.value) {
    pathRef.value.setAttribute('d', buildPath(detailScale))
  }

  const particles = particleRefs.value
  for (let i = 0; i < particles.length; i++) {
    const node = particles[i]
    if (!node) continue
    const particle = getParticle(i, progress, detailScale)
    node.setAttribute('cx', particle.x.toFixed(2))
    node.setAttribute('cy', particle.y.toFixed(2))
    node.setAttribute('r', particle.radius.toFixed(2))
    node.setAttribute('opacity', particle.opacity.toFixed(3))
  }

  animationFrameId = requestAnimationFrame(render)
}

onMounted(() => {
  if (pathRef.value) {
    pathRef.value.setAttribute('stroke-width', String(config.strokeWidth))
  }
  animationFrameId = requestAnimationFrame(render)
})

onBeforeUnmount(() => {
  cancelAnimationFrame(animationFrameId)
})
</script>

<style scoped>
.rose-three-loader {
  /* 基础宽高设为 1em，外部通过 Tailwind w-x h-x 类进行覆盖和控制 */
  width: 1em;
  height: 1em;
  display: inline-block;
  overflow: visible;
  /* 平滑缩放时的抗锯齿处理 */
  will-change: transform;
}
</style>
