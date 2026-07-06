<template>
  <section class="page approval-delegate-page">
    <header class="page-header">
      <div>
        <h2>Approval Delegate Rules</h2>
        <p>Configure temporary delegate roles for rule approval tasks.</p>
      </div>
    </header>

    <el-alert v-if="errorMessage" :title="errorMessage" type="error" show-icon :closable="false" />
    <el-alert v-if="successMessage" :title="successMessage" type="success" show-icon :closable="false" />

    <section class="delegate-form" aria-label="Approval delegate rule form">
      <label>
        <span>Assignee role</span>
        <el-select
          v-model="form.assigneeRole"
          aria-label="Assignee organization role"
          filterable
          class="role-directory-select"
          placeholder="Select from organization directory"
        >
          <el-option
            v-for="option in roleDirectoryOptions"
            :key="`assignee-${option.role}`"
            :label="option.label"
            :value="option.role"
          />
        </el-select>
        <el-input v-model="form.assigneeRole" aria-label="Delegate assignee role" placeholder="finance_manager" />
        <small v-if="roleCandidatesText(form.assigneeRole)">Assignee candidates {{ roleCandidatesText(form.assigneeRole) }}</small>
      </label>
      <label>
        <span>Delegate role</span>
        <el-select
          v-model="form.delegateRole"
          aria-label="Delegate organization role"
          filterable
          class="role-directory-select"
          placeholder="Select from organization directory"
        >
          <el-option
            v-for="option in roleDirectoryOptions"
            :key="`delegate-${option.role}`"
            :label="option.label"
            :value="option.role"
          />
        </el-select>
        <el-input v-model="form.delegateRole" aria-label="Delegate role" placeholder="finance_delegate" />
        <small v-if="roleCandidatesText(form.delegateRole)">Delegate candidates {{ roleCandidatesText(form.delegateRole) }}</small>
      </label>
      <label>
        <span>Active from</span>
        <el-input v-model="form.activeFrom" aria-label="Delegate active from" placeholder="2026-06-26T08:00:00Z" />
      </label>
      <label>
        <span>Active to</span>
        <el-input v-model="form.activeTo" aria-label="Delegate active to" placeholder="2026-06-26T18:00:00Z" />
      </label>
      <label>
        <span>Active weekdays</span>
        <el-input v-model="form.activeWeekdays" aria-label="Delegate active weekdays" placeholder="MONDAY,WEDNESDAY" />
      </label>
      <label>
        <span>Active dates</span>
        <el-input v-model="form.activeDates" aria-label="Delegate active dates" placeholder="2026-06-26,2026-06-28" />
      </label>
      <label class="reason-field">
        <span>Reason</span>
        <el-input v-model="form.reason" aria-label="Delegate reason" type="textarea" :rows="3" placeholder="quarter close coverage" />
      </label>
      <div class="form-actions">
        <el-button type="primary" :loading="submitting" @click="createDelegateRule">Create delegate rule</el-button>
      </div>
    </section>

    <section v-if="createdRule" class="created-rule" aria-label="Created delegate rule">
      <strong>{{ createdRule.assigneeRole }} -> {{ createdRule.delegateRole }}</strong>
      <span>Status {{ createdRule.status }}</span>
      <span v-if="createdRule.activeFrom || createdRule.activeTo">Window {{ formatWindow(createdRule) }}</span>
      <span v-if="createdRule.activeWeekdays?.length">Weekdays {{ createdRule.activeWeekdays.join(', ') }}</span>
      <span v-if="createdRule.activeDates?.length">Dates {{ createdRule.activeDates.join(', ') }}</span>
      <span v-if="createdRule.reason">Reason {{ createdRule.reason }}</span>
    </section>

    <section class="delegate-import" aria-label="Approval delegate batch import">
      <label>
        <span>Batch import delegate rules</span>
        <el-input
          v-model="importText"
          aria-label="Batch import delegate rules"
          type="textarea"
          :rows="4"
          placeholder="assigneeRole,delegateRole,activeFrom,activeTo,activeWeekdays,activeDates,reason"
        />
      </label>
      <div class="form-actions">
        <el-button :loading="importingRules" @click="batchImportDelegateRules">Import delegate rules</el-button>
      </div>
      <ul v-if="importResults.length" class="import-results" aria-label="Delegate import results">
        <li v-for="result in importResults" :key="result.rowNumber">
          <template v-if="result.status === 'failed'">Row {{ result.rowNumber }} failed: {{ result.reason }}</template>
          <template v-else>Row {{ result.rowNumber }} imported: {{ result.assigneeRole }} -> {{ result.delegateRole }}</template>
        </li>
      </ul>
    </section>

    <section class="delegate-calendar" aria-label="Delegate schedule calendar">
      <h3>Delegate schedule calendar</h3>
      <div v-if="calendarDays.length" class="calendar-grid">
        <article v-for="day in calendarDays" :key="day.date" class="calendar-day">
          <strong>{{ day.date }}</strong>
          <div v-for="rule in day.rules" :key="`${day.date}-${rule.delegateRuleId}`" class="calendar-rule">
            <span>{{ rule.assigneeRole }} -> {{ rule.delegateRole }}</span>
            <small v-if="rule.activeWeekdays?.length">Weekdays {{ rule.activeWeekdays.join(', ') }}</small>
            <small v-if="rule.activeFrom || rule.activeTo">Window {{ formatWindow(rule) }}</small>
          </div>
        </article>
      </div>
      <el-empty v-else description="No date-specific delegate rules" />
      <div v-if="recurringRules.length" class="calendar-recurring">
        <h4>Recurring / window based</h4>
        <article v-for="rule in recurringRules" :key="`recurring-${rule.delegateRuleId}`" class="calendar-rule">
          <span>{{ rule.assigneeRole }} -> {{ rule.delegateRole }}</span>
          <small v-if="rule.activeWeekdays?.length">Weekdays {{ rule.activeWeekdays.join(', ') }}</small>
          <small v-if="rule.activeFrom || rule.activeTo">Window {{ formatWindow(rule) }}</small>
        </article>
      </div>
    </section>

    <section class="delegate-rule-list" aria-label="Approval delegate rules">
      <div class="list-header">
        <h3>Existing delegate rules</h3>
        <div class="list-actions">
          <el-button @click="exportDelegateRules">Export delegate rules</el-button>
          <el-button :loading="loadingRules" @click="loadDelegateRules">Refresh</el-button>
        </div>
      </div>
      <el-empty v-if="!loadingRules && delegateRules.length === 0" description="No delegate rules" />
      <article
        v-for="rule in delegateRules"
        :key="String(rule.delegateRuleId ?? `${rule.assigneeRole}-${rule.delegateRole}`)"
        class="delegate-rule-card"
      >
        <div class="rule-summary">
          <strong>{{ rule.assigneeRole }} -> {{ rule.delegateRole }}</strong>
          <span>Status {{ rule.status }}</span>
          <span v-if="rule.activeFrom || rule.activeTo">Window {{ formatWindow(rule) }}</span>
          <span v-if="rule.activeWeekdays?.length">Weekdays {{ rule.activeWeekdays.join(', ') }}</span>
          <span v-if="rule.activeDates?.length">Dates {{ rule.activeDates.join(', ') }}</span>
          <span v-if="rule.reason">Reason {{ rule.reason }}</span>
          <span v-if="rule.createdAt">Created {{ rule.createdAt }}</span>
        </div>
        <div v-if="editingRuleId === String(rule.delegateRuleId)" class="edit-actions">
          <el-select
            v-model="editForm.assigneeRole"
            aria-label="Edit assignee organization role"
            filterable
            class="role-directory-select"
            placeholder="Select from organization directory"
          >
            <el-option
              v-for="option in roleDirectoryOptions"
              :key="`edit-assignee-${option.role}`"
              :label="option.label"
              :value="option.role"
            />
          </el-select>
          <el-input v-model="editForm.assigneeRole" aria-label="Edit assignee role" placeholder="finance_manager" />
          <small v-if="roleCandidatesText(editForm.assigneeRole)">Assignee candidates {{ roleCandidatesText(editForm.assigneeRole) }}</small>
          <el-select
            v-model="editForm.delegateRole"
            aria-label="Edit delegate organization role"
            filterable
            class="role-directory-select"
            placeholder="Select from organization directory"
          >
            <el-option
              v-for="option in roleDirectoryOptions"
              :key="`edit-delegate-${option.role}`"
              :label="option.label"
              :value="option.role"
            />
          </el-select>
          <el-input v-model="editForm.delegateRole" aria-label="Edit delegate role" placeholder="finance_delegate" />
          <small v-if="roleCandidatesText(editForm.delegateRole)">Delegate candidates {{ roleCandidatesText(editForm.delegateRole) }}</small>
          <el-input v-model="editForm.activeFrom" aria-label="Edit active from" placeholder="2026-06-26T08:00:00Z" />
          <el-input v-model="editForm.activeTo" aria-label="Edit active to" placeholder="2026-06-26T18:00:00Z" />
          <el-input v-model="editForm.activeWeekdays" aria-label="Edit active weekdays" placeholder="MONDAY,WEDNESDAY" />
          <el-input v-model="editForm.activeDates" aria-label="Edit active dates" placeholder="2026-06-26,2026-06-28" />
          <el-input v-model="editForm.reason" aria-label="Edit reason" type="textarea" :rows="2" placeholder="director travel cover" />
          <div class="edit-buttons">
            <el-button
              type="primary"
              :loading="savingRuleId === String(rule.delegateRuleId)"
              @click="saveEdit(rule)"
            >
              Save edit
            </el-button>
            <el-button @click="cancelEdit">Cancel</el-button>
          </div>
        </div>
        <div v-else-if="rule.status === 'enabled'" class="rule-actions">
          <el-button @click="startEdit(rule)">Edit</el-button>
          <el-input
            v-model="disableReasons[String(rule.delegateRuleId)]"
            aria-label="Disable reason"
            placeholder="manager returned"
          />
          <el-button
            :loading="disablingRuleId === String(rule.delegateRuleId)"
            @click="disableRule(rule)"
          >
            Disable
          </el-button>
        </div>
        <div v-else class="rule-actions">
          <el-button @click="startEdit(rule)">Edit</el-button>
          <el-input
            v-model="enableReasons[String(rule.delegateRuleId)]"
            aria-label="Enable reason"
            placeholder="manager away again"
          />
          <el-button
            :loading="enablingRuleId === String(rule.delegateRuleId)"
            @click="enableRule(rule)"
          >
            Enable
          </el-button>
        </div>
      </article>
    </section>
  </section>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue';
