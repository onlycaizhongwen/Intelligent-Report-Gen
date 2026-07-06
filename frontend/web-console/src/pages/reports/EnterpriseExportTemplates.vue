<template>
  <section class="page enterprise-export-template-page">
    <header class="page-header">
      <div>
        <h2>Enterprise Export Templates</h2>
        <p>Manage governed brand and layout templates for report export.</p>
      </div>
      <el-button :loading="loading" @click="loadTemplates">Refresh</el-button>
    </header>

    <el-alert v-if="errorMessage" :title="errorMessage" type="error" show-icon :closable="false" />
    <el-alert v-if="successMessage" :title="successMessage" type="success" show-icon :closable="false" />

    <section class="template-form" aria-label="Enterprise export template form">
      <label>
        <span>Template ID</span>
        <el-input v-model="form.templateId" aria-label="Template ID" :disabled="Boolean(editingTemplate)" placeholder="board-standard" />
      </label>
      <label>
        <span>Name</span>
        <el-input v-model="form.name" aria-label="Template name" placeholder="Board standard export" />
      </label>
      <label>
        <span>Status</span>
        <select v-model="form.status" aria-label="Template status">
          <option value="active">active</option>
          <option value="disabled">disabled</option>
        </select>
      </label>
      <label>
        <span>Company name</span>
        <el-input v-model="form.companyName" aria-label="Company name" placeholder="Acme Finance" />
      </label>
      <label>
        <span>Logo object key</span>
        <el-input v-model="form.logoObjectKey" aria-label="Logo object key" placeholder="logos/acme.svg" />
      </label>
      <label>
        <span>Header</span>
        <el-input v-model="form.header" aria-label="Header" placeholder="Board Pack" />
      </label>
      <label>
        <span>Footer</span>
        <el-input v-model="form.footer" aria-label="Footer" placeholder="Confidential" />
      </label>
      <label>
        <span>Font family</span>
        <el-input v-model="form.fontFamily" aria-label="Font family" placeholder="Microsoft YaHei" />
      </label>
      <label>
        <span>Primary color</span>
        <el-input v-model="form.primaryColor" aria-label="Primary color" placeholder="#1F4E79" />
      </label>
      <label>
        <span>Cover title</span>
        <el-input v-model="form.coverTitle" aria-label="Cover title" placeholder="Board Strategy Pack" />
      </label>
      <label>
        <span>TOC title</span>
        <el-input v-model="form.tocTitle" aria-label="TOC title" placeholder="Report Outline" />
      </label>
      <label>
        <span>Section title prefix</span>
        <el-input v-model="form.bodyTitlePrefix" aria-label="Section title prefix" placeholder="Section" />
      </label>
      <div class="form-actions">
        <el-button type="primary" :loading="submitting" @click="submitTemplate">
          {{ editingTemplate ? 'Save enterprise export template changes' : 'Create enterprise export template' }}
        </el-button>
        <el-button v-if="editingTemplate" @click="cancelEdit">Cancel edit</el-button>
      </div>
    </section>

    <section class="template-list" aria-label="Enterprise export templates">
      <h3>Existing templates</h3>
      <el-empty v-if="!loading && templates.length === 0" description="No enterprise export templates" />
      <article
        v-for="template in templates"
        :key="template.templateId"
        class="template-card"
      >
        <div class="template-summary">
          <strong>{{ template.name }}</strong>
          <span>Template {{ template.templateId }}</span>
          <span>Status {{ template.status }}</span>
          <span>Version {{ template.version }}</span>
          <span>Company {{ template.brandSnapshot.companyName }}</span>
          <span>{{ template.brandSnapshot.header }}</span>
          <span>{{ template.brandSnapshot.footer }}</span>
          <span>{{ template.brandSnapshot.fontFamily }}</span>
          <span>{{ template.brandSnapshot.primaryColor }}</span>
          <div class="template-actions">
            <el-button
              v-if="template.status === 'active'"
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
              size="small"
              :loading="versionLoadingTemplateId === template.templateId"
              :aria-label="`View versions ${template.name}`"
              @click="loadVersions(template)"
            >
              View versions
            </el-button>
          </div>
        </div>
        <dl class="layout-grid">
          <div>
            <dt>Cover</dt>
            <dd>{{ template.brandSnapshot.layout?.coverTitle || '-' }}</dd>
          </div>
          <div>
            <dt>TOC</dt>
            <dd>{{ template.brandSnapshot.layout?.tocTitle || '-' }}</dd>
          </div>
          <div>
            <dt>Section prefix</dt>
            <dd>{{ template.brandSnapshot.layout?.bodyTitlePrefix || '-' }}</dd>
          </div>
          <div>
            <dt>Logo</dt>
            <dd>{{ template.brandSnapshot.logoObjectKey }}</dd>
          </div>
        </dl>
        <section
          v-if="versionHistory?.templateId === template.templateId"
          class="version-panel"
          :aria-label="`Version history for ${template.name}`"
        >
          <h4>Version history for {{ template.name }}</h4>
          <ol>
            <li v-for="version in versionHistory.items" :key="`${template.templateId}-${version.version}`">
              <strong>Version snapshot {{ version.version }}</strong>
              <span>{{ version.name }}</span>
              <span>{{ version.brandSnapshot.footer }}</span>
              <span>{{ version.brandSnapshot.layout?.coverTitle || '-' }}</span>
            </li>
          </ol>
        </section>
      </article>
    </section>
  </section>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue';
