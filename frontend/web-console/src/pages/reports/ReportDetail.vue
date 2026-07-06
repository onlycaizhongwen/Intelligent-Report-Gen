<template>
  <section class="page report-detail-page">
    <header class="toolbar">
      <div>
        <h2>报告详情</h2>
        <p v-if="reportTitle" class="muted">{{ reportTitle }}</p>
      </div>
      <el-button type="primary" :loading="exporting" @click="createEnterpriseExport">导出文件</el-button>
    </header>

    <el-alert v-if="message" :title="message" :type="messageType" show-icon :closable="false" />
    <p v-if="latestDownloadUrl" class="download-result">
      <a :href="latestDownloadUrl" target="_blank" rel="noopener">下载导出文件</a>
    </p>

    <section class="export-panel">
      <div class="format-row" aria-label="导出格式">
        <button
          v-for="option in exportOptions"
          :key="option.value"
          type="button"
          :class="{ active: form.format === option.value, disabled: !option.enabled }"
          :disabled="!option.enabled"
          @click="form.format = option.value"
        >
          <span>{{ option.label }}</span>
          <small>{{ option.enabled ? option.description : '暂未支持' }}</small>
        </button>
      </div>

      <el-form label-position="top" class="brand-form">
        <el-form-item label="已治理企业模板">
          <select v-model="selectedManagedTemplateId" aria-label="已治理企业模板" @change="applyManagedTemplate">
            <option value="">手工填写模板</option>
            <option
              v-for="template in managedTemplates"
              :key="template.templateId"
              :value="template.templateId"
            >
              {{ template.name }} {{ template.version }}
            </option>
          </select>
        </el-form-item>
        <el-form-item label="模板 ID">
          <el-input v-model="form.templateId" aria-label="模板 ID" />
        </el-form-item>
        <el-form-item label="企业名称">
          <el-input v-model="form.brand.companyName" aria-label="企业名称" />
        </el-form-item>
        <el-form-item label="Logo 对象键">
          <el-input v-model="form.brand.logoObjectKey" aria-label="Logo 对象键" />
        </el-form-item>
        <el-form-item label="页眉">
          <el-input v-model="form.brand.header" aria-label="页眉" />
        </el-form-item>
        <el-form-item label="页脚">
          <el-input v-model="form.brand.footer" aria-label="页脚" />
        </el-form-item>
        <el-form-item label="字体">
          <el-input v-model="form.brand.fontFamily" aria-label="字体" />
        </el-form-item>
        <el-form-item label="主色">
          <el-input v-model="form.brand.primaryColor" aria-label="主色" />
        </el-form-item>
        <el-form-item label="封面标题">
          <el-input v-model="form.brand.layout.coverTitle" aria-label="封面标题" />
        </el-form-item>
        <el-form-item label="目录标题">
          <el-input v-model="form.brand.layout.tocTitle" aria-label="目录标题" />
        </el-form-item>
        <el-form-item label="章节标题前缀">
          <el-input v-model="form.brand.layout.bodyTitlePrefix" aria-label="章节标题前缀" />
        </el-form-item>
        <el-form-item label="标题字号">
          <el-input v-model.number="form.brand.layout.titleFontSize" aria-label="标题字号" />
        </el-form-item>
        <el-form-item label="正文字号">
          <el-input v-model.number="form.brand.layout.bodyFontSize" aria-label="正文字号" />
        </el-form-item>
        <el-form-item label="页眉字号">
          <el-input v-model.number="form.brand.layout.headerFontSize" aria-label="页眉字号" />
        </el-form-item>
        <el-form-item label="页脚字号">
          <el-input v-model.number="form.brand.layout.footerFontSize" aria-label="页脚字号" />
        </el-form-item>
      </el-form>
      <div v-if="selectedManagedTemplate" class="managed-template-preview" aria-label="已治理企业模板快照">
        <strong>{{ selectedManagedTemplate.name }}</strong>
        <span>Version {{ selectedManagedTemplate.version }}</span>
        <span>{{ selectedManagedTemplate.brandSnapshot.header }}</span>
        <span>{{ selectedManagedTemplate.brandSnapshot.footer }}</span>
        <span>{{ selectedManagedTemplate.brandSnapshot.layout?.coverTitle }}</span>
      </div>
    </section>

    <el-row :gutter="16">
      <el-col :span="16">
        <el-card shadow="never">
          <template #header>正文内容</template>
          <div v-if="sections.length" class="section-list">
            <article
              v-for="section in sections"
              :key="section.sectionId ?? section.heading"
              :data-section-id="String(section.sectionId ?? section.heading)"
            >
              <h3>{{ section.heading }}</h3>
              <p data-section-content @mouseup="captureSelection">{{ section.content }}</p>
              <div v-if="section.citations?.length" class="citation-row" aria-label="引用标记">
                <button
                  v-for="citation in section.citations"
                  :key="String(citation.referenceId)"
                  type="button"
                  class="citation-button"
                  @click="loadReference(citation.referenceId)"
                >
                  引用 {{ citation.referenceId }}
                </button>
              </div>
            </article>
          </div>
          <el-empty v-else description="暂无正文内容" />
        </el-card>
      </el-col>
      <el-col :span="8">
        <el-card shadow="never">
          <template #header>引用来源</template>
          <div v-if="selectedReference" class="reference-detail">
            <h3>{{ selectedReference.sourceTitle ?? selectedReference.title ?? `引用 ${selectedReference.referenceId}` }}</h3>
            <el-tag v-if="selectedReference.sourceType" size="small">{{ selectedReference.sourceType }}</el-tag>
            <p>{{ selectedReference.snapshot ?? selectedReference.snippet }}</p>
            <div v-if="referenceScores.length" class="score-list">
              <span v-for="score in referenceScores" :key="score.label">{{ score.label }} {{ score.value }}%</span>
            </div>
            <p v-if="selectedReference.anchor?.text" class="anchor-text">{{ selectedReference.anchor.text }}</p>
          </div>
          <div v-else class="muted">
            点击正文引用标记可查看来源快照、可信度和引用质量评分。
          </div>
        </el-card>
        <el-card shadow="never" class="collaboration-panel">
          <template #header>批注协作</template>
          <p v-if="selectedAnchor.selectedText" class="selected-text">已选中：{{ selectedAnchor.selectedText }}</p>
          <p v-else class="muted">选中正文片段后可创建批注任务。</p>
          <el-form label-position="top" class="comment-form">
            <el-form-item label="批注意见">
              <el-input
                v-model="commentForm.content"
                type="textarea"
                :rows="3"
                aria-label="批注意见"
                placeholder="请输入需要协作处理的问题"
              />
            </el-form-item>
            <el-form-item label="指派用户 ID">
              <el-input v-model="commentForm.assigneeUserId" aria-label="指派用户 ID" placeholder="例如 1001" />
            </el-form-item>
            <el-button type="primary" :loading="submittingComment" :disabled="!canSubmitComment" @click="submitAnnotation">
              提交批注任务
            </el-button>
          </el-form>
        </el-card>
        <el-card shadow="never" class="share-panel">
          <template #header>分享链接管理</template>
          <el-form label-position="top" class="share-form">
            <el-form-item label="分享密码">
              <el-input
                v-model="shareForm.password"
                type="password"
                show-password
                aria-label="分享密码"
                placeholder="外部访问密码"
              />
            </el-form-item>
            <el-form-item label="分享有效期">
              <el-input v-model="shareForm.expiresAt" type="datetime-local" aria-label="分享有效期" />
            </el-form-item>
            <el-form-item label="最大访问次数">
              <el-input-number
                v-model="shareForm.maxAccessCount"
                :min="0"
                :precision="0"
                aria-label="最大访问次数"
                data-testid="share-max-access-count"
                controls-position="right"
              />
            </el-form-item>
            <el-form-item label="允许访问邮箱">
              <el-input
                v-model="shareForm.allowedVisitors"
                aria-label="允许访问邮箱"
                placeholder="external@example.com, reviewer@partner.com"
              />
            </el-form-item>
            <el-form-item label="允许访问域名">
              <el-input
                v-model="shareForm.allowedVisitorDomains"
                aria-label="允许访问域名"
                placeholder="example.com, partner.com"
              />
            </el-form-item>
            <el-form-item>
              <el-checkbox v-model="shareForm.allowDownload" data-testid="share-allow-download">允许外部下载</el-checkbox>
            </el-form-item>
            <el-form-item>
              <el-checkbox v-model="shareForm.singleUse" data-testid="share-single-use">单次访问链接</el-checkbox>
            </el-form-item>
            <el-form-item v-if="shareForm.allowDownload" label="允许下载格式">
              <el-checkbox-group v-model="shareForm.allowedDownloadFormats" aria-label="允许下载格式">
                <el-checkbox label="markdown" aria-label="允许下载格式 Markdown">Markdown</el-checkbox>
                <el-checkbox label="pdf" aria-label="允许下载格式 PDF">PDF</el-checkbox>
                <el-checkbox label="docx" aria-label="允许下载格式 Word">Word</el-checkbox>
                <el-checkbox label="pptx" aria-label="允许下载格式 PPT">PPT</el-checkbox>
              </el-checkbox-group>
            </el-form-item>
            <el-button type="primary" :loading="creatingShare" @click="createShareLink">
              创建分享链接
            </el-button>
          </el-form>
          <div v-if="activeShare" class="share-result">
            <p>状态：{{ activeShare.status }}</p>
            <p class="share-url">{{ absoluteShareUrl(activeShare.shareUrl) }}</p>
            <div class="share-actions">
              <el-button :disabled="!activeShare.shareUrl" @click="copyShareLink">复制分享链接</el-button>
              <el-button
                type="danger"
                :loading="revokingShare"
                :disabled="activeShare.status === 'revoked'"
                @click="revokeShareLink"
              >
                撤销分享链接
              </el-button>
            </div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <el-card shadow="never" class="version-panel">
      <template #header>
        <div class="panel-header">
          <span>版本管理</span>
          <el-button :loading="loadingVersions" @click="loadVersions">刷新版本</el-button>
        </div>
      </template>

      <div class="version-controls">
        <label>
          基准版本
          <select v-model="baseVersionId" aria-label="基准版本">
            <option v-for="version in versions" :key="version.versionId" :value="String(version.versionId)">
              v{{ version.versionNo }} {{ version.changeReason }}
            </option>
          </select>
        </label>
        <label>
          目标版本
          <select v-model="targetVersionId" aria-label="目标版本">
            <option v-for="version in versions" :key="version.versionId" :value="String(version.versionId)">
              v{{ version.versionNo }} {{ version.changeReason }}{{ version.current ? '（当前）' : '' }}
            </option>
          </select>
        </label>
        <el-button :disabled="!canCompare" :loading="loadingDiff" @click="compareSelectedVersions">查看差异</el-button>
      </div>

      <el-table :data="versions" class="version-table">
        <el-table-column prop="versionNo" label="版本" width="90" />
        <el-table-column prop="changeReason" label="变更原因" />
        <el-table-column label="当前" width="90">
          <template #default="{ row }">{{ row.current ? '是' : '否' }}</template>
        </el-table-column>
        <el-table-column label="操作" width="130">
          <template #default="{ row }">
            <el-button
              :aria-label="`回滚版本 v${row.versionNo}`"
              :disabled="row.current"
              :loading="rollingBackVersionId === String(row.versionId)"
              @click="rollback(row.versionId)"
            >
              回滚
            </el-button>
          </template>
        </el-table-column>
      </el-table>

      <div v-if="versionDiff" class="diff-panel">
        <h3>版本差异</h3>
        <p class="muted">
          新增 {{ versionDiff.summary.added }}，删除 {{ versionDiff.summary.removed }}，修改 {{ versionDiff.summary.modified }}，未变 {{ versionDiff.summary.unchanged }}
        </p>
        <ul>
          <li v-for="change in versionDiff.changes" :key="`${change.changeType}-${change.heading}`">
            <strong>{{ changeLabel(change.changeType) }}：{{ change.heading }}</strong>
            <p v-if="change.baseContent">原文：{{ change.baseContent }}</p>
            <p v-if="change.targetContent">新版：{{ change.targetContent }}</p>
          </li>
        </ul>
      </div>
    </el-card>
  </section>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue';
