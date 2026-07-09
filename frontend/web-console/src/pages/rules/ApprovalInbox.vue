<template>
  <section class="page approval-inbox-page">
    <header class="page-header">
      <div>
        <h2>审批待办</h2>
        <p>集中处理规则运行中的审批任务、补充材料和催办。</p>
      </div>
      <el-button :loading="loading" @click="loadPendingApprovals">刷新</el-button>
    </header>

    <el-alert v-if="errorMessage" :title="errorMessage" type="error" show-icon :closable="false" />
    <el-alert v-if="successMessage" :title="successMessage" type="success" show-icon :closable="false" />

    <section class="summary-strip" aria-label="审批待办汇总">
      <div class="status-switch">
        <el-button :type="selectedStatus === 'pending' ? 'primary' : 'default'" @click="switchStatus('pending')">待审批</el-button>
        <el-button :type="selectedStatus === 'approved' ? 'primary' : 'default'" @click="switchStatus('approved')">已通过</el-button>
        <el-button :type="selectedStatus === 'rejected' ? 'primary' : 'default'" @click="switchStatus('rejected')">已驳回</el-button>
        <el-button :type="selectedStatus === 'supplement_required' ? 'primary' : 'default'" @click="switchStatus('supplement_required')">待补充</el-button>
        <el-button :type="selectedStatus === 'resubmitted' ? 'primary' : 'default'" @click="switchStatus('resubmitted')">已重提</el-button>
        <el-button :type="selectedStatus === 'closed' ? 'primary' : 'default'" @click="switchStatus('closed')">已关闭</el-button>
      </div>
      <div class="summary-actions">
        <span>{{ summaryLabel }} {{ approvalRecords.length }}</span>
        <el-button
          v-if="selectedStatus === 'pending'"
          type="primary"
          :disabled="selectedApprovalIds.length === 0"
          :loading="batchHandling"
          @click="batchHandleApprovals('approve')"
        >
          批量通过
        </el-button>
      </div>
    </section>

    <section class="filter-strip" aria-label="审批待办筛选">
      <el-input v-model="filters.ruleId" aria-label="规则编号筛选" placeholder="规则编号" clearable />
      <el-input v-model="filters.assigneeRole" aria-label="审批角色筛选" placeholder="审批角色" clearable />
      <el-input v-model="filters.approvalTitle" aria-label="审批标题筛选" placeholder="审批标题" clearable />
      <el-input v-model="filters.createdByUserId" aria-label="发起人编号筛选" placeholder="发起人编号" clearable />
      <el-input v-model="filters.approvedByUserId" aria-label="审批人编号筛选" placeholder="审批人编号" clearable />
      <el-input v-model="filters.createdAtFrom" aria-label="创建开始时间筛选" placeholder="创建开始时间（ISO-8601）" clearable />
      <el-input v-model="filters.createdAtTo" aria-label="创建结束时间筛选" placeholder="创建结束时间（ISO-8601）" clearable />
      <el-button type="primary" :loading="loading" @click="applyFilters">应用筛选</el-button>
      <el-button @click="resetFilters">重置</el-button>
    </section>

    <section v-if="approvalRecords.length > 0" class="approval-list">
      <article v-for="record in approvalRecords" :key="String(record.approvalRecordId)" class="approval-card">
        <div class="approval-main">
          <label v-if="record.status === 'pending'" class="selection-box">
            <input
              :aria-label="`选择审批记录 ${record.approvalRecordId}`"
              type="checkbox"
              :checked="selectedApprovalIds.includes(String(record.approvalRecordId))"
              @change="toggleApprovalSelection(record.approvalRecordId)"
            >
          </label>
          <div class="approval-meta">
            <strong>{{ record.approvalTitle }}</strong>
            <span>规则 #{{ record.ruleId }} · 节点 {{ record.nodeId }}</span>
            <span>审批角色 {{ record.assigneeRole }}</span>
            <small v-if="approvalUsersText(record.assigneeUsers)">候选审批人 {{ approvalUsersText(record.assigneeUsers) }}</small>
            <span v-if="record.delegateRole">代理角色 {{ record.delegateRole }}</span>
            <small v-if="approvalUsersText(record.delegateUsers)">代理用户 {{ approvalUsersText(record.delegateUsers) }}</small>
            <small v-if="delegateWindowText(record)">代理时段 {{ delegateWindowText(record) }}</small>
            <span v-if="hasApprovalGroup(record)">
              {{ approvalGroupSummaryText(record) }}
            </span>
            <span v-if="approvalGroupRoles(record)">审批组角色 {{ approvalGroupRoles(record) }}</span>
            <span>状态 {{ statusLabel(record.status) }}</span>
            <small v-if="record.isOverdue" class="overdue-flag">已逾期</small>
            <small v-if="record.slaDueAt">SLA 截止 {{ formatDate(record.slaDueAt) }}</small>
            <small>催办次数 {{ record.remindCount ?? 0 }}</small>
            <small v-if="record.lastRemindedAt">最近催办 {{ formatDate(record.lastRemindedAt) }}</small>
            <small v-if="record.approvalComment">审批备注 {{ record.approvalComment }}</small>
            <small>创建时间 {{ formatDate(record.createdAt) }}</small>
            <small v-if="record.approvedAt">处理时间 {{ formatDate(record.approvedAt) }}</small>
          </div>
          <div v-if="record.status === 'pending'" class="approval-actions">
            <el-button
              type="primary"
              size="small"
              :loading="actionRecordId === record.approvalRecordId && actionType === 'approve'"
              @click="handleApproval(record, 'approve')"
            >
              通过
            </el-button>
            <el-button
              size="small"
              :loading="actionRecordId === record.approvalRecordId && actionType === 'reject'"
              @click="handleApproval(record, 'reject')"
            >
              驳回
            </el-button>
            <el-button
              size="small"
              :loading="actionRecordId === record.approvalRecordId && actionType === 'remind'"
              @click="handleReminder(record)"
            >
              催办
            </el-button>
          </div>
          <div v-else-if="record.status === 'rejected'" class="supplement-actions">
            <el-input
              v-model="supplementForms[String(record.approvalRecordId)].comment"
              aria-label="补充说明"
              type="textarea"
              :rows="2"
              placeholder="补充说明"
            />
            <el-input
              v-model="supplementForms[String(record.approvalRecordId)].evidenceUrl"
              aria-label="补充材料地址"
              placeholder="补充材料地址"
              clearable
            />
            <label class="supplement-upload">
              <span>补充附件</span>
              <input
                aria-label="补充附件"
                type="file"
                :disabled="uploadingSupplementIds.includes(String(record.approvalRecordId))"
                @change="uploadSupplementAttachment(record, $event)"
              >
            </label>
            <small v-if="supplementForms[String(record.approvalRecordId)].fileName" class="supplement-file">
              {{ supplementForms[String(record.approvalRecordId)].fileName }}
            </small>
            <el-button
              type="primary"
              size="small"
              :loading="actionRecordId === record.approvalRecordId && actionType === 'supplement'"
              @click="submitSupplement(record)"
            >
              提交补充
            </el-button>
          </div>
        </div>
        <RouterLink class="rule-link" :to="`/rules?ruleId=${record.ruleId}`">打开规则</RouterLink>
      </article>
    </section>

    <el-empty v-else :description="emptyDescription" />
  </section>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { RouterLink } from 'vue-router';