import {
  reportApi,
  type EnterpriseBrandTemplate,
  type EnterpriseExportTemplate,
  type EnterpriseExportTemplateRequest
} from '../../api/reportApi';

interface TemplateForm {
  templateId: string;
  name: string;
  status: 'active' | 'disabled';
  companyName: string;
  logoObjectKey: string;
  header: string;
  footer: string;
  fontFamily: string;
  primaryColor: string;
  coverTitle: string;
  tocTitle: string;
  bodyTitlePrefix: string;
}

const emptyForm = (): TemplateForm => ({
  templateId: '',
  name: '',
  status: 'active',
  companyName: '',
  logoObjectKey: '',
  header: '',
  footer: '',
  fontFamily: '',
  primaryColor: '',
  coverTitle: '',
  tocTitle: '',
  bodyTitlePrefix: ''
});

const templates = ref<EnterpriseExportTemplate[]>([]);
const loading = ref(false);
const submitting = ref(false);
const versionLoadingTemplateId = ref('');
const errorMessage = ref('');
const successMessage = ref('');
const editingTemplate = ref<EnterpriseExportTemplate | null>(null);
const versionHistory = ref<{ templateId: string; items: EnterpriseExportTemplate[] } | null>(null);
const form = reactive<TemplateForm>(emptyForm());

onMounted(() => {
  void loadTemplates();
});

async function loadTemplates() {
  loading.value = true;
  errorMessage.value = '';
  try {
    const result = await reportApi.listEnterpriseExportTemplates({ page: 1, pageSize: 20 });
    templates.value = result.items ?? [];
  } catch (error) {
    errorMessage.value = errorText(error, 'Failed to load enterprise export templates');
  } finally {
    loading.value = false;
  }
}

async function submitTemplate() {
  submitting.value = true;
  errorMessage.value = '';
  successMessage.value = '';
  try {
    const payload = formPayload();
    const saved = editingTemplate.value
      ? await reportApi.updateEnterpriseExportTemplate(editingTemplate.value.templateId, payload)
      : await reportApi.createEnterpriseExportTemplate(payload);
    upsertTemplate(saved);
    successMessage.value = editingTemplate.value
      ? `Enterprise export template updated to version ${saved.version}: ${saved.name}`
      : `Enterprise export template created: ${saved.name}`;
    editingTemplate.value = null;
    resetForm();
  } catch (error) {
    errorMessage.value = errorText(error, 'Failed to save enterprise export template');
  } finally {
    submitting.value = false;
  }
}

async function disableTemplate(template: EnterpriseExportTemplate) {
  await changeStatus(template, 'disabled');
}

async function enableTemplate(template: EnterpriseExportTemplate) {
  await changeStatus(template, 'active');
}

async function changeStatus(template: EnterpriseExportTemplate, status: 'active' | 'disabled') {
  errorMessage.value = '';
  successMessage.value = '';
  try {
    const updated = status === 'active'
      ? await reportApi.enableEnterpriseExportTemplate(template.templateId)
      : await reportApi.disableEnterpriseExportTemplate(template.templateId);
    upsertTemplate(updated);
    successMessage.value = status === 'active'
      ? `Enterprise export template enabled: ${updated.name}`
      : `Enterprise export template disabled: ${updated.name}`;
  } catch (error) {
    errorMessage.value = errorText(error, 'Failed to change enterprise export template status');
  }
}

