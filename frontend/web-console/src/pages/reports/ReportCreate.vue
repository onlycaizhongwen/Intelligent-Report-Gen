<template>
  <section class="report-workbench">
    <aside class="input-panel">
      <header class="panel-header">
        <span>{{ mode === 'natural' ? 'AI 对话输入' : '快速模板填报' }}</span>
      </header>

      <el-tabs v-model="mode" class="mode-tabs">
        <el-tab-pane label="智能生成" name="natural">
          <div class="chat-area" aria-label="AI 对话输入">
            <div class="chat-msg ai">
              你好，我是智能报告助手。请描述报告主题、数据范围和分析重点，我会先生成大纲供你确认。
            </div>
            <div class="chat-msg ai">
              示例：生成 2026Q1 集团经营分析报告，关注收入、成本、回款风险和改进建议。
            </div>
            <div v-for="message in chatMessages" :key="message.id" class="chat-msg" :class="message.role">
              {{ message.content }}
            </div>
          </div>

          <el-form :model="naturalForm" label-position="top" class="natural-form">
            <el-form-item label="报告主题">
              <el-input v-model="naturalForm.topic" placeholder="输入报告主题" />
            </el-form-item>
            <el-form-item label="报告需求">
              <el-input
                v-model="naturalForm.focus"
                type="textarea"
                :rows="4"
                placeholder="描述你的报告需求..."
                @keydown.enter.exact.prevent="submitNatural"
              />
            </el-form-item>
            <p class="input-hint">支持自然语言描述，可指定报告类型、数据范围、分析维度和输出风格。</p>
            <el-button type="primary" class="wide-action" :loading="submitting" @click="submitNatural">
              发送生成需求
            </el-button>
          </el-form>
        </el-tab-pane>

        <el-tab-pane label="模板填报" name="template">
          <el-form label-position="top" class="template-form">
            <el-form-item label="报告模板">
              <el-select v-model="selectedTemplateId" placeholder="选择报告模板" class="full-width" @change="applyTemplateDefaults">
                <el-option
                  v-for="template in templates"
                  :key="template.templateId"
                  :label="`${template.name}（${template.version}）`"
                  :value="template.templateId"
                />
              </el-select>
            </el-form-item>

            <template v-if="selectedTemplate">
              <el-form-item
                v-for="field in selectedTemplate.fields"
                :key="field.fieldKey"
                :label="field.label"
                :required="field.required"
              >
                <el-select
                  v-if="field.type === 'select'"
                  v-model="templatePayload[field.fieldKey]"
                  class="full-width"
                  :data-testid="`template-field-${field.fieldKey}`"
                  :placeholder="field.helpText || field.label"
                >
                  <el-option v-for="option in field.options || []" :key="option" :label="option" :value="option" />
                </el-select>
                <el-input
                  v-else-if="field.type === 'textarea'"
                  v-model="templatePayload[field.fieldKey]"
                  type="textarea"
                  :data-testid="`template-field-${field.fieldKey}`"
                  :rows="3"
                  :placeholder="field.helpText || field.label"
                />
                <el-input
                  v-else
                  v-model="templatePayload[field.fieldKey]"
                  :data-testid="`template-field-${field.fieldKey}`"
                  :placeholder="field.helpText || field.label"
                />
              </el-form-item>
            </template>

            <el-empty v-else description="暂无可用模板" :image-size="72" />
            <el-alert v-if="templateError" :title="templateError" type="error" :closable="false" />
            <el-button type="primary" class="wide-action" :loading="submitting" :disabled="!selectedTemplate" @click="submitTemplate">
              按模板生成大纲
            </el-button>
          </el-form>
        </el-tab-pane>
      </el-tabs>
    </aside>

    <main class="preview-panel">
      <header class="panel-header preview-header">
        <span>报告预览</span>
        <span class="status-text">{{ reportStatus }}</span>
      </header>

      <div class="progress-steps" :class="{ muted: !generationStarted }">
        <span v-for="step in progressSteps" :key="step.key" class="progress-step" :class="step.state">
          <i class="step-dot" />
          {{ step.label }}
        </span>
      </div>

      <section class="report-content" aria-label="报告预览">
        <div v-if="!outlineVisible && !generated" class="empty-preview">
          <div class="empty-icon">文</div>
          <p>在左侧输入报告需求，开始智能生成</p>
          <span>支持大纲确认、三阶段生成、溯源引用、导出和版本记录。</span>
        </div>

        <article v-if="outlineVisible && !generated" class="outline-card">
          <h2>报告大纲预览</h2>
          <p>AI 根据你的需求规划了以下报告结构，确认后开始生成正文。</p>
          <p v-if="taskMeta" class="task-evidence">任务 {{ taskMeta.taskId }} 已生成大纲（{{ reportTaskStatusLabel(taskMeta.status) }}）</p>
          <p v-if="taskMeta" class="task-raw">{{ taskMeta.taskId }} {{ reportTaskStatusLabel(taskMeta.status) }}</p>
          <ol>
            <li v-for="item in outline" :key="item">{{ item }}</li>
          </ol>
          <div class="outline-actions">
            <el-button @click="resetReport">修改大纲</el-button>
            <el-button type="primary" @click="confirmAndGenerate">确认，开始生成</el-button>
          </div>
        </article>

        <article v-if="generated" class="generated-report">
          <h1>2026Q1 集团经营分析报告</h1>
          <h2>一、执行摘要</h2>
          <p>
            2026Q1 集团整体收入保持增长，核心业务收入同比提升 12.6%，经营利润率较上季度改善 2.1 个百分点。
            主要增长来自重点区域客户续约和新产品线扩张。
            <button class="ref-tag" type="button" @click="activeReference = 'ref-1'">[1]</button>
          </p>
          <h2>二、核心指标分析</h2>
          <p>
            成本端仍需关注原材料价格波动和回款周期拉长。财务数据表明，应收账款周转天数较上季度增加 4 天，
            需要在重点客户续约中同步设置回款节点。
            <button class="ref-tag" type="button" @click="activeReference = 'ref-2'">[2]</button>
          </p>
          <h2>三、风险提示与改进建议</h2>
          <p>
            建议将华东、华南重点客户纳入专项跟踪，建立销售、财务和交付联合例会，优先处理高金额、长账期合同。
          </p>
        </article>
      </section>
    </main>

    <aside class="side-panel">
      <section class="side-section">
        <h3>来源引用</h3>
        <button
          v-for="reference in references"
          :key="reference.id"
          class="reference-item"
          :class="{ active: activeReference === reference.id }"
          type="button"
          @click="activeReference = reference.id"
        >
          <strong>{{ reference.title }}</strong>
          <span>{{ reference.library }} · 可信度 {{ reference.score }}%</span>
        </button>
      </section>

      <section class="side-section">
        <h3>导出报告</h3>
        <div class="export-list">
          <el-button plain>PDF 文件</el-button>
          <el-button plain>Word 文件</el-button>
          <el-button plain>PPT 演示</el-button>
          <el-button plain>Markdown 源码</el-button>
        </div>
      </section>

      <section class="side-section">
        <h3>版本历史</h3>
        <div class="version-item"><span>v3 · 刚刚</span><b>当前</b></div>
        <div class="version-item"><span>v2 · 2 分钟前</span><button type="button">回滚</button></div>
        <div class="version-item"><span>v1 · 5 分钟前</span><button type="button">回滚</button></div>
      </section>
    </aside>

    <section v-if="templateResult" class="template-result" aria-label="模板填报结果">
      <h3>模板填报验收</h3>
      <dl>
        <div>
          <dt>任务</dt>
          <dd>模板任务 {{ templateResult.taskId }}</dd>
        </div>
        <div v-if="templateResult.reportId">
          <dt>报告</dt>
          <dd>报告 {{ templateResult.reportId }}</dd>
        </div>
        <div>
          <dt>状态</dt>
          <dd>{{ reportTaskStatusLabel(templateResult.status) }}</dd>
        </div>
        <div v-if="templateResult.templateSnapshot?.templateId">
          <dt>模板</dt>
          <dd>模板 {{ templateResult.templateSnapshot.templateId }}</dd>
        </div>
        <div v-if="templateResult.templateSnapshot?.version">
          <dt>版本</dt>
          <dd>版本 {{ templateResult.templateSnapshot.version }}</dd>
        </div>
      </dl>
      <ul v-if="templateParameterEntries.length">
        <li v-for="[key, value] in templateParameterEntries" :key="key">{{ key }}: {{ value }}</li>
      </ul>
      <RouterLink class="outline-link" :to="`/reports/${templateResult.taskId}/outline`">查看大纲</RouterLink>
    </section>
  </section>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue';
