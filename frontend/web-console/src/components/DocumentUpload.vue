<template>
  <section
    class="upload"
    :class="{ active: dragOver }"
    @dragover.prevent="dragOver = true"
    @dragleave="dragOver = false"
    @drop.prevent="handleDrop"
  >
    <el-icon><UploadFilled /></el-icon>
    <strong>拖拽文件到此处</strong>
    <span>支持 PDF、Word、Excel、CSV、TXT、扫描件图片</span>
    <el-button :loading="loading" @click="inputRef?.click()">上传文件</el-button>
    <input ref="inputRef" class="hidden" type="file" @change="handleChange" />
  </section>
</template>

<script setup lang="ts">
import { ref } from 'vue';
import { UploadFilled } from '@element-plus/icons-vue';

defineProps<{ loading: boolean }>();
const emit = defineEmits<{ upload: [files: File[]] }>();
const dragOver = ref(false);
const inputRef = ref<HTMLInputElement>();

const emitFiles = (files: FileList | null) => {
  if (!files?.length) return;
  emit('upload', Array.from(files));
};

const handleDrop = (event: DragEvent) => {
  dragOver.value = false;
  emitFiles(event.dataTransfer?.files ?? null);
};

const handleChange = (event: Event) => {
  emitFiles((event.target as HTMLInputElement).files);
};
</script>

<style scoped>
.upload {
  min-height: 220px;
  border: 1px dashed #a8abb2;
  background: #fff;
  display: grid;
  place-items: center;
  gap: 8px;
  padding: 24px;
}
.active { border-color: #409eff; background: #f5faff; }
.hidden { display: none; }
.el-icon { font-size: 36px; color: #409eff; }
</style>
