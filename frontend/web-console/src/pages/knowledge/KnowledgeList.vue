<template>
  <section class="knowledge-page" data-testid="knowledge-page">
    <header class="knowledge-header">
      <div>
        <p class="eyebrow">管理中心 / 知识库</p>
        <h2>知识库管理</h2>
        <p class="subtitle">统一维护报告生成可引用的知识证据、索引状态与来源类型。</p>
      </div>
      <div class="header-actions">
        <el-button :icon="Upload" @click="router.push('/knowledge/upload')">文档上传</el-button>
        <el-button :icon="Connection" @click="router.push('/knowledge/data-sources')">数据源同步</el-button>
      </div>
    </header>

    <section class="metric-strip" aria-label="知识库指标">
      <div class="metric-tile">
        <span>知识条目</span>
        <strong>{{ rows.length }}</strong>
      </div>
      <div class="metric-tile">
        <span>已入索引</span>
        <strong>{{ indexedCount }}</strong>
      </div>
      <div class="metric-tile">
        <span>待处理</span>
        <strong>{{ pendingCount }}</strong>
      </div>
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

    <div class="knowledge-layout">
      <section class="import-card" data-testid="knowledge-import-panel" aria-labelledby="knowledge-import-title">
        <div class="panel-heading">
          <span class="panel-icon"><DocumentAdd /></span>
          <div>
            <h3 id="knowledge-import-title">批量导入</h3>
            <p>适合少量结构化证据快速录入。</p>
          </div>
        </div>

        <div class="import-form">
          <label class="field-label" for="knowledge-base-id">知识库编号</label>
          <el-input id="knowledge-base-id" v-model="knowledgeBaseId" aria-label="知识库编号" placeholder="例如：1" />

          <label class="field-label" for="batch-content">导入内容</label>
          <el-input
            id="batch-content"
            v-model="batchText"
            type="textarea"
            :rows="7"
            aria-label="批量导入内容"
            placeholder="每行一条：标题,内容,来源类型"
          />

          <p class="format-hint">来源类型可填手工录入、批量导入、文档解析或数据源同步；留空默认批量导入。</p>
          <el-button class="import-button" type="primary" :loading="importing" @click="batchImport">导入</el-button>
          <p v-if="importSummary" class="import-summary">{{ importSummary }}</p>
        </div>
      </section>

      <section class="list-card" data-testid="knowledge-list-panel" aria-labelledby="knowledge-list-title">
        <div class="list-toolbar">
          <div>
            <h3 id="knowledge-list-title">知识条目</h3>
            <p>按标题检索并维护可被报告引用的证据。</p>
          </div>
          <div class="list-actions">
            <el-input
              v-model="keyword"
              :prefix-icon="Search"
              placeholder="搜索知识条目"
              clearable
              @change="loadItems"
              @clear="loadItems"
            />
            <el-button :icon="Refresh" :loading="loading" @click="loadItems">刷新</el-button>
          </div>
        </div>

        <el-table class="knowledge-table" :data="rows" v-loading="loading">
          <el-table-column prop="title" label="标题" min-width="220" />
          <el-table-column label="来源类型" width="140">
            <template #default="{ row }">
              <el-tag size="small" :type="sourceTypeTag(row.sourceType)">{{ sourceTypeLabel(row.sourceType) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="索引状态" width="140">
            <template #default="{ row }">
              <el-tag size="small" :type="indexStatusTag(row.indexStatus)">{{ indexStatusLabel(row.indexStatus) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="160" fixed="right">
            <template #default="{ row }">
              <el-button link>查看</el-button>
              <el-button link type="danger" @click="deleteItem(row)">删除</el-button>
            </template>
          </el-table-column>
          <template #empty>
            <div class="empty-state">
              <strong>暂无知识条目</strong>
              <span>可通过左侧批量导入、文档上传或数据源同步补充知识。</span>
            </div>
          </template>
        </el-table>
      </section>
    </div>
  </section>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { useRouter } from 'vue-router';
import { ElMessage } from 'element-plus';
import { Connection, DocumentAdd, Refresh, Search, Upload } from '@element-plus/icons-vue';
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
const router = useRouter();

const indexedCount = computed(() => rows.value.filter((item) => item.indexStatus === 'indexed').length);
const pendingCount = computed(() => rows.value.filter((item) => item.indexStatus !== 'indexed').length);

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
      return { title, content, sourceType: normalizeSourceType(sourceType) };
    });
}

function normalizeSourceType(sourceType: string) {
  const labels: Record<string, string> = {
    手工录入: 'manual',
    批量导入: 'batch_import',
    文档解析: 'document',
    数据源同步: 'data_source'
  };
  return labels[sourceType] ?? sourceType;
}

function sourceTypeLabel(sourceType?: string) {
  const labels: Record<string, string> = {
    manual: '手工录入',
    batch_import: '批量导入',
    document: '文档解析',
    data_source: '数据源同步'
  };
  return sourceType ? (labels[sourceType] ?? sourceType) : '-';
}

function indexStatusLabel(status?: string) {
  const labels: Record<string, string> = {
    indexed: '已入索引',
    pending: '待索引',
    processing: '索引中',
    failed: '索引失败'
  };
  return status ? (labels[status] ?? status) : '-';
}

function sourceTypeTag(sourceType?: string) {
  const tags: Record<string, 'primary' | 'success' | 'warning' | 'info'> = {
    manual: 'primary',
    batch_import: 'success',
    document: 'warning',
    data_source: 'info'
  };
  return sourceType ? (tags[sourceType] ?? 'info') : 'info';
}

function indexStatusTag(status?: string) {
  const tags: Record<string, 'success' | 'warning' | 'info' | 'danger'> = {
    indexed: 'success',
    pending: 'warning',
    processing: 'info',
    failed: 'danger'
  };
  return status ? (tags[status] ?? 'info') : 'info';
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
.knowledge-page {
  min-height: 100vh;
  height: 100vh;
  overflow: auto;
  box-sizing: border-box;
  padding: 24px;
  background: #f4f6f8;
  color: #1f2937;
}

.knowledge-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 16px;
  padding: 20px 24px;
  margin-bottom: 16px;
  background: #ffffff;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
}

.eyebrow {
  margin: 0 0 6px;
  color: #64748b;
  font-size: 12px;
}

.knowledge-header h2 {
  margin: 0;
  font-size: 24px;
  line-height: 1.25;
  font-weight: 700;
}

.subtitle {
  margin: 8px 0 0;
  color: #64748b;
  font-size: 14px;
}

.header-actions {
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
  justify-content: flex-end;
}

.metric-strip {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
  margin-bottom: 16px;
}

.metric-tile {
  min-height: 74px;
  padding: 14px 16px;
  box-sizing: border-box;
  background: #ffffff;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  display: flex;
  flex-direction: column;
  justify-content: space-between;
}

.metric-tile span {
  color: #64748b;
  font-size: 13px;
}

.metric-tile strong {
  color: #111827;
  font-size: 24px;
  line-height: 1;
}

.knowledge-layout {
  display: grid;
  grid-template-columns: minmax(300px, 380px) minmax(0, 1fr);
  gap: 16px;
  align-items: start;
}

.import-card,
.list-card {
  background: #ffffff;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
}

.import-card {
  padding: 18px;
}

.list-card {
  min-width: 0;
  overflow: hidden;
}

.panel-heading,
.list-toolbar {
  display: flex;
  justify-content: space-between;
  gap: 16px;
}

.panel-heading {
  align-items: flex-start;
  margin-bottom: 18px;
}

.panel-icon {
  width: 34px;
  height: 34px;
  border-radius: 8px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  flex: 0 0 auto;
  color: #1677ff;
  background: #e6f4ff;
}

.panel-icon :deep(svg) {
  width: 18px;
  height: 18px;
}

.panel-heading h3,
.list-toolbar h3 {
  margin: 0;
  font-size: 16px;
  line-height: 1.4;
  font-weight: 700;
}

.panel-heading p,
.list-toolbar p {
  margin: 4px 0 0;
  color: #64748b;
  font-size: 13px;
}

.import-form {
  display: grid;
  gap: 10px;
}

.field-label {
  color: #374151;
  font-size: 13px;
  font-weight: 600;
}

.format-hint {
  margin: 0;
  color: #64748b;
  font-size: 12px;
  line-height: 1.6;
}

.import-button {
  width: 100%;
  margin-top: 2px;
}

.import-summary {
  margin: 0;
  color: #2f6f4e;
  font-size: 13px;
}

.delete-alert {
  margin-bottom: 16px;
}

.list-toolbar {
  align-items: center;
  padding: 18px 18px 14px;
  border-bottom: 1px solid #eef2f7;
}

.list-actions {
  display: grid;
  grid-template-columns: minmax(220px, 320px) auto;
  gap: 10px;
  align-items: center;
}

.knowledge-table {
  width: 100%;
}

.empty-state {
  min-height: 160px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 8px;
  color: #64748b;
}

.empty-state strong {
  color: #374151;
  font-size: 15px;
}

.empty-state span {
  font-size: 13px;
}

:deep(.el-textarea__inner) {
  resize: vertical;
}

:deep(.el-table th.el-table__cell) {
  background: #f8fafc;
  color: #475569;
  font-weight: 600;
}

@media (max-width: 760px) {
  .knowledge-page {
    padding: 16px;
  }

  .knowledge-header,
  .list-toolbar {
    display: grid;
  }

  .header-actions,
  .list-actions {
    grid-template-columns: 1fr;
    display: grid;
    justify-content: stretch;
  }

  .metric-strip,
  .knowledge-layout {
    grid-template-columns: 1fr;
  }

  .knowledge-header h2 {
    font-size: 22px;
  }
}
</style>