import { RouterLink, useRoute } from 'vue-router';
import { isLocalPreviewUnauthorizedError } from '../../api/client';
import { reportApi, type ReportTemplate, type TemplateReportTaskResponse } from '../../api/reportApi';

type Mode = 'natural' | 'template';
type StepState = 'waiting' | 'active' | 'done';

const route = useRoute();
const mode = ref<Mode>('natural');
const naturalForm = reactive({ topic: '', focus: '' });
const templates = ref<ReportTemplate[]>([]);
const selectedTemplateId = ref('');
const templatePayload = reactive<Record<string, string>>({});
const submitting = ref(false);
const templateError = ref('');
const templateResult = ref<TemplateReportTaskResponse | null>(null);
const taskMeta = ref<{ taskId: string; status: string } | null>(null);
const outlineVisible = ref(false);
const generated = ref(false);
const generationStarted = ref(false);
const currentStep = ref(0);
const reportStatus = ref('');
const activeReference = ref('ref-1');
const chatMessages = ref<Array<{ id: number; role: 'user' | 'ai'; content: string }>>([]);

const outline = ['执行摘要：整体经营概览与核心结论', '核心指标分析：收入、成本、客户和区域', '风险提示与改进建议', '下季度展望与行动计划'];
const references = [
  { id: 'ref-1', title: '2026Q1 经营数据汇总表', library: '财报库', score: 92 },
  { id: 'ref-2', title: '重点客户回款风险清单', library: '规则库', score: 88 },
  { id: 'ref-3', title: '行业增长率基准报告', library: '调研库', score: 84 }
];

