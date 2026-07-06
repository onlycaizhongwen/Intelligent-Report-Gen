<template>
  <section class="page approval-template-page">
    <header class="page-header">
      <div>
        <h2>Approval Templates</h2>
        <p>Manage reusable approval steps for rule workflows.</p>
      </div>
      <el-button :loading="loadingTemplates" @click="loadApprovalTemplates">Refresh</el-button>
    </header>

    <el-alert v-if="errorMessage" :title="errorMessage" type="error" show-icon :closable="false" />
    <el-alert v-if="successMessage" :title="successMessage" type="success" show-icon :closable="false" />

    <section
      v-if="disableCandidate"
      class="confirm-panel"
      role="dialog"
      aria-modal="true"
      aria-label="Disable referenced approval template"
    >
      <h3>Disable referenced approval template?</h3>
      <p>{{ disableCandidate.name }} is used by {{ disableCandidate.usageCount ?? 0 }} rule(s).</p>
      <ul>
        <li v-for="usageRule in disableCandidate.usageRules ?? []" :key="String(usageRule.ruleId)">
          {{ usageRule.name }} ({{ usageRule.status }})
        </li>
      </ul>
      <div class="confirm-actions">
        <el-button @click="disableCandidate = null">Cancel</el-button>
        <el-button type="danger" @click="confirmDisableTemplate">Confirm disable</el-button>
      </div>
    </section>

    <section
      v-if="rollbackCandidate"
      class="confirm-panel"
      role="dialog"
      aria-modal="true"
      aria-label="Rollback approval template version"
    >
      <h3>Rollback approval template version?</h3>
      <p>{{ rollbackCandidate.template.name }} will restore version {{ rollbackCandidate.version }} as a new current version.</p>
      <div class="confirm-actions">
        <el-button @click="rollbackCandidate = null">Cancel</el-button>
        <el-button type="warning" :loading="rollbackLoadingTemplateId === rollbackCandidate.template.approvalTemplateId" @click="confirmRollbackTemplateVersion">
          Confirm rollback
        </el-button>
      </div>
    </section>

    <section class="template-form" aria-label="Approval template form">
      <label>
        <span>Name</span>
        <el-input v-model="form.name" aria-label="Template name" placeholder="Finance two-level approval" />
      </label>
      <label>
        <span>Description</span>
        <el-input
          v-model="form.description"
          aria-label="Template description"
          placeholder="Finance manager then finance director"
        />
      </label>
      <label>
        <span>Status</span>
        <select v-model="form.status" aria-label="Template status">
          <option value="enabled">enabled</option>
          <option value="disabled">disabled</option>
        </select>
      </label>
      <label class="steps-field">
        <span>Steps</span>
        <el-input
          v-model="form.stepsText"
          aria-label="Approval template steps"
          type="textarea"
          :rows="5"
          placeholder="stepId|approvalTitle|roleA,roleB|all|4|8:finance_director"
        />
      </label>
      <div class="form-actions">
        <el-button type="primary" :loading="submitting" @click="submitTemplate">
          {{ editingTemplate ? 'Save approval template changes' : 'Create approval template' }}
        </el-button>
        <el-button v-if="editingTemplate" @click="cancelEditTemplate">Cancel edit</el-button>
      </div>
    </section>

    <section class="template-list" aria-label="Approval templates">
      <h3>Existing templates</h3>
      <el-empty v-if="!loadingTemplates && templates.length === 0" description="No approval templates" />
      <article
        v-for="template in templates"
        :key="String(template.approvalTemplateId)"
        class="template-card"
      >
        <div class="template-summary">
          <strong>{{ template.name }}</strong>
          <span>Status {{ template.status }}</span>
          <span>Version {{ template.version ?? 1 }}</span>
          <span>Usage {{ template.usageCount ?? 0 }} {{ (template.usageCount ?? 0) === 1 ? 'rule' : 'rules' }}</span>
          <span
            v-for="usageRule in template.usageRules ?? []"
            :key="`${template.approvalTemplateId}-${usageRule.ruleId}`"
          >
            Used by {{ usageRule.name }} ({{ usageRule.status }})
          </span>
          <span v-if="template.description">Description {{ template.description }}</span>
          <span v-if="template.createdAt">Created {{ template.createdAt }}</span>
          <div class="template-actions">
            <el-button
              v-if="template.status === 'enabled'"
              size="small"
              :aria-label="`Disable ${template.name}`"
              @click="disableTemplate(template)"
            >
              Disable
            </el-button>
            <el-button
              v-else
              size="small"
              :aria-label="`Enable ${template.name}`"
              @click="enableTemplate(template)"
            >
              Enable
            </el-button>
            <el-button
              size="small"
              :aria-label="`Edit ${template.name}`"
              @click="editTemplate(template)"
            >
              Edit
            </el-button>
            <el-button
              v-if="(template.version ?? 1) > 1"
              size="small"
              :loading="versionLoadingTemplateId === template.approvalTemplateId"
              :aria-label="`View versions ${template.name}`"
              @click="loadTemplateVersions(template)"
            >
              View versions
            </el-button>
            <el-button
              size="small"
              :loading="usageLoadingTemplateId === template.approvalTemplateId"
              :aria-label="`View usage ${template.name}`"
              @click="loadTemplateUsage(template, 1)"
            >
              View usage
            </el-button>
          </div>
        </div>
        <ol class="step-list">
          <li v-for="step in template.steps" :key="step.stepId">
            <strong>{{ step.approvalTitle }}</strong>
            <span>{{ step.stepId }}</span>
            <span>{{ step.assigneeRoles.join(', ') }}</span>
            <span>Mode {{ step.approvalMode || 'all' }}</span>
            <span v-if="step.slaHours">SLA {{ step.slaHours }}h</span>
            <small
              v-for="escalation in step.slaEscalations ?? []"
              :key="`${step.stepId}-${escalation.afterHours}-${escalation.role}`"
            >
              Escalate after {{ escalation.afterHours }}h to {{ escalation.role }}
            </small>
          </li>
        </ol>
        <section
          v-if="usagePanel?.templateId === template.approvalTemplateId"
          class="usage-panel"
          :aria-label="`Usage details for ${template.name}`"
        >
          <h4>Usage details for {{ template.name }}</h4>
          <span>Page {{ usagePanel.page }} of {{ usageTotalPages }}</span>
          <ul>
            <li v-for="rule in usagePanel.items" :key="String(rule.ruleId)">
              Referenced by {{ rule.name }} ({{ rule.status }})
            </li>
          </ul>
          <div class="usage-actions">
            <el-button
              size="small"
              :disabled="usagePanel.page <= 1"
              @click="loadTemplateUsage(template, usagePanel.page - 1)"
            >
              Previous usage page
            </el-button>
            <el-button
              size="small"
              :disabled="usagePanel.page >= usageTotalPages"
              @click="loadTemplateUsage(template, usagePanel.page + 1)"
            >
              Next usage page
            </el-button>
          </div>
        </section>
        <section
          v-if="versionHistory?.templateId === template.approvalTemplateId"
          class="version-panel"
          :aria-label="`Version history for ${template.name}`"
        >
          <h4>Version history for {{ template.name }}</h4>
          <div class="version-actions">
            <el-button
              v-if="versionHistory.items.length >= 2"
              size="small"
              :loading="versionDiffLoadingTemplateId === template.approvalTemplateId"
              :aria-label="`Compare version ${oldestVersion(versionHistory.items)} to ${newestVersion(versionHistory.items)} ${template.name}`"
              @click="compareTemplateVersions(template, oldestVersion(versionHistory.items), newestVersion(versionHistory.items))"
            >
              Compare {{ oldestVersion(versionHistory.items) }} to {{ newestVersion(versionHistory.items) }}
            </el-button>
            <el-button
              v-for="version in versionHistory.items"
              :key="`${template.approvalTemplateId}-${version.version}`"
              size="small"
              :aria-label="`Open version ${version.version ?? 1} ${template.name}`"
              @click="loadTemplateVersion(template, version.version ?? 1)"
            >
              Version {{ version.version ?? 1 }}
            </el-button>
            <el-button
              v-for="version in rollbackVersions(template, versionHistory.items)"
              :key="`${template.approvalTemplateId}-rollback-${version.version}`"
              size="small"
              :loading="rollbackLoadingTemplateId === template.approvalTemplateId"
              :aria-label="`Rollback version ${version.version ?? 1} ${template.name}`"
              @click="rollbackCandidate = { template, version: version.version ?? 1 }"
            >
              Rollback version {{ version.version ?? 1 }}
            </el-button>
          </div>
        </section>
        <section
          v-if="versionDiff?.templateId === template.approvalTemplateId"
          class="version-panel"
          :aria-label="`Version diff for ${template.name}`"
        >
          <h4>Version diff {{ versionDiff.baseVersion }} -> {{ versionDiff.targetVersion }} for {{ template.name }}</h4>
          <p>
            Added {{ versionDiff.summary.added }}, removed {{ versionDiff.summary.removed }},
            modified {{ versionDiff.summary.modified }}, unchanged {{ versionDiff.summary.unchanged }}
          </p>
          <ul>
            <li v-for="change in versionDiff.changes" :key="`${change.changeType}-${change.stepId}`">
              <strong>{{ changeLabel(change.changeType) }} {{ change.stepId }}</strong>
              <span v-if="change.baseStep">Base {{ change.baseStep.approvalTitle }}</span>
              <span v-if="change.targetStep">Target {{ change.targetStep.approvalTitle }}</span>
            </li>
          </ul>
        </section>
        <section
          v-if="versionSnapshot?.approvalTemplateId === template.approvalTemplateId"
          class="version-panel"
          :aria-label="`Version snapshot for ${template.name}`"
        >
          <h4>Version snapshot {{ versionSnapshot.version }} for {{ template.name }}</h4>
          <ol class="step-list compact">
            <li v-for="step in versionSnapshot.steps" :key="step.stepId">
              <strong>{{ step.approvalTitle }}</strong>
              <span>{{ step.stepId }}</span>
              <span>{{ step.assigneeRoles.join(', ') }}</span>
              <span>Mode {{ step.approvalMode || 'all' }}</span>
              <span v-if="step.slaHours">SLA {{ step.slaHours }}h</span>
            </li>
          </ol>
        </section>
      </article>
    </section>
  </section>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue';
