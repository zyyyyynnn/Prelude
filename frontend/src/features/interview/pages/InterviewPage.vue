<script setup lang="ts">
import InterviewWorkspace from '../components/InterviewWorkspace.vue'

const emit = defineEmits<{
  (event: 'open-global-settings', tab?: 'profile' | 'theme' | 'llm'): void
}>()
</script>

<template>
  <InterviewWorkspace @open-global-settings="(tab) => emit('open-global-settings', tab)" />
</template>