import { useRoute } from 'vue-router';
import { collaborationApi } from '../../api/collaborationApi';
import {
  reportApi,
  type CitationMark,
  type EnterpriseExportTemplate,
  type ReportVersion,
  type ReportVersionDiff
} from '../../api/reportApi';
import { shareApi, type ShareLinkSummary } from '../../api/shareApi';
import type { ReferenceSummary } from '../../api/types';

// OpenSpec: report-citation-export-version / REQ-REPORT-003/004/005; permission-collaboration / REQ-COLLAB-002
const route = useRoute();
const reportId = computed(() => String(route.params.id));
const exporting = ref(false);
const loadingVersions = ref(false);
const loadingDiff = ref(false);
const rollingBackVersionId = ref('');
const submittingComment = ref(false);
const creatingShare = ref(false);
const revokingShare = ref(false);
const message = ref('');
const messageType = ref<'success' | 'error'>('success');
const latestDownloadUrl = ref('');
const reportTitle = ref('');
const sections = ref<Array<{ sectionId?: string | number; heading: string; content: string; citations?: CitationMark[] }>>([]);
const selectedReference = ref<ReferenceSummary | null>(null);
const activeShare = ref<ShareLinkSummary | null>(null);
const versions = ref<ReportVersion[]>([]);
const versionDiff = ref<ReportVersionDiff | null>(null);
const managedTemplates = ref<EnterpriseExportTemplate[]>([]);
const selectedManagedTemplateId = ref('');
const baseVersionId = ref('');
const targetVersionId = ref('');
const selectedAnchor = reactive({
  sectionId: '',
  startOffset: 0,
  endOffset: 0,
  selectedText: ''
});