import { ruleApi, type ApprovalTemplatePayload, type ApprovalTemplateStepPayload } from '../../api/ruleApi';

interface ApprovalTemplateResponse {
  approvalTemplateId: number | string;
  name: string;
  description?: string;
  status: string;
  version?: number;
  steps: ApprovalTemplateStepPayload[];
  usageCount?: number;
  usageRules?: Array<{
    ruleId: number | string;
    name: string;
    status: string;
  }>;
  createdAt?: string | null;
}

interface ApprovalTemplateUsageRule {
  ruleId: number | string;
  name: string;
  status: string;
}

interface ApprovalTemplateUsagePanel {
  templateId: number | string;
  templateName: string;
  items: ApprovalTemplateUsageRule[];
  page: number;
  pageSize: number;
  total: number;
}

interface ApprovalTemplateVersionHistory {
  templateId: number | string;
  templateName: string;
  items: ApprovalTemplateResponse[];
}

interface ApprovalTemplateVersionDiff {
  templateId: number | string;
  baseVersion: number;
  targetVersion: number;
  summary: {
    added: number;
    removed: number;
    modified: number;
    unchanged: number;
  };
  changes: Array<{
    changeType: 'added' | 'removed' | 'modified' | string;
    stepId: string;
    baseStep?: ApprovalTemplateStepPayload | null;
    targetStep?: ApprovalTemplateStepPayload | null;
  }>;
}

