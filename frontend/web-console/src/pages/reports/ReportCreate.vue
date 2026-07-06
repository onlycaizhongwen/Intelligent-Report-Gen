<template>
  <section class="page">
    <header class="page-header">
      <h2>智能报告生成</h2>
    </header>

    <el-tabs v-model="mode" class="create-tabs">
      <el-tab-pane label="智能生成" name="natural">
        <el-form :model="naturalForm" label-position="top" class="create-form">
          <el-form-item label="报告主题">
            <el-input v-model="naturalForm.topic" placeholder="输入报告主题" />
          </el-form-item>
          <el-form-item label="重点关注">
            <el-input v-model="naturalForm.focus" type="textarea" :rows="4" placeholder="输入关注重点、分析范围或输出要求" />
          </el-form-item>
          <el-button type="primary" :loading="submitting" @click="submitNatural">生成大纲</el-button>
        </el-form>
      </el-tab-pane>

      <el-tab-pane label="模板填报" name="template">
        <el-form label-position="top" class="create-form">
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

          <el-alert v-if="templateError" :title="templateError" type="error" :closable="false" />
          <el-button type="primary" :loading="submitting" :disabled="!selectedTemplate" @click="submitTemplate">按模板生成大纲</el-button>
        </el-form>
      </el-tab-pane>
    </el-tabs>

    <el-alert v-if="resultText" :title="resultText" type="success" :closable="false" />

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
          <dd>{{ templateResult.status }}</dd>
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
import { computed, onMounted, reactive, ref } from 'vue';
import { RouterLink } from 'vue-router';
import { reportApi, type ReportTemplate, type TemplateReportTaskResponse } from '../../api/reportApi';

const mode = ref<'natural' | 'template'>('natural');
const naturalForm = reactive({ topic: '', focus: '' });
const templates = ref<ReportTemplate[]>([]);
const selectedTemplateId = ref('');
const templatePayload = reactive<Record<string, string>>({});
const submitting = ref(false);
const resultText = ref('');
const templateError = ref('');
const templateResult = ref<TemplateReportTaskResponse | null>(null);

const selectedTemplate = computed(() => templates.value.find((template) => template.templateId === selectedTemplateId.value));
const templateParameterEntries = computed(() =>
  Object.entries(templateResult.value?.templateSnapshot?.parameters ?? {})
);

const applyTemplateDefaults = () => {
  templateError.value = '';
  const template = selectedTemplate.value;
  if (!template) return;
  for (const field of template.fields) {
    templatePayload[field.fieldKey] = field.defaultValue ?? '';
  }
};

const loadTemplates = async () => {
  templates.value = await reportApi.listTemplates();
  if (!selectedTemplateId.value && templates.value.length > 0) {
    selectedTemplateId.value = templates.value[0].templateId;
    applyTemplateDefaults();
  }
};

const submitNatural = async () => {
  submitting.value = true;
  resultText.value = '';
  templateResult.value = null;
  try {
    const task = await reportApi.createTask({ ...naturalForm });
    resultText.value = `${task.taskId} ${task.status}`;
  } finally {
    submitting.value = false;
  }
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
  resultText.value = '';
  templateResult.value = null;
  try {
    const task = await reportApi.createTemplateTask({
      templateId: selectedTemplate.value.templateId,
      payload: { ...templatePayload }
    });
    templateResult.value = task;
    resultText.value = `${task.taskId} ${task.status}`;
  } finally {
    submitting.value = false;
  }
};

onMounted(loadTemplates);
</script>

<style scoped>
.page {
  max-width: 820px;
  display: grid;
  gap: 16px;
}

.page-header h2 {
  margin: 0;
  font-size: 22px;
}

.create-tabs,
.create-form {
  display: grid;
  gap: 12px;
}

.full-width {
  width: 100%;
}

.template-result {
  border: 1px solid #dbeafe;
  display: grid;
  gap: 12px;
  padding: 14px;
}

.template-result h3 {
  margin: 0;
  font-size: 18px;
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
</style>