const exportOptions = [
  { label: 'Word', value: 'docx', enabled: true, description: '企业模板' },
  { label: 'Markdown', value: 'markdown', enabled: true, description: '轻量交付' },
  { label: 'PDF', value: 'pdf', enabled: true, description: '便携归档' },
  { label: 'PPT', value: 'pptx', enabled: true, description: '演示汇报' }
];

const form = reactive({
  format: 'docx',
  templateId: 'enterprise-board',
  brand: {
    companyName: 'Contoso Analytics',
    logoObjectKey: 'branding/contoso-logo.png',
    header: 'Confidential Board Report',
    footer: 'Generated by Intelligent Report System',
    fontFamily: 'Aptos',
    primaryColor: '#1F4E79',
    layout: {
      coverTitle: '',
      tocTitle: 'Table of Contents',
      bodyTitlePrefix: '',
      titleFontSize: 32,
      bodyFontSize: 18,
      headerFontSize: 14,
      footerFontSize: 12
    }
  }
});
const commentForm = reactive({
  content: '',
  assigneeUserId: ''
});
const shareForm = reactive({
  password: '',
  expiresAt: '',
  allowDownload: false,
  allowedDownloadFormats: [] as string[],
  maxAccessCount: 0,
  allowedVisitors: '',
  allowedVisitorDomains: '',
  singleUse: false
});