import { isLocalPreviewUnauthorizedError } from '../../api/client';
import { ruleApi } from '../../api/ruleApi';

interface ApprovalRoleUser {
  userId?: number | string;
  username?: string;
  displayName?: string;
  role?: string;
}

interface PendingApprovalRecord {
  approvalRecordId: number | string;
  ruleId: number | string;
  runId: number | string;
  nodeId: string;
  assigneeRole: string;
  assigneeUsers?: ApprovalRoleUser[];
  delegateRole?: string | null;
  delegateUsers?: ApprovalRoleUser[];
  delegateActiveFrom?: string | null;
  delegateActiveTo?: string | null;
  assigneeRoles?: string[];
  approvalMode?: 'all' | 'any' | string;
  approvalGroupKey?: string;
  approvalGroupTotalCount?: number;
  approvalGroupApprovedCount?: number;
  approvalGroupPendingCount?: number;
  approvalGroupRejectedCount?: number;
  approvalGroupClosedCount?: number;
  approvalTitle: string;
  status: string;
  approvalComment?: string | null;
  createdAt?: string | null;
  approvedAt?: string | null;
  slaHours?: number | null;
  slaDueAt?: string | null;
  isOverdue?: boolean;
  remindCount?: number | null;
  lastRemindedAt?: string | null;
}