interface ApprovalTemplateRollbackCandidate {
  template: ApprovalTemplateResponse;
  version: number;
}

const form = reactive({
  name: '',
  description: '',
  status: 'enabled' as 'enabled' | 'disabled',
  stepsText: ''
});

const templates = ref<ApprovalTemplateResponse[]>([]);
const loadingTemplates = ref(false);
const submitting = ref(false);
const errorMessage = ref('');
const successMessage = ref('');
const disableCandidate = ref<ApprovalTemplateResponse | null>(null);
const editingTemplate = ref<ApprovalTemplateResponse | null>(null);
const usagePanel = ref<ApprovalTemplateUsagePanel | null>(null);
const versionHistory = ref<ApprovalTemplateVersionHistory | null>(null);
const versionDiff = ref<ApprovalTemplateVersionDiff | null>(null);
const versionSnapshot = ref<ApprovalTemplateResponse | null>(null);
const usageLoadingTemplateId = ref<number | string | null>(null);
const versionLoadingTemplateId = ref<number | string | null>(null);
const versionDiffLoadingTemplateId = ref<number | string | null>(null);
const rollbackLoadingTemplateId = ref<number | string | null>(null);
const rollbackCandidate = ref<ApprovalTemplateRollbackCandidate | null>(null);
const usageTotalPages = computed(() => {
  if (!usagePanel.value) {
    return 1;
  }
  return Math.max(1, Math.ceil(usagePanel.value.total / usagePanel.value.pageSize));
});