async function loadVersions(template: EnterpriseExportTemplate) {
  versionLoadingTemplateId.value = template.templateId;
  errorMessage.value = '';
  try {
    const items = await reportApi.listEnterpriseExportTemplateVersions(template.templateId);
    versionHistory.value = { templateId: template.templateId, items };
  } catch (error) {
    errorMessage.value = errorText(error, 'Failed to load enterprise export template versions');
  } finally {
    versionLoadingTemplateId.value = '';
  }
}

function editTemplate(template: EnterpriseExportTemplate) {
  editingTemplate.value = template;
  versionHistory.value = null;
  Object.assign(form, templateToForm(template));
}

function cancelEdit() {
  editingTemplate.value = null;
  resetForm();
}

function formPayload(): EnterpriseExportTemplateRequest {
  return {
    templateId: form.templateId.trim(),
    name: form.name.trim(),
    status: form.status,
    brand: brandPayload()
  };
}

function brandPayload(): EnterpriseBrandTemplate {
  return {
    companyName: form.companyName.trim(),
    logoObjectKey: form.logoObjectKey.trim(),
    header: form.header.trim(),
    footer: form.footer.trim(),
    fontFamily: form.fontFamily.trim(),
    primaryColor: form.primaryColor.trim(),
    layout: {
      coverTitle: form.coverTitle.trim(),
      tocTitle: form.tocTitle.trim(),
      bodyTitlePrefix: form.bodyTitlePrefix.trim()
    }
  };
}

function templateToForm(template: EnterpriseExportTemplate): TemplateForm {
  return {
    templateId: template.templateId,
    name: template.name,
    status: template.status === 'disabled' ? 'disabled' : 'active',
    companyName: template.brandSnapshot.companyName,
    logoObjectKey: template.brandSnapshot.logoObjectKey,
    header: template.brandSnapshot.header,
    footer: template.brandSnapshot.footer,
    fontFamily: template.brandSnapshot.fontFamily,
    primaryColor: template.brandSnapshot.primaryColor,
    coverTitle: template.brandSnapshot.layout?.coverTitle ?? '',
    tocTitle: template.brandSnapshot.layout?.tocTitle ?? '',
    bodyTitlePrefix: template.brandSnapshot.layout?.bodyTitlePrefix ?? ''
  };
}

function resetForm() {
  Object.assign(form, emptyForm());
}

function upsertTemplate(template: EnterpriseExportTemplate) {
  templates.value = [
    template,
    ...templates.value.filter((item) => item.templateId !== template.templateId)
  ];
}

function errorText(error: unknown, fallback: string) {
  return error instanceof Error ? error.message : fallback;
}
</script>

<style scoped>
.enterprise-export-template-page {
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
  grid-template-columns: repeat(3, minmax(180px, 1fr));
  gap: 14px 16px;
  max-width: 1080px;
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

.form-actions {
  grid-column: 1 / -1;
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.template-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
  max-width: 1080px;
}

.template-list h3 {
  margin: 0;
  font-size: 18px;
}

.template-card {
  display: grid;
  grid-template-columns: minmax(240px, 320px) minmax(0, 1fr);
  gap: 16px;
  padding: 14px 16px;
  background: #ffffff;
  border: 1px solid #dcdfe6;
  border-radius: 8px;
}

.template-summary {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.template-summary span,
.layout-grid dd,
.version-panel span {
  color: #606266;
}

.template-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 4px;
}

.layout-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(160px, 1fr));
  gap: 10px;
  margin: 0;
}

.layout-grid div {
  padding: 10px;
  background: #f5f7fa;
  border-radius: 6px;
}

.layout-grid dt {
  margin-bottom: 4px;
  font-weight: 700;
}

.layout-grid dd {
  margin: 0;
  overflow-wrap: anywhere;
}

.version-panel {
  grid-column: 1 / -1;
  display: grid;
  gap: 8px;
  padding: 12px;
  background: #f8fafc;
  border: 1px solid #dce7f3;
  border-radius: 6px;
}

.version-panel h4 {
  margin: 0;
  font-size: 15px;
}

.version-panel ol {
  display: grid;
  gap: 8px;
  margin: 0;
  padding-left: 20px;
}

.version-panel li {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

@media (max-width: 860px) {
  .page-header,
  .template-card {
    display: flex;
    flex-direction: column;
  }

  .template-form,
  .layout-grid {
    grid-template-columns: 1fr;
  }
}
</style>