const approvalRecords = ref<PendingApprovalRecord[]>([]);
const loading = ref(false);
const errorMessage = ref('');
const successMessage = ref('');
const actionRecordId = ref<number | string | null>(null);
const actionType = ref<'approve' | 'reject' | 'remind' | 'supplement' | null>(null);
const batchHandling = ref(false);
type ApprovalStatusFilter = 'pending' | 'approved' | 'rejected' | 'supplement_required' | 'resubmitted' | 'closed';
const selectedStatus = ref<ApprovalStatusFilter>('pending');
const selectedApprovalIds = ref<string[]>([]);
const uploadingSupplementIds = ref<string[]>([]);
const supplementForms = ref<Record<string, { comment: string; evidenceUrl: string; fileName: string }>>({});
const filters = ref({
  ruleId: '',
  assigneeRole: '',
  approvalTitle: '',
  createdByUserId: '',
  approvedByUserId: '',
  createdAtFrom: '',
  createdAtTo: ''
});

const summaryLabel = computed(() => {
  return statusLabel(selectedStatus.value);
});

const emptyDescription = computed(() => {
  if (selectedStatus.value === 'approved') return '暂无已通过记录';
  if (selectedStatus.value === 'rejected') return '暂无已驳回记录';
  if (selectedStatus.value === 'supplement_required') return '暂无待补充记录';
  if (selectedStatus.value === 'resubmitted') return '暂无已重提记录';
  if (selectedStatus.value === 'closed') return '暂无已关闭记录';
  return '暂无待审批记录';
});

function normalizeDateTimeFilter(value: string) {
  const trimmed = value.trim();
  if (!trimmed) {
    return undefined;
  }
  if (/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}$/.test(trimmed)) {
    return `${trimmed}:00Z`;
  }
  return trimmed;
}

async function loadPendingApprovals() {
  loading.value = true;
  errorMessage.value = '';
  try {
    const query = {
      page: 1,
      pageSize: 20,
      status: selectedStatus.value,
      ruleId: filters.value.ruleId.trim() || undefined,
      assigneeRole: filters.value.assigneeRole.trim() || undefined,
      approvalTitle: filters.value.approvalTitle.trim() || undefined,
      createdByUserId: filters.value.createdByUserId.trim() || undefined,
      approvedByUserId: filters.value.approvedByUserId.trim() || undefined,
      createdAtFrom: normalizeDateTimeFilter(filters.value.createdAtFrom),
      createdAtTo: normalizeDateTimeFilter(filters.value.createdAtTo)
    };
    const result = await ruleApi.listPendingApprovalRecords(query) as unknown as {
      items: PendingApprovalRecord[];
    };
    approvalRecords.value = result.items ?? [];
    syncSupplementForms();
    selectedApprovalIds.value = selectedApprovalIds.value.filter((approvalRecordId) =>
      approvalRecords.value.some((record) => String(record.approvalRecordId) === approvalRecordId && record.status === 'pending')
    );
  } catch (error) {
    if (isLocalPreviewUnauthorizedError(error)) {
      approvalRecords.value = [];
      selectedApprovalIds.value = [];
      syncSupplementForms();
      return;
    }
    errorMessage.value = error instanceof Error ? error.message : '审批记录加载失败';
  } finally {
    loading.value = false;
  }
}

function syncSupplementForms() {
  const nextForms: Record<string, { comment: string; evidenceUrl: string; fileName: string }> = {};
  for (const record of approvalRecords.value) {
    if (record.status !== 'rejected') {
      continue;
    }
    const key = String(record.approvalRecordId);
    nextForms[key] = supplementForms.value[key] ?? { comment: '', evidenceUrl: '', fileName: '' };
  }
  supplementForms.value = nextForms;
}

async function handleApproval(record: PendingApprovalRecord, action: 'approve' | 'reject') {
  actionRecordId.value = record.approvalRecordId;
  actionType.value = action;
  errorMessage.value = '';
  successMessage.value = '';
  try {
    await ruleApi.handleApprovalRecord(String(record.ruleId), String(record.approvalRecordId), {
      action,
      comment: action === 'approve' ? '在审批待办中通过' : '在审批待办中驳回'
    });
    await loadPendingApprovals();
    selectedApprovalIds.value = selectedApprovalIds.value.filter((approvalRecordId) => approvalRecordId !== String(record.approvalRecordId));
    successMessage.value = action === 'approve'
      ? '审批已通过，下游流程已恢复。'
      : '审批已驳回，请补充材料后重新提交。';
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : (action === 'approve' ? '审批通过失败' : '审批驳回失败');
  } finally {
    actionRecordId.value = null;
    actionType.value = null;
  }
}

