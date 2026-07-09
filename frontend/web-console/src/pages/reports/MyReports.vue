<template>
  <section class="page my-reports-page">
    <header class="page-header">
      <div>
        <h2>我的报告</h2>
        <p>共 {{ total }} 条</p>
      </div>
      <RouterLink class="primary-link" role="button" to="/reports/create">+ 新建报告</RouterLink>
    </header>

    <el-alert v-if="errorMessage" :title="errorMessage" type="error" show-icon :closable="false" />

    <section class="report-controls" aria-label="报告列表控制">
      <span>每页5条</span>
      <span>每页10条</span>
      <span>每页20条</span>
    </section>

    <el-table :data="reports" row-key="reportId" empty-text="暂无报告">
      <el-table-column prop="title" label="报告名称" min-width="240" />
      <el-table-column label="生成方式" width="140">
        <template #default="{ row }">{{ row.generationMode || generationModeLabel(row.status) }}</template>
      </el-table-column>
      <el-table-column label="创建时间" width="190">
        <template #default="{ row }">{{ row.createdAt || '-' }}</template>
      </el-table-column>
      <el-table-column label="状态" width="120">
        <template #default="{ row }">{{ statusLabel(row.status) }}</template>
      </el-table-column>
      <el-table-column label="操作" width="170">
        <template #default="{ row }">
          <RouterLink class="table-link" :to="`/reports/${row.reportId}`">查看</RouterLink>
        </template>
      </el-table-column>
    </el-table>
  </section>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue';
import { RouterLink } from 'vue-router';
import { isLocalPreviewUnauthorizedError } from '../../api/client';
import { reportApi } from '../../api/reportApi';
import type { ReportSummary } from '../../api/types';

interface ReportRow extends ReportSummary {
  generationMode?: string;
  createdAt?: string;
}

const reports = ref<ReportRow[]>([]);
const total = ref(0);
const errorMessage = ref('');

onMounted(loadReports);

async function loadReports() {
  errorMessage.value = '';
  try {
    const result = await reportApi.list({ page: 1, pageSize: 20 }) as unknown as {
      items?: ReportRow[];
      total?: number;
    };
    reports.value = result.items ?? [];
    total.value = result.total ?? reports.value.length;
  } catch (error) {
    if (isLocalPreviewUnauthorizedError(error)) {
      reports.value = previewReports();
      total.value = reports.value.length;
      return;
    }
    errorMessage.value = error instanceof Error ? error.message : '报告列表加载失败';
  }
}

function previewReports(): ReportRow[] {
  return [
    {
      reportId: 101,
      title: 'Q3华东区销售分析报告',
      status: 'completed',
      generationMode: '智能生成',
      createdAt: '2026-06-11 10:30'
    },
    {
      reportId: 102,
      title: '法规文本分类报告',
      status: 'draft',
      generationMode: '模板填报',
      createdAt: '2026-06-10 16:00'
    }
  ];
}

function generationModeLabel(status: string) {
  return status === 'draft' ? '模板填报' : '智能生成';
}

function statusLabel(status: string) {
  const labels: Record<string, string> = {
    completed: '已完成',
    generating: '生成中',
    failed: '失败',
    draft: '草稿'
  };
  return labels[status] ?? status;
}
</script>

<style scoped>
.my-reports-page {
  display: grid;
  gap: 16px;
}

.page-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
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

.primary-link,
.table-link {
  color: #1677ff;
  text-decoration: none;
  font-weight: 600;
}

.primary-link {
  padding: 8px 12px;
  border-radius: 6px;
  background: #1677ff;
  color: #fff;
}

.report-controls {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  color: #606266;
  font-size: 13px;
}
</style>