const selectedTemplate = computed(() => templates.value.find((template) => template.templateId === selectedTemplateId.value));
const templateParameterEntries = computed(() =>
  Object.entries(templateResult.value?.templateSnapshot?.parameters ?? {})
);
const progressSteps = computed<Array<{ key: string; label: string; state: StepState }>>(() =>
  ['检索知识库', '分析数据', '生成报告'].map((label, index) => {
    const step = index + 1;
    if (!generationStarted.value) return { key: label, label, state: 'waiting' };
    if (currentStep.value > step) return { key: label, label, state: 'done' };
    if (currentStep.value === step) return { key: label, label, state: 'active' };
    return { key: label, label, state: 'waiting' };
  })
);

const syncModeFromRoute = () => {
  mode.value = route.query.mode === 'template' ? 'template' : 'natural';
};

const applyTemplateDefaults = () => {
  templateError.value = '';
  const template = selectedTemplate.value;
  if (!template) return;
  for (const field of template.fields) {
    templatePayload[field.fieldKey] = field.defaultValue ?? '';
  }
};

const loadTemplates = async () => {
  try {
    templates.value = await reportApi.listTemplates();
    if (!selectedTemplateId.value && templates.value.length > 0) {
      selectedTemplateId.value = templates.value[0].templateId;
      applyTemplateDefaults();
    }
  } catch (error) {
    if (isLocalPreviewUnauthorizedError(error)) {
      templates.value = [];
      selectedTemplateId.value = '';
      return;
    }
    templateError.value = error instanceof Error ? error.message : '模板加载失败';
  }
};

const submitNatural = async () => {
  const topic = naturalForm.topic.trim() || naturalForm.focus.trim();
  if (!topic) return;
  submitting.value = true;
  templateResult.value = null;
  generated.value = false;
  generationStarted.value = false;
  reportStatus.value = '正在解析需求...';
  chatMessages.value.push({ id: Date.now(), role: 'user', content: naturalForm.focus || naturalForm.topic });
  try {
    const task = await reportApi.createTask({ topic, focus: naturalForm.focus });
    taskMeta.value = task;
    outlineVisible.value = true;
    reportStatus.value = '大纲待确认';
    chatMessages.value.push({ id: Date.now() + 1, role: 'ai', content: `已生成报告大纲，任务 ${task.taskId} 等待确认。` });
  } finally {
    submitting.value = false;
  }
};

