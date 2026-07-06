<template>
  <el-skeleton v-if="loading" :rows="rows" animated />
  <el-alert v-else-if="error" type="error" :title="error" show-icon />
  <el-empty v-else-if="empty" :description="emptyText" />
  <slot v-else />
</template>

<script setup lang="ts">
defineProps<{
  loading?: boolean;
  error?: string;
  empty?: boolean;
  emptyText?: string;
  rows?: number;
}>();
</script>