const canCompare = computed(() => baseVersionId.value && targetVersionId.value && baseVersionId.value !== targetVersionId.value);
const selectedManagedTemplate = computed(() =>
  managedTemplates.value.find((template) => template.templateId === selectedManagedTemplateId.value) ?? null
);
const canSubmitComment = computed(() =>
  Boolean(selectedAnchor.selectedText && commentForm.content.trim() && !submittingComment.value)
);
const referenceScores = computed(() => {
  const score = selectedReference.value?.score;
  if (!score || typeof score === 'number') {
    return typeof score === 'number' ? [{ label: '可信度', value: Math.round(score * 100) }] : [];
  }
  const items: Array<{ label: string; value: number }> = [];
  if (typeof score.credibility === 'number') items.push({ label: '可信度', value: Math.round(score.credibility * 100) });
  if (typeof score.citationQuality === 'number') items.push({ label: '引用质量', value: Math.round(score.citationQuality * 100) });
  return items;
});

onMounted(async () => {
  await Promise.all([loadReport(), loadVersions(), loadManagedTemplates()]);
});

async function loadReport() {
  try {
    const report = await reportApi.getDetail(reportId.value);
    reportTitle.value = report.title;
    sections.value = report.sections ?? [];
  } catch {
    sections.value = [];
  }
}