const confirmAndGenerate = () => {
  outlineVisible.value = false;
  generated.value = true;
  generationStarted.value = true;
  currentStep.value = 4;
  reportStatus.value = '报告生成完成';
};

const resetReport = () => {
  outlineVisible.value = false;
  generated.value = false;
  generationStarted.value = false;
  currentStep.value = 0;
  reportStatus.value = '';
};

const validateTemplatePayload = () => {
  const template = selectedTemplate.value;
  if (!template) return '请选择报告模板';
  const missing = template.fields.find((field) => field.required && !String(templatePayload[field.fieldKey] ?? '').trim());
  return missing ? `请填写${missing.label}` : '';
};

const submitTemplate = async () => {
  templateError.value = validateTemplatePayload();
  if (templateError.value || !selectedTemplate.value) return;
  submitting.value = true;
  templateResult.value = null;
  resetReport();
  try {
    const task = await reportApi.createTemplateTask({
      templateId: selectedTemplate.value.templateId,
      payload: { ...templatePayload }
    });
    templateResult.value = task;
    taskMeta.value = { taskId: task.taskId, status: task.status };
    outlineVisible.value = true;
    reportStatus.value = '模板大纲待确认';
  } finally {
    submitting.value = false;
  }
};

function reportTaskStatusLabel(status: string) {
  const labels: Record<string, string> = {
    outline_generated: '大纲已生成',
    outline_ready: '大纲待确认',
    retrieval: '检索中',
    writing: '生成中',
    export: '导出中',
    completed: '已完成',
    failed: '失败'
  };
  return labels[status] ?? status;
}

watch(() => route.query.mode, syncModeFromRoute, { immediate: true });
onMounted(loadTemplates);
</script>

<style scoped>
.report-workbench {
  height: 100vh;
  display: grid;
  grid-template-columns: minmax(320px, 30%) 1fr minmax(270px, 25%);
  background: #f0f2f5;
  overflow: hidden;
}

.input-panel,
.preview-panel,
.side-panel {
  min-width: 0;
  background: #fff;
  border-right: 1px solid #e8e8e8;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.side-panel {
  border-right: 0;
}

.panel-header {
  min-height: 52px;
  padding: 0 20px;
  border-bottom: 1px solid #f0f0f0;
  display: flex;
  align-items: center;
  justify-content: space-between;
  color: #333;
  font-size: 14px;
  font-weight: 700;
}

.mode-tabs {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

:deep(.mode-tabs .el-tabs__content) {
  flex: 1;
  overflow: hidden;
}

:deep(.mode-tabs .el-tab-pane) {
  height: 100%;
  display: flex;
  flex-direction: column;
}

.chat-area {
  flex: 1;
  overflow-y: auto;
  padding: 16px;
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.chat-msg {
  max-width: 92%;
  padding: 10px 14px;
  border-radius: 10px;
  font-size: 13px;
  line-height: 1.6;
}

.chat-msg.ai {
  align-self: flex-start;
  background: #f5f5f5;
  color: #555;
}

.chat-msg.user {
  align-self: flex-end;
  background: #e6f4ff;
  color: #333;
}

.natural-form,
.template-form {
  padding: 16px;
  border-top: 1px solid #f0f0f0;
  overflow-y: auto;
}

.input-hint {
  margin: -4px 0 12px;
  color: #999;
  font-size: 12px;
}

.wide-action,
.full-width {
  width: 100%;
}

.preview-header .status-text {
  color: #1677ff;
  font-size: 13px;
  font-weight: 600;
}

.progress-steps {
  min-height: 38px;
  padding: 8px 20px;
  background: #fafafa;
  border-bottom: 1px solid #f0f0f0;
  display: flex;
  align-items: center;
  gap: 16px;
  flex-wrap: wrap;
  color: #999;
  font-size: 12px;
}

.progress-steps.muted {
  color: #bbb;
}

.progress-step {
  display: inline-flex;
  align-items: center;
  gap: 6px;
}

.progress-step.done {
  color: #52c41a;
}

.progress-step.active {
  color: #1677ff;
  font-weight: 700;
}

.step-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: #d9d9d9;
}

.progress-step.done .step-dot {
  background: #52c41a;
}

.progress-step.active .step-dot {
  background: #1677ff;
}

.report-content {
  flex: 1;
  overflow-y: auto;
  padding: 28px 32px;
  color: #333;
  font-size: 14px;
  line-height: 1.8;
}

.empty-preview {
  height: 100%;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 8px;
  color: #999;
  text-align: center;
}

.empty-icon {
  width: 56px;
  height: 56px;
  border-radius: 14px;
  background: #f5f7fb;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #1677ff;
  font-size: 26px;
  font-weight: 700;
}

.outline-card {
  max-width: 620px;
  margin: 36px auto 0;
  border: 1px solid #e8e8e8;
  border-radius: 8px;
  padding: 28px 32px;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.06);
}

