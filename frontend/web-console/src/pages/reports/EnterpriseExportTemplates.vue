<template>
  <section class="page enterprise-export-template-page">
    <header class="page-header">
      <div>
        <h2>企业导出模板</h2>
        <p>统一维护报告导出的企业品牌、版式和模板版本。</p>
      </div>
      <el-button :loading="loading" @click="loadTemplates">刷新</el-button>
    </header>

    <el-alert v-if="errorMessage" :title="errorMessage" type="error" show-icon :closable="false" />
    <el-alert v-if="successMessage" :title="successMessage" type="success" show-icon :closable="false" />

    <section class="template-form" aria-label="企业导出模板表单">
      <label>
        <span>模板编码</span>
        <el-input v-model="form.templateId" aria-label="模板编码" :disabled="Boolean(editingTemplate)" placeholder="board-standard" />
      </label>
      <label>
        <span>模板名称</span>
        <el-input v-model="form.name" aria-label="模板名称" placeholder="董事会标准导出模板" />
      </label>
      <label>
        <span>状态</span>
        <select v-model="form.status" aria-label="模板状态">
          <option value="active">启用</option>
          <option value="disabled">停用</option>
        </select>
      </label>
      <label>
        <span>企业名称</span>
        <el-input v-model="form.companyName" aria-label="企业名称" placeholder="集团财务中心" />
      </label>
      <label>
        <span>标识对象键</span>
        <el-input v-model="form.logoObjectKey" aria-label="标识对象键" placeholder="logos/company.svg" />
      </label>
      <label>
        <span>页眉</span>
        <el-input v-model="form.header" aria-label="页眉" placeholder="集团经营分析报告" />
      </label>
      <label>
        <span>页脚</span>
        <el-input v-model="form.footer" aria-label="页脚" placeholder="内部资料" />
      </label>
      <label>
        <span>字体</span>
        <el-input v-model="form.fontFamily" aria-label="字体" placeholder="Microsoft YaHei" />
      </label>
      <label>
        <span>主色</span>
        <el-input v-model="form.primaryColor" aria-label="主色" placeholder="#1F4E79" />
      </label>
      <label>
        <span>封面标题</span>
        <el-input v-model="form.coverTitle" aria-label="封面标题" placeholder="经营分析报告" />
      </label>
      <label>
        <span>目录标题</span>
        <el-input v-model="form.tocTitle" aria-label="目录标题" placeholder="目录" />
      </label>
      <label>
        <span>章节标题前缀</span>
        <el-input v-model="form.bodyTitlePrefix" aria-label="章节标题前缀" placeholder="章节" />
      </label>
      <div class="form-actions">
        <el-button type="primary" :loading="submitting" @click="submitTemplate">
          {{ editingTemplate ? '保存企业导出模板' : '创建企业导出模板' }}
        </el-button>
        <el-button v-if="editingTemplate" @click="cancelEdit">取消编辑</el-button>
      </div>
    </section>

    <section class="template-list" aria-label="企业导出模板列表">
      <h3>已有模板</h3>
      <el-empty v-if="!loading && templates.length === 0" description="暂无企业导出模板" />
      <article
        v-for="template in templates"
        :key="template.templateId"
        class="template-card"
      >
        <div class="template-summary">
          <strong>{{ template.name }}</strong>
          <span>模板编码 {{ template.templateId }}</span>
          <span>状态 {{ statusLabel(template.status) }}</span>
          <span>版本 {{ template.version }}</span>
          <span>企业 {{ template.brandSnapshot.companyName }}</span>
          <span>{{ template.brandSnapshot.header }}</span>
          <span>{{ template.brandSnapshot.footer }}</span>
          <span>{{ template.brandSnapshot.fontFamily }}</span>
          <span>{{ template.brandSnapshot.primaryColor }}</span>
          <div class="template-actions">
            <el-button
              v-if="template.status === 'active'"
              size="small"
              :aria-label="`停用 ${template.name}`"
              @click="disableTemplate(template)"
            >
              停用
            </el-button>
            <el-button
              v-else
              size="small"
              :aria-label="`启用 ${template.name}`"
              @click="enableTemplate(template)"
            >
              启用
            </el-button>
            <el-button
              size="small"
              :aria-label="`编辑 ${template.name}`"
              @click="editTemplate(template)"
            >
              编辑
            </el-button>
            <el-button
              size="small"
              :loading="versionLoadingTemplateId === template.templateId"
              :aria-label="`查看版本 ${template.name}`"
              @click="loadVersions(template)"
            >
              查看版本
            </el-button>
          </div>
        </div>
        <dl class="layout-grid">
          <div>
            <dt>封面</dt>
            <dd>{{ template.brandSnapshot.layout?.coverTitle || '-' }}</dd>
          </div>
          <div>
            <dt>目录</dt>
            <dd>{{ template.brandSnapshot.layout?.tocTitle || '-' }}</dd>
          </div>
          <div>
            <dt>章节前缀</dt>
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
          :aria-label="`${template.name} 的版本历史`"
        >
          <h4>{{ template.name }} 的版本历史</h4>
          <ol>
            <li v-for="version in versionHistory.items" :key="`${template.templateId}-${version.version}`">
              <strong>版本快照 {{ version.version }}</strong>
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
import { isLocalPreviewUnauthorizedError } from '../../api/client';

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
    if (isLocalPreviewUnauthorizedError(error)) {
      templates.value = [];
      return;
    }
    errorMessage.value = errorText(error, '企业导出模板加载失败');
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
      ? `企业导出模板已更新到版本 ${saved.version}：${saved.name}`
      : `企业导出模板已创建：${saved.name}`;
    editingTemplate.value = null;
    resetForm();
  } catch (error) {
    errorMessage.value = errorText(error, '企业导出模板保存失败');
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
      ? `企业导出模板已启用：${updated.name}`
      : `企业导出模板已停用：${updated.name}`;
  } catch (error) {
    errorMessage.value = errorText(error, '企业导出模板状态变更失败');
  }
}

async function loadVersions(template: EnterpriseExportTemplate) {
  versionLoadingTemplateId.value = template.templateId;
  errorMessage.value = '';
  try {
    const items = await reportApi.listEnterpriseExportTemplateVersions(template.templateId);
    versionHistory.value = { templateId: template.templateId, items };
  } catch (error) {
    errorMessage.value = errorText(error, '企业导出模板版本加载失败');
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

function statusLabel(status: string) {
  return status === 'active' ? '启用' : '停用';
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