onMounted(() => {
  void loadApprovalTemplates();
});

async function loadApprovalTemplates() {
  loadingTemplates.value = true;
  errorMessage.value = '';
  try {
    const result = await ruleApi.listApprovalTemplates({
      page: 1,
      pageSize: 20
    }) as unknown as {
      items?: ApprovalTemplateResponse[];
      data?: { items?: ApprovalTemplateResponse[] };
    };
    templates.value = result.items ?? result.data?.items ?? [];
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : 'Failed to load approval templates';
  } finally {
    loadingTemplates.value = false;
  }
}

async function submitTemplate() {
  errorMessage.value = '';
  successMessage.value = '';
  if (!form.name.trim()) {
    errorMessage.value = 'Template name is required.';
    return;
  }
  const steps = parseSteps(form.stepsText);
  if (steps.length === 0) {
    errorMessage.value = 'At least one approval template step is required.';
    return;
  }
  submitting.value = true;
  try {
    const payload: ApprovalTemplatePayload = {
      name: form.name.trim(),
      description: form.description.trim() || undefined,
      status: form.status,
      steps
    };
    if (editingTemplate.value) {
      const updated = await ruleApi.updateApprovalTemplate(
        String(editingTemplate.value.approvalTemplateId),
        payload
      ) as unknown as ApprovalTemplateResponse;
      templates.value = templates.value.map((item) =>
        item.approvalTemplateId === updated.approvalTemplateId ? updated : item
      );
      successMessage.value = `Approval template updated to version ${updated.version ?? 1}: ${updated.name}`;
      cancelEditTemplate();
    } else {
      const created = await ruleApi.createApprovalTemplate(payload) as unknown as ApprovalTemplateResponse;
      successMessage.value = `Approval template created: ${created.name}`;
      resetForm();
      templates.value = [created, ...templates.value.filter((item) => item.approvalTemplateId !== created.approvalTemplateId)];
    }
    await loadApprovalTemplates();
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : 'Failed to save approval template';
  } finally {
    submitting.value = false;
  }
}

function editTemplate(template: ApprovalTemplateResponse) {
  editingTemplate.value = template;
  form.name = template.name;
  form.description = template.description ?? '';
  form.status = template.status === 'disabled' ? 'disabled' : 'enabled';
  form.stepsText = template.steps.map(stepToText).join('\n');
  successMessage.value = '';
  errorMessage.value = '';
}

function cancelEditTemplate() {
  editingTemplate.value = null;
  resetForm();
}

function resetForm() {
  form.name = '';
  form.description = '';
  form.status = 'enabled';
  form.stepsText = '';
}

async function disableTemplate(template: ApprovalTemplateResponse) {
  if ((template.usageCount ?? 0) > 0) {
    disableCandidate.value = template;
    return;
  }
  await changeTemplateStatus(template, 'disabled');
}

async function confirmDisableTemplate() {
  if (!disableCandidate.value) return;
  const template = disableCandidate.value;
  disableCandidate.value = null;
  await changeTemplateStatus(template, 'disabled');
}

async function enableTemplate(template: ApprovalTemplateResponse) {
  await changeTemplateStatus(template, 'enabled');
}

async function loadTemplateUsage(template: ApprovalTemplateResponse, page: number) {
  errorMessage.value = '';
  usageLoadingTemplateId.value = template.approvalTemplateId;
  try {
    const result = await ruleApi.listApprovalTemplateUsage(String(template.approvalTemplateId), {
      page,
      pageSize: 10
    }) as unknown as {
      items?: ApprovalTemplateUsageRule[];
      page?: number;
      pageSize?: number;
      total?: number;
      data?: {
        items?: ApprovalTemplateUsageRule[];
        page?: number;
        pageSize?: number;
        total?: number;
      };
    };
    const data = result.data ?? result;
    usagePanel.value = {
      templateId: template.approvalTemplateId,
      templateName: template.name,
      items: data.items ?? [],
      page: data.page ?? page,
      pageSize: data.pageSize ?? 10,
      total: data.total ?? 0
    };
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : 'Failed to load approval template usage';
  } finally {
    usageLoadingTemplateId.value = null;
  }
}