async function submitSupplement(record: PendingApprovalRecord) {
  actionRecordId.value = record.approvalRecordId;
  actionType.value = 'supplement';
  errorMessage.value = '';
  successMessage.value = '';
  const form = supplementForms.value[String(record.approvalRecordId)] ?? { comment: '', evidenceUrl: '', fileName: '' };
  try {
    await ruleApi.submitApprovalSupplement(String(record.ruleId), String(record.approvalRecordId), {
      comment: form.comment.trim() || undefined,
      evidenceUrl: form.evidenceUrl.trim() || undefined
    });
    await loadPendingApprovals();
    successMessage.value = '补充材料已提交，审批已回到待处理状态。';
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : '补充材料提交失败';
  } finally {
    actionRecordId.value = null;
    actionType.value = null;
  }
}

async function uploadSupplementAttachment(record: PendingApprovalRecord, event: Event) {
  const input = event.target as HTMLInputElement;
  const file = input.files?.[0];
  if (!file) {
    return;
  }
  const recordId = String(record.approvalRecordId);
  uploadingSupplementIds.value = [...uploadingSupplementIds.value, recordId];
  errorMessage.value = '';
  successMessage.value = '';
  try {
    const formData = new FormData();
    formData.append('file', file);
    const uploaded = await ruleApi.uploadApprovalSupplementAttachment(
      String(record.ruleId),
      recordId,
      formData
    ) as unknown as {
      evidenceUrl?: string;
      fileName?: string;
    };
    const form = supplementForms.value[recordId] ?? { comment: '', evidenceUrl: '', fileName: '' };
    supplementForms.value[recordId] = {
      ...form,
      evidenceUrl: uploaded.evidenceUrl ?? form.evidenceUrl,
      fileName: uploaded.fileName ?? file.name
    };
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : '补充附件上传失败';
    input.value = '';
  } finally {
    uploadingSupplementIds.value = uploadingSupplementIds.value.filter((item) => item !== recordId);
  }
}

async function handleReminder(record: PendingApprovalRecord) {
  actionRecordId.value = record.approvalRecordId;
  actionType.value = 'remind';
  errorMessage.value = '';
  successMessage.value = '';
  try {
    await ruleApi.remindApprovalRecord(String(record.ruleId), String(record.approvalRecordId));
    await loadPendingApprovals();
    successMessage.value = '已发送审批催办。';
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : '审批催办发送失败';
  } finally {
    actionRecordId.value = null;
    actionType.value = null;
  }
}

function toggleApprovalSelection(approvalRecordId: number | string) {
  const targetId = String(approvalRecordId);
  if (selectedApprovalIds.value.includes(targetId)) {
    selectedApprovalIds.value = selectedApprovalIds.value.filter((item) => item !== targetId);
    return;
  }
  selectedApprovalIds.value = [...selectedApprovalIds.value, targetId];
}

async function batchHandleApprovals(action: 'approve' | 'reject') {
  if (selectedApprovalIds.value.length === 0) {
    return;
  }
  batchHandling.value = true;
  errorMessage.value = '';
  successMessage.value = '';
  try {
    const result = await ruleApi.batchHandleApprovalRecords({
      action,
      approvalRecordIds: selectedApprovalIds.value.map((item) => Number(item)),
      comment: action === 'approve' ? '批量通过' : '批量驳回'
    }) as unknown as {
      succeededCount: number;
      failedCount: number;
    };
    await loadPendingApprovals();
    selectedApprovalIds.value = [];
    successMessage.value = `批量审批完成：成功 ${result.succeededCount} 条，失败 ${result.failedCount} 条。`;
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : (action === 'approve' ? '批量通过审批失败' : '批量驳回审批失败');
  } finally {
    batchHandling.value = false;
  }
}

async function applyFilters() {
  successMessage.value = '';
  await loadPendingApprovals();
}

async function resetFilters() {
  filters.value.ruleId = '';
  filters.value.assigneeRole = '';
  filters.value.approvalTitle = '';
  filters.value.createdByUserId = '';
  filters.value.approvedByUserId = '';
  filters.value.createdAtFrom = '';
  filters.value.createdAtTo = '';
  selectedApprovalIds.value = [];
  successMessage.value = '';
  await loadPendingApprovals();
}

async function switchStatus(status: ApprovalStatusFilter) {
  if (selectedStatus.value === status) {
    return;
  }
  selectedStatus.value = status;
  selectedApprovalIds.value = [];
  successMessage.value = '';
  await loadPendingApprovals();
}

