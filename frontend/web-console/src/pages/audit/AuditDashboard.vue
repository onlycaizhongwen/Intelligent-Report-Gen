<template>
  <section class="page audit-page">
    <header class="page-header">
      <h2>审计与历史</h2>
    </header>

    <el-alert v-if="error" type="error" :title="error" show-icon />

    <el-tabs v-model="activeTab" @tab-change="loadActiveTab">
      <el-tab-pane label="个人历史" name="history">
        <el-table v-if="historyLogs.length" :data="historyLogs" border>
          <el-table-column prop="operation" label="操作" min-width="180" />
          <el-table-column prop="actor" label="操作人" width="120" />
          <el-table-column prop="resource" label="资源" min-width="180" />
          <el-table-column prop="result" label="结果" width="120" />
          <el-table-column prop="createdAt" label="时间" min-width="220" />
        </el-table>
        <el-empty v-else description="暂无个人历史" />
      </el-tab-pane>

      <el-tab-pane label="全局审计" name="audit">
        <el-table v-if="auditLogs.length" :data="auditLogs" border>
          <el-table-column prop="operation" label="操作" min-width="180" />
          <el-table-column prop="actor" label="操作人" width="120" />
          <el-table-column prop="resource" label="资源" min-width="180" />
          <el-table-column prop="result" label="结果" width="120" />
          <el-table-column prop="createdAt" label="时间" min-width="220" />
        </el-table>
        <el-empty v-else description="暂无全局审计日志" />
      </el-tab-pane>
    </el-tabs>
  </section>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue';
import { auditApi, type AuditLogPage, type AuditLogRow } from '../../api/auditApi';

interface DisplayAuditLog {
  id: string;
  operation: string;
  actor: string;
  resource: string;
  result: string;
  createdAt: string;
}

const activeTab = ref('history');
const historyLogs = ref<DisplayAuditLog[]>([]);
const auditLogs = ref<DisplayAuditLog[]>([]);
const historyLoaded = ref(false);
const auditLoaded = ref(false);
const error = ref('');

onMounted(async () => {
  await loadHistory();
});

async function loadActiveTab() {
  if (activeTab.value === 'history') {
    await loadHistory();
    return;
  }
  await loadAuditLogs();
}

async function loadHistory() {
  if (historyLoaded.value) {
    return;
  }
  await loadLogs(() => auditApi.history({ page: 1, pageSize: 10 }), historyLogs, historyLoaded);
}

async function loadAuditLogs() {
  if (auditLoaded.value) {
    return;
  }
  await loadLogs(() => auditApi.listAuditLogs({ page: 1, pageSize: 10 }), auditLogs, auditLoaded);
}

async function loadLogs(
  request: () => Promise<unknown>,
  target: typeof historyLogs,
  loaded: typeof historyLoaded
) {
  error.value = '';
  try {
    const page = await request() as AuditLogPage;
    target.value = (page.items ?? []).map(normalizeLog);
    loaded.value = true;
  } catch {
    error.value = '审计日志加载失败';
  }
}

function normalizeLog(row: AuditLogRow): DisplayAuditLog {
  const operation = row.operationType ?? row.action ?? '-';
  const actor = row.actorUserId ?? row.operator ?? '-';
  const resourceType = row.resourceType ?? '-';
  const resourceId = row.resourceId ?? '-';
  return {
    id: String(row.operationLogId ?? row.id ?? `${operation}-${row.createdAt ?? ''}`),
    operation: String(operation),
    actor: String(actor),
    resource: `${resourceType}:${resourceId}`,
    result: String(row.result ?? '-'),
    createdAt: String(row.createdAt ?? '-')
  };
}
</script>

<style scoped>
.audit-page {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.page-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
</style>