async function loadReference(referenceId: string | number) {
  try {
    selectedReference.value = await reportApi.getReference(reportId.value, String(referenceId));
  } catch (error) {
    showError(error, '引用来源加载失败');
  }
}

async function loadVersions() {
  loadingVersions.value = true;
  try {
    versions.value = await reportApi.listVersions(reportId.value);
    if (versions.value.length >= 2) {
      targetVersionId.value = String(versions.value[0].versionId);
      baseVersionId.value = String(versions.value[1].versionId);
    }
  } finally {
    loadingVersions.value = false;
  }
}

async function loadManagedTemplates() {
  try {
    const result = await reportApi.listEnterpriseExportTemplates({ page: 1, pageSize: 20, status: 'active' });
    managedTemplates.value = result.items ?? [];
  } catch {
    managedTemplates.value = [];
  }
}

function applyManagedTemplate() {
  const template = selectedManagedTemplate.value;
  if (!template) return;
  form.templateId = template.templateId;
  Object.assign(form.brand, {
    companyName: template.brandSnapshot.companyName,
    logoObjectKey: template.brandSnapshot.logoObjectKey,
    header: template.brandSnapshot.header,
    footer: template.brandSnapshot.footer,
    fontFamily: template.brandSnapshot.fontFamily,
    primaryColor: template.brandSnapshot.primaryColor,
    layout: {
      ...form.brand.layout,
      ...(template.brandSnapshot.layout ?? {})
    }
  });
}

async function compareSelectedVersions() {
  if (!canCompare.value) return;
  loadingDiff.value = true;
  try {
    versionDiff.value = await reportApi.compareVersions(reportId.value, {
      baseVersionId: baseVersionId.value,
      targetVersionId: targetVersionId.value
    });
  } catch (error) {
    showError(error, '版本差异加载失败');
  } finally {
    loadingDiff.value = false;
  }
}

async function rollback(versionId: string | number) {
  rollingBackVersionId.value = String(versionId);
  try {
    const result = await reportApi.rollbackVersion(reportId.value, versionId);
    showSuccess(`已回滚并生成新版本：${result.newVersionId}`);
    await Promise.all([loadReport(), loadVersions()]);
  } catch (error) {
    showError(error, '版本回滚失败');
  } finally {
    rollingBackVersionId.value = '';
  }
}

async function createEnterpriseExport() {
  exporting.value = true;
  message.value = '';
  latestDownloadUrl.value = '';
  try {
    const needsEnterpriseBrand = ['docx', 'pdf', 'pptx'].includes(form.format);
    const result = await reportApi.createExport(reportId.value, {
      format: form.format,
      templateId: form.templateId,
      brand: needsEnterpriseBrand && !selectedManagedTemplate.value ? { ...form.brand } : undefined
    });
    if (result.exportFileId) {
      const download = await reportApi.getExportDownloadUrl(String(result.exportFileId));
      if (download.downloadUrl) {
        latestDownloadUrl.value = download.downloadUrl;
      }
    }
    showSuccess(`导出已完成：${result.fileName ?? result.exportFileId}`);
  } catch (error) {
    showError(error, '导出失败，请检查企业模板配置');
  } finally {
    exporting.value = false;
  }
}