function formatDate(value?: string | null) {
  if (!value) {
    return '-';
  }
  return value.replace('T', ' ').replace('Z', '');
}

function delegateWindowText(record: PendingApprovalRecord) {
  if (!record.delegateActiveFrom && !record.delegateActiveTo) {
    return '';
  }
  return `${formatDate(record.delegateActiveFrom) || '*'}..${formatDate(record.delegateActiveTo) || '*'}`;
}

function statusLabel(status: string) {
  const labels: Record<string, string> = {
    pending: '待审批',
    approved: '已通过',
    rejected: '已驳回',
    supplement_required: '待补充',
    resubmitted: '已重提',
    closed: '已关闭'
  };
  return labels[status] ?? status;
}

function approvalModeLabel(mode?: string) {
  if (mode === 'any') return '任一审批';
  if (mode === 'all') return '全部审批';
  return mode || '全部审批';
}

function hasApprovalGroup(record: PendingApprovalRecord) {
  return Boolean(record.approvalGroupKey) || (record.approvalGroupTotalCount ?? 0) > 1;
}

function approvalGroupRoles(record: PendingApprovalRecord) {
  if (!Array.isArray(record.assigneeRoles) || record.assigneeRoles.length <= 1) {
    return '';
  }
  return record.assigneeRoles.join(', ');
}

function approvalGroupSummaryText(record: PendingApprovalRecord) {
  const parts = [
    `审批组 ${approvalModeLabel(record.approvalMode)} 已通过 ${record.approvalGroupApprovedCount ?? 0}/${record.approvalGroupTotalCount ?? 1}`,
    `待审批 ${record.approvalGroupPendingCount ?? 0}`
  ];
  if ((record.approvalGroupRejectedCount ?? 0) > 0) {
    parts.push(`已驳回 ${record.approvalGroupRejectedCount ?? 0}`);
  }
  if ((record.approvalGroupClosedCount ?? 0) > 0) {
    parts.push(`已关闭 ${record.approvalGroupClosedCount ?? 0}`);
  }
  return parts.join(', ');
}

function approvalUsersText(users?: ApprovalRoleUser[]) {
  if (!Array.isArray(users) || users.length === 0) {
    return '';
  }
  return users
    .map((user) => {
      const label = user.displayName || user.username || String(user.userId ?? '').trim();
      const username = user.username ? ` (${user.username})` : '';
      return `${label}${username}`.trim();
    })
    .filter(Boolean)
    .join(', ');
}

onMounted(() => {
  void loadPendingApprovals();
});
</script>

<style scoped>
.approval-inbox-page {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.page-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
}

.page-header h2 {
  margin: 0;
  font-size: 24px;
}

.page-header p {
  margin: 6px 0 0;
  color: #606266;
}

.summary-strip {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 12px 16px;
  background: #ffffff;
  border: 1px solid #dcdfe6;
  border-radius: 8px;
}

.summary-actions {
  display: flex;
  align-items: center;
  gap: 12px;
}

.status-switch {
  display: flex;
  gap: 8px;
}

.filter-strip {
  display: grid;
  grid-template-columns:
    minmax(120px, 140px)
    minmax(180px, 220px)
    minmax(180px, 1fr)
    minmax(150px, 180px)
    minmax(150px, 180px)
    minmax(180px, 220px)
    minmax(180px, 220px)
    auto
    auto;
  gap: 12px;
  align-items: center;
}

.approval-list {
  display: grid;
  gap: 12px;
}

.approval-card {
  display: flex;
  flex-direction: column;
  gap: 12px;
  padding: 16px;
  background: #ffffff;
  border: 1px solid #dcdfe6;
  border-radius: 8px;
}

.approval-main {
  display: flex;
  justify-content: space-between;
  gap: 16px;
}

.selection-box {
  display: flex;
  align-items: flex-start;
  padding-top: 2px;
}

.approval-meta {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.approval-meta span,
.approval-meta small {
  color: #606266;
}

.approval-actions {
  display: flex;
  gap: 8px;
  align-items: center;
}

.supplement-actions {
  display: grid;
  width: min(360px, 38vw);
  gap: 8px;
}

.supplement-upload {
  display: grid;
  gap: 4px;
  color: #606266;
  font-size: 13px;
}

.supplement-file {
  color: #409eff;
  overflow-wrap: anywhere;
}

.overdue-flag {
  color: #c45656 !important;
  font-weight: 600;
}

.rule-link {
  color: #409eff;
  text-decoration: none;
  width: fit-content;
}
</style>