import { adminApi, type OrganizationDirectory } from '../../api/adminApi';
import { ruleApi } from '../../api/ruleApi';

interface ApprovalDelegateRuleResponse {
  delegateRuleId?: number | string;
  assigneeRole: string;
  delegateRole: string;
  activeFrom?: string | null;
  activeTo?: string | null;
  activeWeekdays?: string[];
  activeDates?: string[];
  status: string;
  reason?: string | null;
  createdAt?: string | null;
}

const form = reactive({
  assigneeRole: '',
  delegateRole: '',
  activeFrom: '',
  activeTo: '',
  activeWeekdays: '',
  activeDates: '',
  reason: ''
});

const submitting = ref(false);
const errorMessage = ref('');
const successMessage = ref('');
const createdRule = ref<ApprovalDelegateRuleResponse | null>(null);
const delegateRules = ref<ApprovalDelegateRuleResponse[]>([]);
const organizationDirectory = ref<OrganizationDirectory>({ departments: [], roles: [] });
const loadingRules = ref(false);
const importingRules = ref(false);
const disablingRuleId = ref('');
const enablingRuleId = ref('');
const editingRuleId = ref('');
const savingRuleId = ref('');
const importText = ref('');
const importResults = ref<Array<{
  rowNumber: number;
  status: string;
  reason?: string;
  assigneeRole?: string;
  delegateRole?: string;
}>>([]);
const disableReasons = reactive<Record<string, string>>({});
const enableReasons = reactive<Record<string, string>>({});
const editForm = reactive({
  assigneeRole: '',
  delegateRole: '',
  activeFrom: '',
  activeTo: '',
  activeWeekdays: '',
  activeDates: '',
  reason: ''
});