function captureSelection(event: MouseEvent) {
  const sectionElement = (event.currentTarget as HTMLElement | null)?.closest<HTMLElement>('[data-section-id]');
  const contentElement = event.currentTarget as HTMLElement | null;
  const selection = window.getSelection();
  const selectedText = selection?.toString().trim() ?? '';
  const contentText = contentElement?.textContent ?? '';
  if (!sectionElement || !contentElement || !selection || !selectedText || selection.rangeCount === 0) {
    clearSelectedAnchor();
    return;
  }

  const startOffset = contentText.indexOf(selectedText);
  if (startOffset < 0) {
    clearSelectedAnchor();
    return;
  }

  selectedAnchor.sectionId = sectionElement.dataset.sectionId ?? '';
  selectedAnchor.startOffset = startOffset;
  selectedAnchor.endOffset = startOffset + selectedText.length;
  selectedAnchor.selectedText = selectedText;
}

async function submitAnnotation() {
  if (!canSubmitComment.value) return;
  submittingComment.value = true;
  try {
    const result = await collaborationApi.createComment(reportId.value, {
      content: commentForm.content.trim(),
      assigneeUserId: assigneeUserIdPayload(),
      anchor: {
        sectionId: selectedAnchor.sectionId,
        startOffset: selectedAnchor.startOffset,
        endOffset: selectedAnchor.endOffset,
        selectedText: selectedAnchor.selectedText
      }
    });
    showSuccess(`批注已提交，任务：${result.taskId}`);
    commentForm.content = '';
    commentForm.assigneeUserId = '';
  } catch (error) {
    showError(error, '批注提交失败');
  } finally {
    submittingComment.value = false;
  }
}

async function createShareLink() {
  creatingShare.value = true;
  try {
    activeShare.value = await shareApi.createShareLink(reportId.value, {
      password: shareForm.password.trim() || undefined,
      allowDownload: shareForm.allowDownload,
      allowedDownloadFormats: shareForm.allowDownload ? shareForm.allowedDownloadFormats : [],
      maxAccessCount: shareForm.maxAccessCount > 0 ? shareForm.maxAccessCount : undefined,
      allowedVisitors: splitScopeValues(shareForm.allowedVisitors),
      allowedVisitorDomains: splitScopeValues(shareForm.allowedVisitorDomains),
      singleUse: shareForm.singleUse,
      expiresAt: shareExpiresAtPayload()
    });
    showSuccess('分享链接已创建');
  } catch (error) {
    showError(error, '分享链接创建失败');
  } finally {
    creatingShare.value = false;
  }
}

async function copyShareLink() {
  if (!activeShare.value?.shareUrl) return;
  await navigator.clipboard.writeText(absoluteShareUrl(activeShare.value.shareUrl));
  showSuccess('分享链接已复制');
}

async function revokeShareLink() {
  if (!activeShare.value?.shareToken) return;
  revokingShare.value = true;
  try {
    activeShare.value = await shareApi.revokeShareLink(activeShare.value.shareToken);
    showSuccess('分享链接已撤销');
  } catch (error) {
    showError(error, '分享链接撤销失败');
  } finally {
    revokingShare.value = false;
  }
}

function shareExpiresAtPayload() {
  if (!shareForm.expiresAt) return undefined;
  return new Date(shareForm.expiresAt).toISOString();
}

function splitScopeValues(value: string) {
  return value
    .split(/[\n,;]/)
    .map((item) => item.trim().toLowerCase())
    .filter(Boolean);
}

