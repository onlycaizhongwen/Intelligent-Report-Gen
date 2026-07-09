<template>
  <section class="dashboard-page">
    <header class="page-header">
      <div>
        <h2>工作台</h2>
        <span v-if="overview" class="scope-label">{{ scopeLabel }}</span>
      </div>
      <el-segmented v-model="range" :options="ranges" @change="loadOverview" />
    </header>

    <el-alert v-if="errorMessage" :title="errorMessage" type="error" show-icon :closable="false" />

    <el-row v-loading="loading" :gutter="12">
      <el-col v-for="card in cards" :key="card.key" :xs="12" :sm="8" :md="4">
        <el-card shadow="never" class="metric-card">
          <span>{{ card.label }}</span>
          <strong>{{ card.value }}</strong>
        </el-card>
      </el-col>
    </el-row>

    <section class="content-grid">
      <div class="panel">
        <h3>报告生成趋势</h3>
        <el-table v-if="overview?.reportTrend.length" :data="overview.reportTrend" size="small">
          <el-table-column prop="date" label="日期" />
          <el-table-column prop="completedReports" label="完成报告" align="right" />
        </el-table>
        <el-empty v-else description="暂无趋势数据" />
      </div>

      <div class="panel">
        <h3>知识库引用排行</h3>
        <el-table v-if="overview?.knowledgeRank.length" :data="overview.knowledgeRank" size="small">
          <el-table-column prop="name" label="知识库" />
          <el-table-column prop="references" label="引用次数" align="right" />
        </el-table>
        <el-empty v-else description="暂无引用数据" />
      </div>
    </section>

    <section class="panel">
      <h3>规则调度健康</h3>
      <el-row :gutter="12">
        <el-col v-for="item in ruleScheduleCards" :key="item.key" :xs="12" :sm="6">
          <div class="health-tile" :class="item.level">
            <span>{{ item.label }}</span>
            <strong>{{ item.value }}</strong>
          </div>
        </el-col>
      </el-row>
    </section>

    <section class="panel">
      <h3>最近活动</h3>
      <el-table v-if="overview?.recentActivities.length" :data="overview.recentActivities" size="small">
        <el-table-column prop="operationType" label="操作" />
        <el-table-column prop="actorUserId" label="用户" width="120" />
        <el-table-column prop="result" label="结果" width="120" />
        <el-table-column prop="createdAt" label="时间" width="220" />
      </el-table>
      <el-empty v-else description="暂无活动" />
    </section>
  </section>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { auditApi, type DashboardOverview } from '../api/auditApi';
import { isLocalPreviewUnauthorizedError } from '../api/client';

const range = ref('last7days');
const ranges = [
  { label: '今天', value: 'today' },
  { label: '近 7 天', value: 'last7days' },
  { label: '近 30 天', value: 'last30days' },
  { label: '全部', value: 'all' }
];
const loading = ref(false);
const errorMessage = ref('');
const overview = ref<(DashboardOverview & { scope?: string }) | null>(null);

const scopeLabel = computed(() => overview.value?.scope === 'global' ? '全局指标' : '个人指标');

const cards = computed(() => {
  const data = overview.value?.cards;
  return [
    { key: 'reportOutputs', label: '报告产出', value: data?.reportOutputs ?? 0 },
    { key: 'knowledgeItems', label: '知识条目', value: data?.knowledgeItems ?? 0 },
    { key: 'activeDataSources', label: '活跃数据源', value: data?.activeDataSources ?? 0 },
    { key: 'citationHitRate', label: '引用命中率', value: `${Math.round((data?.citationHitRate ?? 0) * 100)}%` },
    { key: 'activeUsers', label: '活跃用户', value: data?.activeUsers ?? 0 }
  ];
});

const ruleScheduleCards = computed(() => {
  const data = overview.value?.ruleScheduleHealth;
  return [
    { key: 'scheduledRules', label: '计划调度规则', value: data?.scheduledRules ?? 0, level: 'neutral' },
    { key: 'failedScheduledRules', label: '失败调度规则', value: data?.failedScheduledRules ?? 0, level: (data?.failedScheduledRules ?? 0) > 0 ? 'warning' : 'neutral' },
    { key: 'blockedScheduledRules', label: '达到重试上限', value: data?.blockedScheduledRules ?? 0, level: (data?.blockedScheduledRules ?? 0) > 0 ? 'danger' : 'neutral' },
    { key: 'recentAlerts', label: '未读调度告警', value: data?.recentAlerts ?? 0, level: (data?.recentAlerts ?? 0) > 0 ? 'warning' : 'neutral' }
  ];
});

async function loadOverview() {
  loading.value = true;
  errorMessage.value = '';
  try {
    overview.value = await auditApi.dashboardOverview(range.value) as unknown as DashboardOverview & { scope?: string };
  } catch (error) {
    if (isLocalPreviewUnauthorizedError(error)) {
      overview.value = previewOverview(range.value);
      return;
    }
    errorMessage.value = error instanceof Error ? error.message : '工作台数据加载失败';
  } finally {
    loading.value = false;
  }
}

function previewOverview(currentRange: string): DashboardOverview & { scope?: string } {
  return {
    range: currentRange,
    scope: 'personal',
    cards: {
      reportOutputs: 12,
      knowledgeItems: 86,
      activeDataSources: 5,
      citationHitRate: 0.92,
      activeUsers: 8
    },
    reportTrend: [
      { date: '2026-06-09', completedReports: 3 },
      { date: '2026-06-10', completedReports: 4 },
      { date: '2026-06-11', completedReports: 5 }
    ],
    knowledgeRank: [
      { knowledgeBaseId: 1, name: '财报库', references: 42 },
      { knowledgeBaseId: 2, name: '法规库', references: 27 }
    ],
    ruleScheduleHealth: {
      scheduledRules: 6,
      failedScheduledRules: 0,
      blockedScheduledRules: 0,
      recentAlerts: 1
    },
    recentActivities: [
      { operationLogId: 1, operationType: '生成报告', actorUserId: 1, result: '成功', createdAt: '2026-06-11 10:30' }
    ]
  };
}

onMounted(loadOverview);
</script>

<style scoped>
.dashboard-page {
  display: grid;
  gap: 16px;
}

.page-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.page-header h2 {
  margin: 0;
}

.scope-label {
  display: inline-block;
  margin-top: 6px;
  color: #6b7280;
  font-size: 13px;
}

.metric-card {
  min-height: 96px;
}

.metric-card span,
.health-tile span {
  color: #6b7280;
  font-size: 13px;
}

.metric-card strong,
.health-tile strong {
  display: block;
  margin-top: 10px;
  font-size: 26px;
  line-height: 1.2;
}

.content-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 16px;
}

.panel {
  display: grid;
  gap: 10px;
}

.panel h3 {
  margin: 0;
  font-size: 16px;
}

.health-tile {
  min-height: 86px;
  padding: 14px;
  border: 1px solid #e5e7eb;
  border-radius: 6px;
  background: #ffffff;
}

.health-tile.warning {
  border-color: #f59e0b;
  background: #fffbeb;
}

.health-tile.danger {
  border-color: #ef4444;
  background: #fef2f2;
}

@media (max-width: 860px) {
  .page-header {
    align-items: flex-start;
    flex-direction: column;
  }

  .content-grid {
    grid-template-columns: 1fr;
  }
}
</style>
