<template>
  <section class="page approval-inbox-page">
    <header class="page-header">
      <div>
        <h2>Approval Inbox</h2>
        <p>Review pending rule approvals across all published rule runs.</p>
      </div>
      <el-button :loading="loading" @click="loadPendingApprovals">Refresh</el-button>
    </header>

    <el-alert v-if="errorMessage" :title="errorMessage" type="error" show-icon :closable="false" />
    <el-alert v-if="successMessage" :title="successMessage" type="success" show-icon :closable="false" />

    <section class="summary-strip" aria-label="Pending approval summary">
      <div class="status-switch">
        <el-button :type="selectedStatus === 'pending' ? 'primary' : 'default'" @click="switchStatus('pending')">Pending</el-button>
        <el-button :type="selectedStatus === 'approved' ? 'primary' : 'default'" @click="switchStatus('approved')">Approved</el-button>
        <el-button :type="selectedStatus === 'rejected' ? 'primary' : 'default'" @click="switchStatus('rejected')">Rejected</el-button>
        <el-button :type="selectedStatus === 'supplement_required' ? 'primary' : 'default'" @click="switchStatus('supplement_required')">Supplement required</el-button>
        <el-button :type="selectedStatus === 'resubmitted' ? 'primary' : 'default'" @click="switchStatus('resubmitted')">Resubmitted</el-button>
        <el-button :type="selectedStatus === 'closed' ? 'primary' : 'default'" @click="switchStatus('closed')">Closed</el-button>
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
          Batch approve
        </el-button>
      </div>
    </section>

    <section class="filter-strip" aria-label="Approval inbox filters">
      <el-input v-model="filters.ruleId" aria-label="Rule ID filter" placeholder="Rule ID" clearable />
      <el-input v-model="filters.assigneeRole" aria-label="Assignee role filter" placeholder="Assignee role" clearable />
      <el-input v-model="filters.approvalTitle" aria-label="Approval title filter" placeholder="Approval title" clearable />
      <el-input v-model="filters.createdByUserId" aria-label="Created by filter" placeholder="Created by user ID" clearable />
      <el-input v-model="filters.approvedByUserId" aria-label="Approved by filter" placeholder="Approved by user ID" clearable />
      <el-input v-model="filters.createdAtFrom" aria-label="Created from filter" placeholder="Created from (ISO-8601)" clearable />
      <el-input v-model="filters.createdAtTo" aria-label="Created to filter" placeholder="Created to (ISO-8601)" clearable />
      <el-button type="primary" :loading="loading" @click="applyFilters">Apply filters</el-button>
      <el-button @click="resetFilters">Reset</el-button>
    </section>

    <section v-if="approvalRecords.length > 0" class="approval-list">
      <article v-for="record in approvalRecords" :key="String(record.approvalRecordId)" class="approval-card">
        <div class="approval-main">
          <label v-if="record.status === 'pending'" class="selection-box">
            <input
              :aria-label="`Select approval ${record.approvalRecordId}`"
              type="checkbox"
              :checked="selectedApprovalIds.includes(String(record.approvalRecordId))"
              @change="toggleApprovalSelection(record.approvalRecordId)"
            >
          </label>
          <div class="approval-meta">
            <strong>{{ record.approvalTitle }}</strong>
            <span>Rule #{{ record.ruleId }} · Node {{ record.nodeId }}</span>
            <span>Assignee {{ record.assigneeRole }}</span>
            <small v-if="approvalUsersText(record.assigneeUsers)">Candidate approvers {{ approvalUsersText(record.assigneeUsers) }}</small>
            <span v-if="record.delegateRole">Delegate {{ record.delegateRole }}</span>
            <small v-if="approvalUsersText(record.delegateUsers)">Delegate users {{ approvalUsersText(record.delegateUsers) }}</small>
            <small v-if="delegateWindowText(record)">Delegate window {{ delegateWindowText(record) }}</small>
            <span v-if="hasApprovalGroup(record)">
              {{ approvalGroupSummaryText(record) }}
            </span>
            <span v-if="approvalGroupRoles(record)">Group roles {{ approvalGroupRoles(record) }}</span>
            <span>Status {{ record.status }}</span>
            <small v-if="record.isOverdue" class="overdue-flag">Overdue</small>
            <small v-if="record.slaDueAt">SLA due {{ formatDate(record.slaDueAt) }}</small>
            <small>Reminder count {{ record.remindCount ?? 0 }}</small>
            <small v-if="record.lastRemindedAt">Last reminder {{ formatDate(record.lastRemindedAt) }}</small>
            <small v-if="record.approvalComment">Comment {{ record.approvalComment }}</small>
            <small>Created {{ formatDate(record.createdAt) }}</small>
            <small v-if="record.approvedAt">Handled {{ formatDate(record.approvedAt) }}</small>
          </div>
          <div v-if="record.status === 'pending'" class="approval-actions">
            <el-button
              type="primary"
              size="small"
              :loading="actionRecordId === record.approvalRecordId && actionType === 'approve'"
              @click="handleApproval(record, 'approve')"
            >
              Approve
            </el-button>
            <el-button
              size="small"
              :loading="actionRecordId === record.approvalRecordId && actionType === 'reject'"
              @click="handleApproval(record, 'reject')"
            >
              Reject
            </el-button>
            <el-button
              size="small"
              :loading="actionRecordId === record.approvalRecordId && actionType === 'remind'"
              @click="handleReminder(record)"
            >
              Remind
            </el-button>
          </div>
          <div v-else-if="record.status === 'rejected'" class="supplement-actions">
            <el-input
              v-model="supplementForms[String(record.approvalRecordId)].comment"
              aria-label="Supplement comment"
              type="textarea"
              :rows="2"
              placeholder="Supplement comment"
            />
            <el-input
              v-model="supplementForms[String(record.approvalRecordId)].evidenceUrl"
              aria-label="Supplement evidence URL"
              placeholder="Evidence URL"
              clearable
            />
            <el-button
              type="primary"
              size="small"
              :loading="actionRecordId === record.approvalRecordId && actionType === 'supplement'"
              @click="submitSupplement(record)"
            >
              Submit supplement
            </el-button>
          </div>
        </div>
        <RouterLink class="rule-link" :to="`/rules?ruleId=${record.ruleId}`">Open rule</RouterLink>
      </article>
    </section>

    <el-empty v-else :description="emptyDescription" />
  </section>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { RouterLink } from 'vue-router';
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
const supplementForms = ref<Record<string, { comment: string; evidenceUrl: string }>>({});
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
  if (selectedStatus.value === 'approved') return 'Approved';
  if (selectedStatus.value === 'rejected') return 'Rejected';
  if (selectedStatus.value === 'supplement_required') return 'Supplement required';
  if (selectedStatus.value === 'resubmitted') return 'Resubmitted';
  if (selectedStatus.value === 'closed') return 'Closed';
  return 'Pending';
});

