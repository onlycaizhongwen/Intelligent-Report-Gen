<template>
  <section class="page approval-delegate-page">
    <header class="page-header">
      <div>
        <h2>审批委托</h2>
        <p>审批委托规则</p>
        <p>配置临时代理角色，保障审批任务不断档。</p>
      </div>
    </header>

    <el-alert v-if="errorMessage" :title="errorMessage" type="error" show-icon :closable="false" />
    <el-alert v-if="successMessage" :title="successMessage" type="success" show-icon :closable="false" />

    <section class="delegate-form" aria-label="审批委托规则表单">
      <label>
        <span>原审批角色</span>
        <el-select
          v-model="form.assigneeRole"
          aria-label="原审批组织角色"
          filterable
          class="role-directory-select"
          placeholder="从组织目录选择"
        >
          <el-option
            v-for="option in roleDirectoryOptions"
            :key="`assignee-${option.role}`"
            :label="option.label"
            :value="option.role"
          />
        </el-select>
        <el-input v-model="form.assigneeRole" aria-label="原审批角色" placeholder="finance_manager" />
        <small v-if="roleCandidatesText(form.assigneeRole)">原审批候选人 {{ roleCandidatesText(form.assigneeRole) }}</small>
      </label>
      <label>
        <span>代理角色</span>
        <el-select
          v-model="form.delegateRole"
          aria-label="代理组织角色"
          filterable
          class="role-directory-select"
          placeholder="从组织目录选择"
        >
          <el-option
            v-for="option in roleDirectoryOptions"
            :key="`delegate-${option.role}`"
            :label="option.label"
            :value="option.role"
          />
        </el-select>
        <el-input v-model="form.delegateRole" aria-label="代理角色" placeholder="finance_delegate" />
        <small v-if="roleCandidatesText(form.delegateRole)">代理候选人 {{ roleCandidatesText(form.delegateRole) }}</small>
      </label>
      <label>
        <span>生效开始</span>
        <el-input v-model="form.activeFrom" aria-label="委托生效开始" placeholder="2026-06-26T08:00:00Z" />
      </label>
      <label>
        <span>生效结束</span>
        <el-input v-model="form.activeTo" aria-label="委托生效结束" placeholder="2026-06-26T18:00:00Z" />
      </label>
      <label>
        <span>生效星期</span>
        <el-input v-model="form.activeWeekdays" aria-label="委托生效星期" placeholder="MONDAY,WEDNESDAY" />
      </label>
      <label>
        <span>生效日期</span>
        <el-input v-model="form.activeDates" aria-label="委托生效日期" placeholder="2026-06-26,2026-06-28" />
      </label>
      <label class="reason-field">
        <span>原因</span>
        <el-input v-model="form.reason" aria-label="委托原因" type="textarea" :rows="3" placeholder="季度结账期间代理审批" />
      </label>
      <div class="form-actions">
        <el-button type="primary" :loading="submitting" @click="createDelegateRule">创建委托规则</el-button>
      </div>
    </section>

    <section v-if="createdRule" class="created-rule" aria-label="已创建的委托规则">
      <strong>{{ createdRule.assigneeRole }} -> {{ createdRule.delegateRole }}</strong>
      <span>状态 {{ statusLabel(createdRule.status) }}</span>
      <span v-if="createdRule.activeFrom || createdRule.activeTo">生效窗口 {{ formatWindow(createdRule) }}</span>
      <span v-if="createdRule.activeWeekdays?.length">星期 {{ createdRule.activeWeekdays.join(', ') }}</span>
      <span v-if="createdRule.activeDates?.length">日期 {{ createdRule.activeDates.join(', ') }}</span>
      <span v-if="createdRule.reason">原因 {{ createdRule.reason }}</span>
    </section>

    <section class="delegate-import" aria-label="审批委托批量导入">
      <label>
        <span>批量导入委托规则</span>
        <el-input
          v-model="importText"
          aria-label="批量导入委托规则"
          type="textarea"
          :rows="4"
          placeholder="原审批角色,代理角色,生效开始,生效结束,生效星期,生效日期,原因"
        />
      </label>
      <div class="form-actions">
        <el-button :loading="importingRules" @click="batchImportDelegateRules">导入委托规则</el-button>
      </div>
      <ul v-if="importResults.length" class="import-results" aria-label="委托导入结果">
        <li v-for="result in importResults" :key="result.rowNumber">
          <template v-if="result.status === 'failed'">第 {{ result.rowNumber }} 行失败：{{ result.reason }}</template>
          <template v-else>第 {{ result.rowNumber }} 行已导入：{{ result.assigneeRole }} -> {{ result.delegateRole }}</template>
        </li>
      </ul>
    </section>

    <section class="delegate-calendar" aria-label="委托日程">
      <h3>委托日程</h3>
      <div v-if="calendarDays.length" class="calendar-grid">
        <article v-for="day in calendarDays" :key="day.date" class="calendar-day">
          <strong>{{ day.date }}</strong>
          <div v-for="rule in day.rules" :key="`${day.date}-${rule.delegateRuleId}`" class="calendar-rule">
            <span>{{ rule.assigneeRole }} -> {{ rule.delegateRole }}</span>
            <small v-if="rule.activeWeekdays?.length">星期 {{ rule.activeWeekdays.join(', ') }}</small>
            <small v-if="rule.activeFrom || rule.activeTo">生效窗口 {{ formatWindow(rule) }}</small>
          </div>
        </article>
      </div>
      <el-empty v-else description="暂无指定日期的委托规则" />
      <div v-if="recurringRules.length" class="calendar-recurring">
        <h4>重复 / 时间窗口规则</h4>
        <article v-for="rule in recurringRules" :key="`recurring-${rule.delegateRuleId}`" class="calendar-rule">
          <span>{{ rule.assigneeRole }} -> {{ rule.delegateRole }}</span>
          <small v-if="rule.activeWeekdays?.length">星期 {{ rule.activeWeekdays.join(', ') }}</small>
          <small v-if="rule.activeFrom || rule.activeTo">生效窗口 {{ formatWindow(rule) }}</small>
        </article>
      </div>
    </section>

    <section class="delegate-rule-list" aria-label="审批委托规则列表">
      <div class="list-header">
        <h3>已有委托规则</h3>
        <div class="list-actions">
          <el-button @click="exportDelegateRules">导出委托规则</el-button>
          <el-button :loading="loadingRules" @click="loadDelegateRules">刷新</el-button>
        </div>
      </div>
      <el-empty v-if="!loadingRules && delegateRules.length === 0" description="暂无委托规则" />
      <article
        v-for="rule in delegateRules"
        :key="String(rule.delegateRuleId ?? `${rule.assigneeRole}-${rule.delegateRole}`)"
        class="delegate-rule-card"
      >
        <div class="rule-summary">
          <strong>{{ rule.assigneeRole }} -> {{ rule.delegateRole }}</strong>
          <span>状态 {{ statusLabel(rule.status) }}</span>
          <span v-if="rule.activeFrom || rule.activeTo">生效窗口 {{ formatWindow(rule) }}</span>
          <span v-if="rule.activeWeekdays?.length">星期 {{ rule.activeWeekdays.join(', ') }}</span>
          <span v-if="rule.activeDates?.length">日期 {{ rule.activeDates.join(', ') }}</span>
          <span v-if="rule.reason">原因 {{ rule.reason }}</span>
          <span v-if="rule.createdAt">创建时间 {{ rule.createdAt }}</span>
        </div>
        <div v-if="editingRuleId === String(rule.delegateRuleId)" class="edit-actions">
          <el-select
            v-model="editForm.assigneeRole"
            aria-label="编辑原审批组织角色"
            filterable
            class="role-directory-select"
            placeholder="从组织目录选择"
          >
            <el-option
              v-for="option in roleDirectoryOptions"
              :key="`edit-assignee-${option.role}`"
              :label="option.label"
              :value="option.role"
            />
          </el-select>
          <el-input v-model="editForm.assigneeRole" aria-label="编辑原审批角色" placeholder="finance_manager" />
          <small v-if="roleCandidatesText(editForm.assigneeRole)">原审批候选人 {{ roleCandidatesText(editForm.assigneeRole) }}</small>
          <el-select
            v-model="editForm.delegateRole"
            aria-label="编辑代理组织角色"
            filterable
            class="role-directory-select"
            placeholder="从组织目录选择"
          >
            <el-option
              v-for="option in roleDirectoryOptions"
              :key="`edit-delegate-${option.role}`"
              :label="option.label"
              :value="option.role"
            />
          </el-select>
          <el-input v-model="editForm.delegateRole" aria-label="编辑代理角色" placeholder="finance_delegate" />
          <small v-if="roleCandidatesText(editForm.delegateRole)">代理候选人 {{ roleCandidatesText(editForm.delegateRole) }}</small>
          <el-input v-model="editForm.activeFrom" aria-label="编辑生效开始" placeholder="2026-06-26T08:00:00Z" />
          <el-input v-model="editForm.activeTo" aria-label="编辑生效结束" placeholder="2026-06-26T18:00:00Z" />
          <el-input v-model="editForm.activeWeekdays" aria-label="编辑生效星期" placeholder="MONDAY,WEDNESDAY" />
          <el-input v-model="editForm.activeDates" aria-label="编辑生效日期" placeholder="2026-06-26,2026-06-28" />
          <el-input v-model="editForm.reason" aria-label="编辑原因" type="textarea" :rows="2" placeholder="总监出差期间代理审批" />
          <div class="edit-buttons">
            <el-button
              type="primary"
              :loading="savingRuleId === String(rule.delegateRuleId)"
              @click="saveEdit(rule)"
            >
              保存编辑
            </el-button>
            <el-button @click="cancelEdit">取消</el-button>
          </div>
        </div>
        <div v-else-if="rule.status === 'enabled'" class="rule-actions">
          <el-button @click="startEdit(rule)">编辑</el-button>
          <el-input
            v-model="disableReasons[String(rule.delegateRuleId)]"
            aria-label="停用原因"
            placeholder="负责人已返回"
          />
          <el-button
            :loading="disablingRuleId === String(rule.delegateRuleId)"
            @click="disableRule(rule)"
          >
            停用
          </el-button>
        </div>
        <div v-else class="rule-actions">
          <el-button @click="startEdit(rule)">编辑</el-button>
          <el-input
            v-model="enableReasons[String(rule.delegateRuleId)]"
            aria-label="启用原因"
            placeholder="负责人再次外出"
          />
          <el-button
            :loading="enablingRuleId === String(rule.delegateRuleId)"
            @click="enableRule(rule)"
          >
            启用
          </el-button>
        </div>
      </article>
    </section>
  </section>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue';
