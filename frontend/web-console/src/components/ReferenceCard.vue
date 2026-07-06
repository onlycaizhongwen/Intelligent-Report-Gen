<template>
  <el-card shadow="never" class="reference">
    <template #header>
      <span>{{ reference.title }}</span>
      <el-tag v-if="reference.score !== undefined" size="small">{{ reference.score }}</el-tag>
    </template>
    <p>{{ reference.snippet }}</p>
  </el-card>
</template>

<script setup lang="ts">
defineProps<{
  reference: { id: string; title: string; snippet: string; score?: number };
}>();
</script>

<style scoped>
.reference { border-radius: 6px; }
.reference :deep(.el-card__header) { display: flex; justify-content: space-between; align-items: center; }
p { margin: 0; color: #606266; }
</style>
