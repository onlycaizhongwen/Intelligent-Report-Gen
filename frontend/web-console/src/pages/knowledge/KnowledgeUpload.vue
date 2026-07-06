<template>
  <section class="page">
    <h2>文档上传</h2>
    <el-select
      v-model="selectedKnowledgeBaseId"
      class="knowledge-base-select"
      placeholder="请选择知识库"
      :disabled="uploading || loadingKnowledgeBases || !knowledgeBaseOptions.length"
    >
      <el-option
        v-for="item in knowledgeBaseOptions"
        :key="item.knowledgeBaseId"
        :label="item.name"
        :value="String(item.knowledgeBaseId)"
      />
    </el-select>
    <DocumentUpload :loading="uploading" @upload="upload" />
    <el-alert
      v-if="statusText"
      :title="statusText"
      :description="failureReason"
      :type="alertType"
      :closable="false"
    />
    <el-alert
      v-else-if="knowledgeBaseError"
      title="知识库加载失败"
      :description="knowledgeBaseError"
      type="error"
      :closable="false"
    />
  </section>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import DocumentUpload from '../../components/DocumentUpload.vue';
import { knowledgeApi, type KnowledgeBaseSummary, type UploadedDocumentResult } from '../../api/knowledgeApi';

const uploading = ref(false);
const statusText = ref('');
const failureReason = ref('');
const currentStatus = ref('');
const knowledgeBaseOptions = ref<KnowledgeBaseSummary[]>([]);
const selectedKnowledgeBaseId = ref('');
const loadingKnowledgeBases = ref(false);
const knowledgeBaseError = ref('');
const alertType = computed(() => (currentStatus.value === 'failed' ? 'error' : 'success'));

const updateStatus = (result: UploadedDocumentResult) => {
  currentStatus.value = result.parseStatus || result.status || 'pending';
  statusText.value = `${result.filename || ''} ${currentStatus.value}`.trim();
  failureReason.value = result.parseFailureReason || '';
};

const wait = (ms: number) => new Promise((resolve) => window.setTimeout(resolve, ms));

const loadKnowledgeBases = async () => {
  loadingKnowledgeBases.value = true;
  knowledgeBaseError.value = '';
  try {
    const response = await knowledgeApi.listKnowledgeBases({ page: 1, pageSize: 20 });
    knowledgeBaseOptions.value = response.items || [];
    if (!selectedKnowledgeBaseId.value && knowledgeBaseOptions.value.length > 0) {
      selectedKnowledgeBaseId.value = String(knowledgeBaseOptions.value[0].knowledgeBaseId);
    }
  } catch (error) {
    knowledgeBaseError.value = error instanceof Error ? error.message : '无法加载知识库列表';
  } finally {
    loadingKnowledgeBases.value = false;
  }
};

const pollDocumentStatus = async (documentId: string | number) => {
  for (let attempt = 0; attempt < 20; attempt += 1) {
    await wait(300);
    const result = await knowledgeApi.getDocumentStatus(documentId);
    updateStatus(result);
    if (['processed', 'failed'].includes(currentStatus.value)) return;
  }
};

const upload = async (files: File[]) => {
  if (!selectedKnowledgeBaseId.value) {
    currentStatus.value = 'failed';
    statusText.value = '未选择知识库';
    failureReason.value = '请先选择一个可用知识库后再上传文档。';
    return;
  }

  const formData = new FormData();
  formData.append('file', files[0]);
  uploading.value = true;
  try {
    const result = await knowledgeApi.uploadDocument(formData, selectedKnowledgeBaseId.value);
    updateStatus(result);
    if (result.documentId && currentStatus.value === 'pending') {
      await pollDocumentStatus(result.documentId);
    }
  } finally {
    uploading.value = false;
  }
};

onMounted(() => {
  void loadKnowledgeBases();
});
</script>

<style scoped>
.knowledge-base-select {
  width: 320px;
  margin-bottom: 16px;
}
</style>
