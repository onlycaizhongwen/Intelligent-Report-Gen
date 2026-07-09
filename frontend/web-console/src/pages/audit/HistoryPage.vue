<template>
  <section class="page history-page">
    <header class="page-header">
      <div>
        <h2>历史记录</h2>
        <p>共 {{ activeTab === 'history' ? historyTotal : auditTotal }} 条记录</p>
      </div>
    </header>

    <el-alert v-if="errorMessage" :title="errorMessage" type="error" show-icon :closable="false" />

    <el-tabs v-model="activeTab" @tab-change="loadActiveTab">
      <el-tab-pane label="个人历史" name="history">
        <el-table :data="historyRows" row-key="id" empty-text="暂无历史记录">
          <el-table-column prop="createdAt" label="时间" width="190" />
          <el-table-column prop="operationType" label="操作类型" width="160" />
          <el-table-column prop="content" label="内容" min-width="260" />
          <el-table-column prop="operator" label="操作人" width="140" />
          <el-table-column label="操作" width="120">
            <template #default>查看</template>
          </el-table-column>
        </el-table>
      </el-tab-pane>
      <el-tab-pane label="管理员审计" name="audit">
        <el-table :data="auditRows" row-key="id" empty-text="暂无审计日志">
          <el-table-column prop="createdAt" label="时间" width="190" />
          <el-table-column prop="operationType" label="操作类型" width="160" />
          <el-table-column prop="content" label="内容" min-width="260" />
          <el-table-column prop="operator" label="操作人" width="140" />
          <el-table-column prop="result" label="结果" width="120" />
        </el-table>
      </el-tab-pane>
    </el-tabs>
  </section>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue';
import { auditApi, type AuditLogPage, type AuditLogRow } from '../../api/auditApi';
import { isLocalPreviewUnauthorizedError } from '../../api/client';

interface HistoryRow {
  id: string;
  createdAt: string;
  operationType: string;
  content: string;
  operator: string;
  result: string;
}

const activeTab = ref('history');
const historyRows = ref<HistoryRow[]>([]);
const auditRows = ref<HistoryRow[]>([]);
const historyTotal = ref(0);
const auditTotal = ref(0);
const historyLoaded = ref(false);
const auditLoaded = ref(false);
const errorMessage = ref('');

onMounted(loadHistory);

async function loadActiveTab() {
  if (activeTab.value === 'history') {
    await loadHistory();
    return;
  }
  await loadAudit();
}

async function loadHistory() {
  if (historyLoaded.value) return;
  await loadRows(() => auditApi.history({ page: 1, pageSize: 10 }), historyRows, historyTotal, historyLoaded, previewHistoryRows());
}

async function loadAudit() {
  if (auditLoaded.value) return;
  await loadRows(() => auditApi.listAuditLogs({ page: 1, pageSize: 10 }), auditRows, auditTotal, auditLoaded, previewAuditRows());
}

async function loadRows(
  request: () => Promise<unknown>,
  target: typeof historyRows,
  totalTarget: typeof historyTotal,
  loaded: typeof historyLoaded,
  previewRows: HistoryRow[]
) {
  errorMessage.value = '';
  try {
    const page = await request() as AuditLogPage;
    target.value = (page.items ?? []).map(normalizeRow);
    totalTarget.value = page.total ?? target.value.length;
    loaded.value = true;
  } catch (error) {
    if (isLocalPreviewUnauthorizedError(error)) {
      target.value = previewRows;
      totalTarget.value = previewRows.length;
      loaded.value = true;
      return;
    }
    errorMessage.value = error instanceof Error ? error.message : '历史记录加载失败';
  }
}

function normalizeRow(row: AuditLogRow): HistoryRow {
  const operationType = String(row.operationType ?? row.action ?? '-');
  const resourceType = row.resourceType ? `${row.resourceType}` : '';
  const resourceId = row.resourceId ? ` #${row.resourceId}` : '';
  return {
    id: String(row.operationLogId ?? row.id ?? `${operationType}-${row.createdAt ?? ''}`),
    createdAt: String(row.createdAt ?? '-'),
    operationType,
    content: String(row.content ?? (`${resourceType}${resourceId}`.trim() || '-')),
    operator: String(row.operator ?? row.actorUserId ?? '-'),
    result: String(row.result ?? '-')
  };
}

function previewHistoryRows(): HistoryRow[] {
  return [
    {
      id: 'preview-history-1',
      createdAt: '2026-06-11 10:30',
      operationType: '生成报告',
      content: '创建 Q3华东区销售分析报告',
      operator: '张三',
      result: '成功'
    }
  ];
}

function previewAuditRows(): HistoryRow[] {
  return [
    {
      id: 'preview-audit-1',
      createdAt: '2026-06-11 10:32',
      operationType: '权限变更',
      content: '管理员调整高级分析师导出权限',
      operator: '管理员',
      result: '成功'
    }
  ];
}
</script>

<style scoped>
.history-page {
  display: grid;
  gap: 16px;
}

.page-header h2,
.page-header p {
  margin: 0;
}

.page-header p {
  margin-top: 4px;
  color: #606266;
  font-size: 13px;
}
</style>
