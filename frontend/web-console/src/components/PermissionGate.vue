<template>
  <slot v-if="allowed" />
  <el-alert v-else type="warning" title="当前账号无权执行该操作" show-icon />
</template>

<script setup lang="ts">
defineProps<{ allowed: boolean }>();
</script>