const roleDirectoryOptions = computed(() => {
  const options = new Map<string, { role: string; label: string }>();
  organizationDirectory.value.roles.forEach((roleItem) => {
    const role = roleItem.role.trim();
    if (!role || options.has(role)) {
      return;
    }
    const user = roleItem.users[0];
    const displayName = user?.displayName?.trim() || user?.username || role;
    options.set(role, {
      role,
      label: `${roleItem.department || 'No department'} / ${roleItem.position || 'No position'} / ${role} / ${displayName}`
    });
  });
  return Array.from(options.values()).sort((left, right) => left.label.localeCompare(right.label));
});

const calendarDays = computed(() => {
  const days = new Map<string, ApprovalDelegateRuleResponse[]>();
  delegateRules.value
    .filter((rule) => rule.status === 'enabled')
    .forEach((rule) => {
      (rule.activeDates ?? []).forEach((date) => {
        if (!days.has(date)) {
          days.set(date, []);
        }
        days.get(date)?.push(rule);
      });
    });
  return Array.from(days.entries())
    .sort(([left], [right]) => left.localeCompare(right))
    .map(([date, rules]) => ({ date, rules }));
});

const recurringRules = computed(() => delegateRules.value
  .filter((rule) => rule.status === 'enabled' && (rule.activeDates ?? []).length === 0));