import { adminApi, type OrganizationDirectory } from '../../api/adminApi';
import { isLocalPreviewUnauthorizedError } from '../../api/client';
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
      label: `${roleItem.department || '无部门'} / ${roleItem.position || '无岗位'} / ${role} / ${displayName}`
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
    errorMessage.value = '请填写原审批角色和代理角色。';
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
    successMessage.value = `委托规则已创建：${result.assigneeRole} -> ${result.delegateRole}`;
    await loadDelegateRules();
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : '委托规则创建失败';
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
    if (isLocalPreviewUnauthorizedError(error)) {
      delegateRules.value = [];
      return;
    }
    errorMessage.value = error instanceof Error ? error.message : '委托规则加载失败';
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
    errorMessage.value = '至少需要填写一行委托规则。';
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
    successMessage.value = `委托规则导入完成：成功 ${result.imported ?? 0} 行，失败 ${result.failed ?? 0} 行`;
    await loadDelegateRules();
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : '委托规则导入失败';
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
  } catch (error) {
    if (isLocalPreviewUnauthorizedError(error)) {
      organizationDirectory.value = { departments: [], roles: [] };
      return;
    }
    organizationDirectory.value = { departments: [], roles: [] };
  }
}

async function disableRule(rule: ApprovalDelegateRuleResponse) {
  const delegateRuleId = rule.delegateRuleId;
  if (delegateRuleId === undefined || delegateRuleId === null) {
    errorMessage.value = '缺少委托规则 ID。';
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
    successMessage.value = `委托规则已停用：${disabled.assigneeRole} -> ${disabled.delegateRole}`;
    delegateRules.value = delegateRules.value.map((item) => (
      String(item.delegateRuleId) === id ? disabled : item
    ));
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : '委托规则停用失败';
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
    errorMessage.value = '缺少委托规则 ID。';
    return;
  }
  errorMessage.value = '';
  successMessage.value = '';
  if (!editForm.assigneeRole.trim() || !editForm.delegateRole.trim()) {
    errorMessage.value = '请填写原审批角色和代理角色。';
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
    successMessage.value = `委托规则已更新：${updated.assigneeRole} -> ${updated.delegateRole}`;
    delegateRules.value = delegateRules.value.map((item) => (
      String(item.delegateRuleId) === id ? updated : item
    ));
    editingRuleId.value = '';
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : '委托规则更新失败';
  } finally {
    savingRuleId.value = '';
  }
}

async function enableRule(rule: ApprovalDelegateRuleResponse) {
  const delegateRuleId = rule.delegateRuleId;
  if (delegateRuleId === undefined || delegateRuleId === null) {
    errorMessage.value = '缺少委托规则 ID。';
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
    successMessage.value = `委托规则已启用：${enabled.assigneeRole} -> ${enabled.delegateRole}`;
    delegateRules.value = delegateRules.value.map((item) => (
      String(item.delegateRuleId) === id ? enabled : item
    ));
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : '委托规则启用失败';
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
      roleItem.department || '无部门',
      roleItem.position || '无岗位'
    ].join(' / ')))
    .join('; ');
}

function statusLabel(status: string) {
  if (status === 'enabled' || status === 'active') return '启用';
  if (status === 'disabled') return '停用';
  return status;
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