function absoluteShareUrl(shareUrl: string) {
  if (/^https?:\/\//.test(shareUrl)) return shareUrl;
  return `${window.location.origin}${shareUrl.startsWith('/') ? shareUrl : `/${shareUrl}`}`;
}

function assigneeUserIdPayload() {
  const assigneeUserId = commentForm.assigneeUserId.trim();
  if (!assigneeUserId) return undefined;
  const numericAssigneeUserId = Number(assigneeUserId);
  return Number.isSafeInteger(numericAssigneeUserId) ? numericAssigneeUserId : assigneeUserId;
}

function clearSelectedAnchor() {
  selectedAnchor.sectionId = '';
  selectedAnchor.startOffset = 0;
  selectedAnchor.endOffset = 0;
  selectedAnchor.selectedText = '';
}

function changeLabel(type: string) {
  return ({ added: '新增', removed: '删除', modified: '修改' } as Record<string, string>)[type] ?? type;
}

function showSuccess(text: string) {
  messageType.value = 'success';
  message.value = text;
}

function showError(error: unknown, fallback: string) {
  messageType.value = 'error';
  message.value = error instanceof Error ? error.message : fallback;
}
</script>

<style scoped>
.report-detail-page {
  display: grid;
  gap: 16px;
}

.toolbar,
.panel-header,
.version-controls {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.toolbar h2 {
  margin: 0;
}

.muted {
  color: #64748b;
  margin: 4px 0 0;
}

.download-result {
  margin: 0;
}

.download-result a {
  color: #1d4ed8;
  font-weight: 700;
}

.export-panel {
  display: grid;
  gap: 14px;
  padding-block: 4px 12px;
  border-bottom: 1px solid #e5e7eb;
}

.format-row {
  display: grid;
  gap: 8px;
  grid-template-columns: repeat(4, minmax(0, 1fr));
}

.format-row button {
  min-height: 64px;
  border: 1px solid #d1d5db;
  background: #fff;
  color: #111827;
  cursor: pointer;
  display: grid;
  gap: 4px;
  padding: 10px;
  text-align: left;
}

.format-row button.active {
  border-color: #2563eb;
  background: #eff6ff;
}

.format-row button.disabled {
  background: #f9fafb;
  color: #9ca3af;
  cursor: not-allowed;
}

.format-row span {
  font-weight: 700;
}

.format-row small {
  font-size: 12px;
}

.brand-form {
  display: grid;
  gap: 8px 12px;
  grid-template-columns: repeat(3, minmax(0, 1fr));
}

.section-list {
  display: grid;
  gap: 16px;
}

.section-list h3,
.diff-panel h3,
.reference-detail h3 {
  margin: 0 0 8px;
}

.section-list p,
.diff-panel p,
.reference-detail p {
  margin: 0;
  line-height: 1.7;
}

.citation-row {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 8px;
}

.citation-button {
  border: 1px solid #bfdbfe;
  background: #eff6ff;
  color: #1d4ed8;
  cursor: pointer;
  font-size: 13px;
  padding: 4px 8px;
}

.reference-detail {
  display: grid;
  gap: 10px;
}

.score-list {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.score-list span,
.anchor-text {
  background: #f8fafc;
  border: 1px solid #e2e8f0;
  color: #334155;
  padding: 6px 8px;
}

.version-controls {
  justify-content: flex-start;
  flex-wrap: wrap;
  margin-bottom: 12px;
}

.version-controls label {
  display: grid;
  gap: 6px;
  color: #334155;
  font-size: 14px;
}

.version-controls select {
  min-width: 190px;
  border: 1px solid #cbd5e1;
  padding: 8px;
}

.version-table {
  width: 100%;
}

.diff-panel {
  margin-top: 16px;
  border-top: 1px solid #e5e7eb;
  padding-top: 14px;
}

.collaboration-panel {
  margin-top: 16px;
}

.share-panel {
  margin-top: 16px;
}

.comment-form {
  display: grid;
  gap: 4px;
}

.share-form,
.share-result {
  display: grid;
  gap: 8px;
}

.share-result {
  border-top: 1px solid #e5e7eb;
  margin-top: 12px;
  padding-top: 12px;
}

.share-result p {
  margin: 0;
}

.share-url {
  color: #1d4ed8;
  overflow-wrap: anywhere;
}

.share-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.selected-text {
  border-left: 3px solid #2563eb;
  color: #1e3a8a;
  margin: 0 0 12px;
  padding-left: 8px;
}

.diff-panel ul {
  display: grid;
  gap: 10px;
  list-style: none;
  margin: 12px 0 0;
  padding: 0;
}

.diff-panel li {
  border: 1px solid #e2e8f0;
  padding: 10px;
}

@media (max-width: 900px) {
  .format-row,
  .brand-form {
    grid-template-columns: 1fr;
  }

  .toolbar,
  .panel-header {
    align-items: flex-start;
    flex-direction: column;
  }
}
</style>