async function loadTemplateVersions(template: ApprovalTemplateResponse) {
  errorMessage.value = '';
  versionLoadingTemplateId.value = template.approvalTemplateId;
  try {
    const result = await ruleApi.listApprovalTemplateVersions(
      String(template.approvalTemplateId)
    ) as unknown as ApprovalTemplateResponse[] | { data?: ApprovalTemplateResponse[] };
    const items = Array.isArray(result) ? result : result.data ?? [];
    versionHistory.value = {
      templateId: template.approvalTemplateId,
      templateName: template.name,
      items
    };
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : 'Failed to load approval template versions';
  } finally {
    versionLoadingTemplateId.value = null;
  }
}

async function loadTemplateVersion(template: ApprovalTemplateResponse, version: number) {
  errorMessage.value = '';
  versionLoadingTemplateId.value = template.approvalTemplateId;
  try {
    const result = await ruleApi.getApprovalTemplateVersion(
      String(template.approvalTemplateId),
      version
    ) as unknown as ApprovalTemplateResponse;
    versionSnapshot.value = {
      ...result,
      approvalTemplateId: template.approvalTemplateId
    };
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : 'Failed to load approval template version';
  } finally {
    versionLoadingTemplateId.value = null;
  }
}

async function compareTemplateVersions(template: ApprovalTemplateResponse, baseVersion: number, targetVersion: number) {
  errorMessage.value = '';
  versionDiffLoadingTemplateId.value = template.approvalTemplateId;
  try {
    const result = await ruleApi.compareApprovalTemplateVersions(
      String(template.approvalTemplateId),
      { baseVersion, targetVersion }
    ) as unknown as ApprovalTemplateVersionDiff | { data?: ApprovalTemplateVersionDiff };
    const data = 'data' in result && result.data ? result.data : result as ApprovalTemplateVersionDiff;
    versionDiff.value = {
      ...data,
      templateId: template.approvalTemplateId
    };
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : 'Failed to compare approval template versions';
  } finally {
    versionDiffLoadingTemplateId.value = null;
  }
}

async function rollbackTemplateVersion(template: ApprovalTemplateResponse, version: number) {
  errorMessage.value = '';
  rollbackLoadingTemplateId.value = template.approvalTemplateId;
  try {
    const result = await ruleApi.rollbackApprovalTemplateVersion(
      String(template.approvalTemplateId),
      version
    ) as unknown as {
      sourceVersion?: number;
      newVersion?: number;
      data?: {
        sourceVersion?: number;
        newVersion?: number;
      };
    };
    const data = result.data ?? result;
    successMessage.value = `Approval template rolled back to version ${data.sourceVersion ?? version} as version ${data.newVersion ?? ''}`.trim();
    versionHistory.value = null;
    versionDiff.value = null;
    versionSnapshot.value = null;
    await loadApprovalTemplates();
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : 'Failed to rollback approval template version';
  } finally {
    rollbackLoadingTemplateId.value = null;
  }
}

async function confirmRollbackTemplateVersion() {
  if (!rollbackCandidate.value) return;
  const candidate = rollbackCandidate.value;
  rollbackCandidate.value = null;
  await rollbackTemplateVersion(candidate.template, candidate.version);
}

async function changeTemplateStatus(template: ApprovalTemplateResponse, status: 'enabled' | 'disabled') {
  errorMessage.value = '';
  successMessage.value = '';
  try {
    const updated = status === 'enabled'
      ? await ruleApi.enableApprovalTemplate(String(template.approvalTemplateId)) as unknown as ApprovalTemplateResponse
      : await ruleApi.disableApprovalTemplate(String(template.approvalTemplateId)) as unknown as ApprovalTemplateResponse;
    templates.value = templates.value.map((item) =>
      item.approvalTemplateId === updated.approvalTemplateId ? updated : item
    );
    successMessage.value = `Approval template ${status}: ${updated.name}`;
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : `Failed to ${status} approval template`;
  }
}

function parseSteps(value: string): ApprovalTemplateStepPayload[] {
  return value
    .split(/\r?\n/)
    .map((line) => line.trim())
    .filter((line) => line && !line.toLowerCase().startsWith('stepid|'))
    .map((line, index) => {
      const columns = line.split('|').map((item) => item.trim());
      const roles = splitValues(columns[2] ?? '');
      const slaHours = positiveInteger(columns[4] ?? '');
      return {
        stepId: columns[0] || `step${index + 1}`,
        approvalTitle: columns[1] || `Approval step ${index + 1}`,
        assigneeRoles: roles,
        approvalMode: columns[3] === 'any' ? 'any' : 'all',
        slaHours,
        slaEscalations: parseEscalations(columns[5] ?? '')
      };
    });
}

