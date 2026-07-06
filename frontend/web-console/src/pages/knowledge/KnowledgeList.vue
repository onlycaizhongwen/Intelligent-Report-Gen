<template>
  <section class="page">
    <header class="toolbar">
      <h2>知识库</h2>
      <el-input v-model="keyword" placeholder="搜索知识条目" clearable @change="loadItems" />
    </header>

    <section class="import-panel" aria-labelledby="knowledge-import-title">
      <h3 id="knowledge-import-title">批量导入知识条目</h3>
      <div class="import-controls">
        <el-input v-model="knowledgeBaseId" aria-label="知识库 ID" placeholder="知识库 ID" />
        <el-input
          v-model="batchText"
          type="textarea"
          :rows="4"
          aria-label="批量导入内容"
          placeholder="每行一条：标题,内容,来源类型"
        />
        <el-button type="primary" :loading="importing" @click="batchImport">导入</el-button>
      </div>
      <p v-if="importSummary" class="import-summary">{{ importSummary }}</p>
    </section>

    <el-alert
      v-if="deleteWarning"
      :title="deleteWarning"
      type="warning"
      show-icon
      :closable="false"
      class="delete-alert"
    >
      <template #default>
        <el-button type="danger" size="small" @click="confirmDelete">确认删除</el-button>
      </template>
    </el-alert>

    <el-table :data="rows" v-loading="loading" empty-text="暂无知识条目">
      <el-table-column prop="title" label="标题" min-width="180" />
      <el-table-column prop="sourceType" label="来源类型" width="140" />
      <el-table-column prop="indexStatus" label="索引状态" width="140" />
      <el-table-column label="操作" width="160">
        <template #default="{ row }">
          <el-button link>查看</el-button>
          <el-button link type="danger" @click="deleteItem(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>
  </section>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue';
import { ElMessage } from 'element-plus';
import { knowledgeApi } from '../../api/knowledgeApi';

interface KnowledgeItemRow {
  itemId: string | number;
  title: string;
  sourceType?: string;
  indexStatus?: string;
}

const keyword = ref('');
const rows = ref<KnowledgeItemRow[]>([]);
const loading = ref(false);
const importing = ref(false);
const pendingDelete = ref<KnowledgeItemRow | null>(null);
const deleteWarning = ref('');
const knowledgeBaseId = ref('1');
const batchText = ref('');
const importSummary = ref('');

onMounted(loadItems);

async function loadItems() {
  loading.value = true;
  try {
    const page = await knowledgeApi.listItems({ page: 1, pageSize: 20, keyword: keyword.value });
    rows.value = (page as { items?: KnowledgeItemRow[] }).items ?? [];
  } finally {
    loading.value = false;
  }
}

async function batchImport() {
  const items = parseBatchText(batchText.value);
  if (items.length === 0) {
    ElMessage.warning('请输入要导入的知识条目');
    return;
  }
  importing.value = true;
  try {
    const result = await knowledgeApi.batchImportItems({ knowledgeBaseId: knowledgeBaseId.value, items });
    importSummary.value = `导入 ${result.imported} 条，失败 ${result.failed} 条`;
    batchText.value = '';
    await loadItems();
  } finally {
    importing.value = false;
  }
}

function parseBatchText(text: string) {
  return text
    .split(/\r?\n/)
    .map((line) => line.trim())
    .filter(Boolean)
    .map((line) => {
      const [title = '', content = '', sourceType = 'batch_import'] = line.split(',').map((part) => part.trim());
      return { title, content, sourceType };
    });
}

async function deleteItem(row: KnowledgeItemRow) {
  pendingDelete.value = row;
  deleteWarning.value = '';
  try {
    const result = await knowledgeApi.deleteItem(row.itemId, false);
    afterDeleted(result.referenceCount ?? 0);
  } catch (error) {
    deleteWarning.value = error instanceof Error ? error.message : '知识条目已被报告引用，请确认影响后再删除';
  }
}

async function confirmDelete() {
  if (!pendingDelete.value) return;
  const result = await knowledgeApi.deleteItem(pendingDelete.value.itemId, true);
  afterDeleted(result.referenceCount ?? 0);
}

function afterDeleted(referenceCount: number) {
  const itemId = pendingDelete.value?.itemId;
  rows.value = rows.value.filter((item) => item.itemId !== itemId);
  pendingDelete.value = null;
  deleteWarning.value = '';
  ElMessage.success(`已删除知识条目，历史引用数：${referenceCount}`);
}
</script>

<style scoped>
.toolbar {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 16px;
}

.toolbar .el-input {
  max-width: 320px;
}

.import-panel {
  margin-bottom: 16px;
}

.import-panel h3 {
  margin: 0 0 8px;
  font-size: 16px;
  font-weight: 600;
}

.import-controls {
  display: grid;
  grid-template-columns: minmax(120px, 160px) 1fr auto;
  gap: 12px;
  align-items: start;
}

.import-summary {
  margin: 8px 0 0;
  color: #2f6f4e;
}

.delete-alert {
  margin-bottom: 16px;
}

@media (max-width: 760px) {
  .toolbar,
  .import-controls {
    grid-template-columns: 1fr;
    display: grid;
  }

  .toolbar .el-input {
    max-width: none;
  }
}
</style>