onMounted(() => {
  void loadDelegateRules();
  void loadOrganizationDirectory();
});

function blankToUndefined(value: string) {
  const trimmed = value.trim();
  return trimmed ? trimmed : undefined;
}

function weekdays(value: string) {
  return value
    .split(',')
    .map((item) => item.trim().toUpperCase())
    .filter((item, index, items) => item && items.indexOf(item) === index);
}

function dates(value: string) {
  return value
    .split(',')
    .map((item) => item.trim())
    .filter((item, index, items) => item && items.indexOf(item) === index);
}

async function createDelegateRule() {
  errorMessage.value = '';
  successMessage.value = '';
  createdRule.value = null;
  if (!form.assigneeRole.trim() || !form.delegateRole.trim()) {
    errorMessage.value = 'Assignee role and delegate role are required.';
    return;
  }
  submitting.value = true;
  try {
    const result = await ruleApi.createApprovalDelegateRule({
      assigneeRole: form.assigneeRole.trim(),
      delegateRole: form.delegateRole.trim(),
      activeFrom: blankToUndefined(form.activeFrom),
      activeTo: blankToUndefined(form.activeTo),
      activeWeekdays: weekdays(form.activeWeekdays),
      activeDates: dates(form.activeDates),
      reason: blankToUndefined(form.reason)
    }) as unknown as ApprovalDelegateRuleResponse;
    createdRule.value = result;
    successMessage.value = `Delegate rule created: ${result.assigneeRole} -> ${result.delegateRole}`;
    await loadDelegateRules();
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : 'Failed to create delegate rule';
  } finally {
    submitting.value = false;
  }
}

async function loadDelegateRules() {
  loadingRules.value = true;
  try {
    const result = await ruleApi.listApprovalDelegateRules({ page: 1, pageSize: 20 }) as unknown as {
      items?: ApprovalDelegateRuleResponse[];
      data?: { items?: ApprovalDelegateRuleResponse[] };
    };
    delegateRules.value = result.items ?? result.data?.items ?? [];
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : 'Failed to load delegate rules';
  } finally {
    loadingRules.value = false;
  }
}

async function batchImportDelegateRules() {
  errorMessage.value = '';
  successMessage.value = '';
  importResults.value = [];
  const rules = parseImportRows(importText.value);
  if (rules.length === 0) {
    errorMessage.value = 'At least one delegate rule row is required.';
    return;
  }
  importingRules.value = true;
  try {
    const result = await ruleApi.batchImportApprovalDelegateRules({ rules }) as unknown as {
      imported?: number;
      failed?: number;
      results?: Array<{
        rowNumber: number;
        status: string;
        reason?: string;
        assigneeRole?: string;
        delegateRole?: string;
      }>;
    };
    importResults.value = result.results ?? [];
    successMessage.value = `Delegate rules imported: ${result.imported ?? 0} imported, ${result.failed ?? 0} failed`;
    await loadDelegateRules();
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : 'Failed to import delegate rules';
  } finally {
    importingRules.value = false;
  }
}

function parseImportRows(value: string) {
  return value
    .split(/\r?\n/)
    .map((line) => line.trim())
    .filter((line) => line && !line.toLowerCase().startsWith('assigneerole,'))
    .map((line) => {
      const columns = line.split(',').map((item) => item.trim());
      return {
        assigneeRole: columns[0] ?? '',
        delegateRole: columns[1] ?? '',
        activeFrom: blankToUndefined(columns[2] ?? ''),
        activeTo: blankToUndefined(columns[3] ?? ''),
        activeWeekdays: splitMultiValue(columns[4] ?? ''),
        activeDates: splitMultiValue(columns[5] ?? ''),
        reason: blankToUndefined(columns.slice(6).join(','))
      };
    });
}

function splitMultiValue(value: string) {
  return value
    .split('|')
    .map((item) => item.trim())
    .filter((item, index, items) => item && items.indexOf(item) === index);
}