function parseEscalations(value: string) {
  return splitValues(value).map((item) => {
    const [afterHoursText, roleText] = item.split(':').map((part) => part.trim());
    return {
      afterHours: positiveInteger(afterHoursText) ?? 1,
      role: roleText
    };
  }).filter((item) => item.role);
}

function stepToText(step: ApprovalTemplateStepPayload) {
  const escalationText = (step.slaEscalations ?? [])
    .map((item) => `${item.afterHours}:${item.role}`)
    .join(',');
  return [
    step.stepId,
    step.approvalTitle,
    step.assigneeRoles.join(','),
    step.approvalMode || 'all',
    step.slaHours ?? '',
    escalationText
  ].join('|');
}

function newestVersion(items: ApprovalTemplateResponse[]) {
  return Math.max(...items.map((item) => item.version ?? 1));
}

function oldestVersion(items: ApprovalTemplateResponse[]) {
  return Math.min(...items.map((item) => item.version ?? 1));
}

function rollbackVersions(template: ApprovalTemplateResponse, items: ApprovalTemplateResponse[]) {
  const currentVersion = template.version ?? 1;
  return items.filter((item) => (item.version ?? 1) < currentVersion);
}

function changeLabel(changeType: string) {
  if (changeType === 'added') return 'Added';
  if (changeType === 'removed') return 'Removed';
  if (changeType === 'modified') return 'Modified';
  return changeType;
}

function splitValues(value: string) {
  return value
    .split(',')
    .map((item) => item.trim())
    .filter((item, index, items) => item && items.indexOf(item) === index);
}

function positiveInteger(value: string) {
  const parsed = Number.parseInt(value, 10);
  return Number.isFinite(parsed) && parsed > 0 ? parsed : undefined;
}
</script>

<style scoped>
.approval-template-page {
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

.template-form {
  display: grid;
  grid-template-columns: repeat(2, minmax(240px, 1fr));
  gap: 14px 16px;
  max-width: 880px;
}

.template-form label {
  display: flex;
  flex-direction: column;
  gap: 6px;
  color: #303133;
  font-weight: 600;
}

.template-form label span {
  font-size: 13px;
}

.template-form select {
  min-height: 32px;
  padding: 0 10px;
  border: 1px solid #dcdfe6;
  border-radius: 4px;
  background: #fff;
  color: #303133;
}

.steps-field,
.form-actions {
  grid-column: 1 / -1;
}

.template-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
  max-width: 880px;
}

.confirm-panel {
  display: grid;
  gap: 10px;
  max-width: 640px;
  padding: 14px 16px;
  border: 1px solid #f56c6c;
  border-radius: 8px;
  background: #fff7f7;
}

.confirm-panel h3,
.confirm-panel p {
  margin: 0;
}

.confirm-panel ul {
  margin: 0;
  padding-left: 20px;
}

.confirm-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.template-list h3 {
  margin: 0;
  font-size: 18px;
}

.template-card {
  display: grid;
  grid-template-columns: minmax(220px, 280px) minmax(0, 1fr);
  gap: 16px;
  padding: 14px 16px;
  background: #ffffff;
  border: 1px solid #dcdfe6;
  border-radius: 8px;
}

.usage-panel {
  grid-column: 1 / -1;
  display: grid;
  gap: 8px;
  padding: 12px;
  background: #f8fafc;
  border: 1px solid #dce7f3;
  border-radius: 6px;
}

.usage-panel h4 {
  margin: 0;
  font-size: 15px;
}

.usage-panel ul {
  margin: 0;
  padding-left: 20px;
}

.usage-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.version-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.template-summary,
.step-list li {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.template-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.template-summary span,
.step-list span,
.step-list small {
  color: #606266;
}

.step-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
  margin: 0;
  padding-left: 20px;
}

.step-list li {
  padding: 10px;
  background: #f5f7fa;
  border-radius: 6px;
}

@media (max-width: 720px) {
  .page-header,
  .template-card {
    display: flex;
    flex-direction: column;
  }

  .template-form {
    grid-template-columns: 1fr;
  }
}
</style>
