<template>
  <footer class="chat-input">
    <el-input
      v-model="input"
      type="textarea"
      :rows="3"
      :disabled="loading"
      placeholder="输入报告问题或补充要求"
      @keydown.enter.exact.prevent="submit"
    />
    <el-button type="primary" :loading="loading" :disabled="!input.trim()" @click="submit">发送</el-button>
  </footer>
</template>

<script setup lang="ts">
import { ref } from 'vue';

const props = defineProps<{ loading: boolean }>();
const emit = defineEmits<{ submit: [text: string] }>();
const input = ref('');

const submit = () => {
  if (!input.value.trim() || props.loading) return;
  emit('submit', input.value.trim());
  input.value = '';
};
</script>

<style scoped>
.chat-input { display: grid; grid-template-columns: 1fr auto; gap: 12px; align-items: end; }
</style>