function exportDelegateRules() {
  const headers = ['delegateRuleId', 'assigneeRole', 'delegateRole', 'activeFrom', 'activeTo', 'activeWeekdays', 'activeDates', 'status', 'reason'];
  const rows = delegateRules.value.map((rule) => [
    rule.delegateRuleId ?? '',
    rule.assigneeRole,
    rule.delegateRole,
    rule.activeFrom ?? '',
    rule.activeTo ?? '',
    (rule.activeWeekdays ?? []).join('|'),
    (rule.activeDates ?? []).join('|'),
    rule.status,
    rule.reason ?? ''
  ]);
  const csv = [
    headers.join(','),
    ...rows.map((row) => row.map(csvCell).join(','))
  ].join('\n');
  const blob = new Blob([csv], { type: 'text/csv;charset=utf-8' });
  const url = URL.createObjectURL(blob);
  const link = document.createElement('a');
  link.href = url;
  link.download = 'approval-delegate-rules.csv';
  document.body.appendChild(link);
  link.click();
  document.body.removeChild(link);
  URL.revokeObjectURL(url);
}

function csvCell(value: unknown) {
  const text = String(value ?? '');
  return /[",\n\r]/.test(text) ? `"${text.replace(/"/g, '""')}"` : text;
}

async function loadOrganizationDirectory() {
  try {
    const result = await adminApi.organizationDirectory() as unknown as OrganizationDirectory;
    organizationDirectory.value = {
      departments: result.departments ?? [],
      roles: result.roles ?? []
    };
  } catch {
    organizationDirectory.value = { departments: [], roles: [] };
  }
}

async function disableRule(rule: ApprovalDelegateRuleResponse) {
  const delegateRuleId = rule.delegateRuleId;
  if (delegateRuleId === undefined || delegateRuleId === null) {
    errorMessage.value = 'Delegate rule id is required.';
    return;
  }
  errorMessage.value = '';
  successMessage.value = '';
  const id = String(delegateRuleId);
  disablingRuleId.value = id;
  try {
    const disabled = await ruleApi.disableApprovalDelegateRule(id, {
      reason: blankToUndefined(disableReasons[id] ?? '')
    }) as unknown as ApprovalDelegateRuleResponse;
    successMessage.value = `Delegate rule disabled: ${disabled.assigneeRole} -> ${disabled.delegateRole}`;
    delegateRules.value = delegateRules.value.map((item) => (
      String(item.delegateRuleId) === id ? disabled : item
    ));
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : 'Failed to disable delegate rule';
  } finally {
    disablingRuleId.value = '';
  }
}

function startEdit(rule: ApprovalDelegateRuleResponse) {
  editingRuleId.value = String(rule.delegateRuleId);
  editForm.assigneeRole = rule.assigneeRole;
  editForm.delegateRole = rule.delegateRole;
  editForm.activeFrom = rule.activeFrom ?? '';
  editForm.activeTo = rule.activeTo ?? '';
  editForm.activeWeekdays = (rule.activeWeekdays ?? []).join(',');
  editForm.activeDates = (rule.activeDates ?? []).join(',');
  editForm.reason = rule.reason ?? '';
}

function cancelEdit() {
  editingRuleId.value = '';
}

async function saveEdit(rule: ApprovalDelegateRuleResponse) {
  const delegateRuleId = rule.delegateRuleId;
  if (delegateRuleId === undefined || delegateRuleId === null) {
    errorMessage.value = 'Delegate rule id is required.';
    return;
  }
  errorMessage.value = '';
  successMessage.value = '';
  if (!editForm.assigneeRole.trim() || !editForm.delegateRole.trim()) {
    errorMessage.value = 'Assignee role and delegate role are required.';
    return;
  }
  const id = String(delegateRuleId);
  savingRuleId.value = id;
  try {
    const updated = await ruleApi.updateApprovalDelegateRule(id, {
      assigneeRole: editForm.assigneeRole.trim(),
      delegateRole: editForm.delegateRole.trim(),
      activeFrom: blankToUndefined(editForm.activeFrom),
      activeTo: blankToUndefined(editForm.activeTo),
      activeWeekdays: weekdays(editForm.activeWeekdays),
      activeDates: dates(editForm.activeDates),
      reason: blankToUndefined(editForm.reason)
    }) as unknown as ApprovalDelegateRuleResponse;
    successMessage.value = `Delegate rule updated: ${updated.assigneeRole} -> ${updated.delegateRole}`;
    delegateRules.value = delegateRules.value.map((item) => (
      String(item.delegateRuleId) === id ? updated : item
    ));
    editingRuleId.value = '';
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : 'Failed to update delegate rule';
  } finally {
    savingRuleId.value = '';
  }
}

async function enableRule(rule: ApprovalDelegateRuleResponse) {
  const delegateRuleId = rule.delegateRuleId;
  if (delegateRuleId === undefined || delegateRuleId === null) {
    errorMessage.value = 'Delegate rule id is required.';
    return;
  }
  errorMessage.value = '';
  successMessage.value = '';
  const id = String(delegateRuleId);
  enablingRuleId.value = id;
  try {
    const enabled = await ruleApi.enableApprovalDelegateRule(id, {
      reason: blankToUndefined(enableReasons[id] ?? '')
    }) as unknown as ApprovalDelegateRuleResponse;
    successMessage.value = `Delegate rule enabled: ${enabled.assigneeRole} -> ${enabled.delegateRole}`;
    delegateRules.value = delegateRules.value.map((item) => (
      String(item.delegateRuleId) === id ? enabled : item
    ));
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : 'Failed to enable delegate rule';
  } finally {
    enablingRuleId.value = '';
  }
}

function formatWindow(rule: ApprovalDelegateRuleResponse) {
  const from = rule.activeFrom || '*';
  const to = rule.activeTo || '*';
  return `${from}..${to}`;
}

function roleCandidatesText(role: string) {
  const normalizedRole = role.trim();
  if (!normalizedRole) {
    return '';
  }
  return organizationDirectory.value.roles
    .filter((roleItem) => roleItem.role === normalizedRole)
    .flatMap((roleItem) => roleItem.users.map((user) => [
      user.displayName || user.username,
      roleItem.department || 'No department',
      roleItem.position || 'No position'
    ].join(' / ')))
    .join('; ');
}
</script>

<style scoped>
.approval-delegate-page {
  display: flex;
  flex-direction: column;
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

.delegate-form {
  display: grid;
  grid-template-columns: repeat(2, minmax(240px, 1fr));
  gap: 14px 16px;
  max-width: 840px;
}

.delegate-form label,
.delegate-import label {
  display: flex;
  flex-direction: column;
  gap: 6px;
  color: #303133;
  font-weight: 600;
}

.delegate-form label span,
.delegate-import label span {
  font-size: 13px;
}

.delegate-form label small,
.edit-actions small {
  color: #606266;
  font-size: 12px;
  line-height: 1.4;
}

.reason-field {
  grid-column: 1 / -1;
}

.form-actions {
  grid-column: 1 / -1;
}

.created-rule {
  display: flex;
  flex-direction: column;
  gap: 6px;
  padding: 14px 16px;
  max-width: 840px;
  background: #ffffff;
  border: 1px solid #dcdfe6;
  border-radius: 8px;
}

.created-rule span {
  color: #606266;
}

.delegate-import {
  display: flex;
  flex-direction: column;
  gap: 10px;
  max-width: 840px;
}

.delegate-calendar {
  display: flex;
  flex-direction: column;
  gap: 12px;
  max-width: 840px;
}

.delegate-calendar h3,
.delegate-calendar h4 {
  margin: 0;
}

.calendar-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(220px, 1fr));
  gap: 12px;
}

.calendar-day,
.calendar-recurring {
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: 12px;
  background: #ffffff;
  border: 1px solid #dcdfe6;
  border-radius: 8px;
}

.calendar-rule {
  display: flex;
  flex-direction: column;
  gap: 4px;
  padding: 8px;
  background: #f5f7fa;
  border-radius: 6px;
}

.calendar-rule small {
  color: #606266;
}

.import-results {
  margin: 0;
  padding-left: 18px;
  color: #606266;
  line-height: 1.6;
}

.delegate-rule-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
  max-width: 840px;
}

.list-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.list-actions {
  display: flex;
  gap: 8px;
}

.list-header h3 {
  margin: 0;
  font-size: 18px;
}

.delegate-rule-card {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(240px, 320px);
  gap: 14px;
  padding: 14px 16px;
  background: #ffffff;
  border: 1px solid #dcdfe6;
  border-radius: 8px;
}

.rule-summary {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.rule-summary span {
  color: #606266;
}

.rule-actions {
  display: flex;
  align-items: flex-start;
  gap: 8px;
}

.edit-actions {
  display: grid;
  grid-template-columns: 1fr;
  gap: 8px;
}

.edit-buttons {
  display: flex;
  gap: 8px;
}

@media (max-width: 720px) {
  .delegate-form,
  .delegate-rule-card {
    grid-template-columns: 1fr;
  }

  .list-header,
  .list-actions {
    align-items: stretch;
    flex-direction: column;
  }

  .rule-actions {
    flex-direction: column;
  }
}
</style>
