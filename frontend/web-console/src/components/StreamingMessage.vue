<template>
  <article class="message">
    <header>
      <strong>{{ role === 'assistant' ? 'AI 助手' : '用户' }}</strong>
      <span v-if="isStreaming" class="cursor">|</span>
    </header>
    <p>{{ content }}</p>
    <ReferenceCard v-for="ref in references" :key="ref.id" :reference="ref" />
  </article>
</template>

<script setup lang="ts">
import ReferenceCard from './ReferenceCard.vue';

defineProps<{
  role: 'user' | 'assistant';
  content: string;
  isStreaming: boolean;
  references?: Array<{ id: string; title: string; snippet: string; score?: number }>;
}>();
</script>

<style scoped>
.message { background: #fff; border: 1px solid #e4e7ed; padding: 12px; display: grid; gap: 8px; }
header { display: flex; gap: 8px; align-items: center; }
p { margin: 0; line-height: 1.7; }
.cursor { color: #409eff; }
</style>