.outline-card h2,
.generated-report h1,
.generated-report h2 {
  margin: 0 0 12px;
  color: #1a1a1a;
}

.outline-card p {
  margin: 0 0 12px;
  color: #666;
}

.outline-card ol {
  margin: 18px 0 0;
  padding-left: 22px;
}

.outline-card li {
  padding: 8px 0;
  border-bottom: 1px solid #f5f5f5;
}

.outline-actions {
  margin-top: 24px;
  display: flex;
  gap: 12px;
}

.task-evidence {
  color: #1677ff !important;
  font-weight: 700;
}

.task-raw {
  color: #1677ff;
  font-size: 13px;
}

.generated-report {
  max-width: 760px;
  margin: 0 auto;
}

.generated-report h1 {
  font-size: 24px;
}

.generated-report h2 {
  margin-top: 24px;
  padding-bottom: 8px;
  border-bottom: 1px solid #f0f0f0;
  font-size: 18px;
}

.ref-tag {
  border: 0;
  border-bottom: 1px dashed #1677ff;
  background: transparent;
  color: #1677ff;
  cursor: pointer;
  font: inherit;
}

.side-section {
  padding: 16px 20px;
  border-bottom: 1px solid #f0f0f0;
}

.side-section h3 {
  margin: 0 0 12px;
  color: #333;
  font-size: 13px;
}

.reference-item {
  width: 100%;
  margin-bottom: 8px;
  padding: 9px 10px;
  border: 1px solid transparent;
  border-radius: 6px;
  background: #fff;
  display: grid;
  gap: 3px;
  text-align: left;
  cursor: pointer;
}

.reference-item:hover,
.reference-item.active {
  border-color: #91caff;
  background: #e6f4ff;
}

.reference-item strong {
  color: #333;
  font-size: 12px;
}

.reference-item span {
  color: #666;
  font-size: 12px;
}

.export-list {
  display: grid;
  gap: 8px;
}

.export-list :deep(.el-button) {
  margin-left: 0;
  justify-content: flex-start;
}

.version-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 6px 0;
  color: #888;
  font-size: 12px;
}

.version-item b,
.version-item button {
  border: 0;
  border-radius: 4px;
  background: #f5f5f5;
  color: #666;
  padding: 2px 8px;
  font-size: 11px;
}

.template-result {
  position: fixed;
  left: 244px;
  right: 24px;
  bottom: 18px;
  max-height: 220px;
  overflow-y: auto;
  border: 1px solid #dbeafe;
  border-radius: 8px;
  background: #fff;
  display: grid;
  gap: 10px;
  padding: 14px;
  box-shadow: 0 8px 24px rgba(15, 23, 42, 0.12);
}

.template-result h3 {
  margin: 0;
  font-size: 16px;
}

.template-result dl {
  display: grid;
  gap: 8px;
  grid-template-columns: repeat(auto-fit, minmax(140px, 1fr));
  margin: 0;
}

.template-result dt {
  color: #64748b;
  font-size: 13px;
}

.template-result dd {
  margin: 2px 0 0;
  font-weight: 700;
}

.template-result ul {
  display: grid;
  gap: 6px;
  list-style: none;
  margin: 0;
  padding: 0;
}

.outline-link {
  color: #2563eb;
  font-weight: 700;
  text-decoration: none;
}

@media (max-width: 1180px) {
  .report-workbench {
    grid-template-columns: 320px 1fr;
  }

  .side-panel {
    display: none;
  }
}
</style>