const emptyDescription = computed(() => {
  if (selectedStatus.value === 'approved') return 'No approved approvals';
  if (selectedStatus.value === 'rejected') return 'No rejected approvals';
  if (selectedStatus.value === 'supplement_required') return 'No supplement-required approvals';
  if (selectedStatus.value === 'resubmitted') return 'No resubmitted approvals';
  if (selectedStatus.value === 'closed') return 'No closed approvals';
  return 'No pending approvals';
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
    errorMessage.value = error instanceof Error ? error.message : 'Failed to load pending approvals';
  } finally {
    loading.value = false;
  }
}

function syncSupplementForms() {
  const nextForms: Record<string, { comment: string; evidenceUrl: string }> = {};
  for (const record of approvalRecords.value) {
    if (record.status !== 'rejected') {
      continue;
    }
    const key = String(record.approvalRecordId);
    nextForms[key] = supplementForms.value[key] ?? { comment: '', evidenceUrl: '' };
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
      comment: action === 'approve' ? 'approved from approval inbox' : 'rejected from approval inbox'
    });
    await loadPendingApprovals();
    selectedApprovalIds.value = selectedApprovalIds.value.filter((approvalRecordId) => approvalRecordId !== String(record.approvalRecordId));
    successMessage.value = action === 'approve'
      ? 'Approval approved and downstream flow resumed.'
      : 'Approval rejected. Submit updated materials before running again.';
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : `Failed to ${action} approval`;
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
  const form = supplementForms.value[String(record.approvalRecordId)] ?? { comment: '', evidenceUrl: '' };
  try {
    await ruleApi.submitApprovalSupplement(String(record.ruleId), String(record.approvalRecordId), {
      comment: form.comment.trim() || undefined,
      evidenceUrl: form.evidenceUrl.trim() || undefined
    });
    await loadPendingApprovals();
    successMessage.value = 'Supplement submitted and approval returned to pending review.';
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : 'Failed to submit approval supplement';
  } finally {
    actionRecordId.value = null;
    actionType.value = null;
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
    successMessage.value = 'Reminder sent for this approval.';
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : 'Failed to send approval reminder';
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
      comment: action === 'approve' ? 'approved in batch' : 'rejected in batch'
    }) as unknown as {
      succeededCount: number;
      failedCount: number;
    };
    await loadPendingApprovals();
    selectedApprovalIds.value = [];
    successMessage.value = `Batch approval completed: ${result.succeededCount} succeeded, ${result.failedCount} failed.`;
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : `Failed to batch ${action} approvals`;
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
    `Group ${record.approvalMode || 'all'} ${record.approvalGroupApprovedCount ?? 0}/${record.approvalGroupTotalCount ?? 1} approved`,
    `${record.approvalGroupPendingCount ?? 0} pending`
  ];
  if ((record.approvalGroupRejectedCount ?? 0) > 0) {
    parts.push(`${record.approvalGroupRejectedCount ?? 0} rejected`);
  }
  if ((record.approvalGroupClosedCount ?? 0) > 0) {
    parts.push(`${record.approvalGroupClosedCount ?? 0} closed`);
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
