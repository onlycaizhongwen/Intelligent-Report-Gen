<template>
  <section class="page data-source-page">
    <header class="page-header">
      <h2>数据源配置</h2>
      <el-button type="primary" :loading="saving" @click="save">保存数据源</el-button>
    </header>

    <el-alert v-if="message" :title="message" :type="messageType" show-icon :closable="false" />

    <el-form label-position="top" class="config-form">
      <el-form-item label="数据源名称">
        <el-input v-model="form.name" aria-label="数据源名称" />
      </el-form-item>

      <el-form-item label="数据源类型">
        <el-select v-model="form.sourceType" aria-label="数据源类型" class="full-width">
          <el-option label="PostgreSQL" value="postgresql" />
          <el-option label="MySQL" value="mysql" />
          <el-option label="HTTP API" value="api" />
        </el-select>
      </el-form-item>

      <el-form-item label="连接地址">
        <el-input v-model="form.endpoint" aria-label="连接地址" />
      </el-form-item>

      <div class="form-grid">
        <el-form-item label="用户名或 Token 标识">
          <el-input v-model="form.username" aria-label="用户名或 Token 标识" />
        </el-form-item>
        <el-form-item label="密码或访问密钥">
          <el-input v-model="form.password" aria-label="密码或访问密钥" type="password" show-password />
        </el-form-item>
      </div>

      <div class="form-grid">
        <el-form-item label="目标知识库 ID">
          <el-input-number v-model="form.knowledgeBaseId" aria-label="目标知识库 ID" :min="1" />
        </el-form-item>
        <el-form-item label="增量游标列">
          <el-input v-model="form.cursorColumn" aria-label="增量游标列" placeholder="id 或 updated_at" />
        </el-form-item>
      </div>

      <el-form-item v-if="form.sourceType !== 'api'" label="同步 SQL">
        <el-input
          v-model="form.syncQuery"
          aria-label="同步 SQL"
          type="textarea"
          :rows="4"
          placeholder="select id, title, content from enterprise_reports"
        />
      </el-form-item>

      <section v-if="form.sourceType === 'api'" class="subsection">
        <h3>API 字段映射</h3>
        <div class="form-grid">
          <el-form-item label="API 行路径">
            <el-input v-model="form.fieldMapping.rowsPath" aria-label="API 行路径" placeholder="data.items" />
          </el-form-item>
          <el-form-item label="标题字段">
            <el-input v-model="form.fieldMapping.titleField" aria-label="标题字段" placeholder="headline" />
          </el-form-item>
          <el-form-item label="内容字段">
            <el-input v-model="form.fieldMapping.contentField" aria-label="内容字段" placeholder="body" />
          </el-form-item>
        </div>
      </section>

      <section v-if="form.sourceType === 'api'" class="subsection">
        <h3>API 请求配置</h3>
        <div class="form-grid">
          <el-form-item label="请求方法">
            <el-select v-model="form.fieldMapping.method" aria-label="请求方法" class="full-width">
              <el-option label="GET" value="GET" />
              <el-option label="POST" value="POST" />
            </el-select>
          </el-form-item>
          <el-form-item label="认证方式">
            <el-select v-model="form.fieldMapping.authType" aria-label="认证方式" class="full-width">
              <el-option label="Bearer Token" value="bearer" />
              <el-option label="API Key Header" value="api_key" />
              <el-option label="Basic Auth" value="basic" />
              <el-option label="不认证" value="none" />
            </el-select>
          </el-form-item>
          <el-form-item label="API Key Header">
            <el-input v-model="form.fieldMapping.apiKeyHeader" aria-label="API Key Header" placeholder="X-API-Key" />
          </el-form-item>
          <el-form-item label="自定义 Header">
            <el-input v-model="headersText" aria-label="自定义 Header" placeholder="X-Tenant: finance" />
          </el-form-item>
        </div>
        <el-form-item v-if="form.fieldMapping.method === 'POST'" label="POST Body">
          <el-input
            v-model="form.fieldMapping.bodyTemplate"
            aria-label="POST Body"
            type="textarea"
            :rows="4"
            placeholder='{"period":"2026Q1"}'
          />
        </el-form-item>
      </section>

      <section v-if="form.sourceType === 'api'" class="subsection">
        <h3>API 分页</h3>
        <div class="form-grid">
          <el-form-item label="页码参数">
            <el-input v-model="form.fieldMapping.pageParam" aria-label="页码参数" placeholder="page" />
          </el-form-item>
          <el-form-item label="起始页码">
            <el-input-number v-model="form.fieldMapping.pageStart" aria-label="起始页码" :min="0" />
          </el-form-item>
          <el-form-item label="每页数量参数">
            <el-input v-model="form.fieldMapping.pageSizeParam" aria-label="每页数量参数" placeholder="pageSize" />
          </el-form-item>
          <el-form-item label="每页数量">
            <el-input-number v-model="form.fieldMapping.pageSize" aria-label="每页数量" :min="1" />
          </el-form-item>
          <el-form-item label="最大页数">
            <el-input-number v-model="form.fieldMapping.maxPages" aria-label="最大页数" :min="1" :max="100" />
          </el-form-item>
        </div>
      </section>

      <section class="subsection">
        <h3>调度与重试</h3>
        <div class="form-grid">
          <el-form-item label="启用定时同步">
            <el-checkbox v-model="form.scheduleEnabled" aria-label="启用定时同步">启用</el-checkbox>
          </el-form-item>
          <el-form-item label="同步间隔秒">
            <el-input-number
              v-model="form.scheduleIntervalSeconds"
              aria-label="同步间隔秒"
              :min="30"
              :step="30"
            />
          </el-form-item>
          <el-form-item label="最大失败重试次数">
            <el-input-number v-model="form.maxRetryCount" aria-label="最大失败重试次数" :min="0" :max="20" />
          </el-form-item>
        </div>
      </section>
    </el-form>

    <section v-if="savedDataSource" class="status-panel">
      <div>
        <strong>{{ savedDataSource.name }}</strong>
        <span>
          {{ savedDataSource.sourceType }} / {{ savedDataSource.status }} /
          {{ savedDataSource.credentialConfigured ? '凭据已配置' : '未配置凭据' }}
          <template v-if="savedDataSource.cursorColumn"> / 游标列 {{ savedDataSource.cursorColumn }}</template>
          <template v-if="savedDataSource.scheduleEnabled"> / 每 {{ savedDataSource.scheduleIntervalSeconds }} 秒同步</template>
          / 最大重试 {{ savedDataSource.maxRetryCount ?? 3 }} 次
        </span>
      </div>
      <div class="actions">
        <el-button :loading="testing" @click="testConnection">测试连接</el-button>
        <el-button type="primary" :loading="syncing" @click="startSync('manual')">启动同步</el-button>
        <el-button :loading="syncing" @click="startSync('manual_retry')">人工重跑</el-button>
      </div>
    </section>

    <section class="sync-panel">
      <h3>同步日志</h3>
      <el-table v-if="syncRuns.length" :data="syncRuns" size="small">
        <el-table-column prop="syncRunId" label="Run ID" width="100" />
        <el-table-column prop="mode" label="模式" width="120" />
        <el-table-column prop="status" label="状态" width="120" />
        <el-table-column prop="processedRows" label="处理行数" width="120" />
        <el-table-column prop="lastCursor" label="游标" width="120" />
        <el-table-column prop="message" label="消息" />
        <el-table-column prop="failureReason" label="失败原因" />
      </el-table>
      <el-empty v-else description="暂无同步日志" />
    </section>
  </section>
</template>

<script setup lang="ts">
import { ref } from 'vue';
import { knowledgeApi, type DataSourceFieldMapping, type DataSourceSyncRun } from '../../api/knowledgeApi';

interface DataSourceSummary {
  dataSourceId: number | string;
  name: string;
  sourceType: string;
  endpoint: string;
  status: string;
  knowledgeBaseId?: number | string;
  cursorColumn?: string;
  scheduleEnabled?: boolean;
  scheduleIntervalSeconds?: number;
  maxRetryCount?: number;
  credentialConfigured?: boolean;
}

const form = ref({
  name: 'ERP PostgreSQL',
  sourceType: 'postgresql',
  endpoint: 'jdbc:postgresql://localhost:5432/erp',
  username: 'erp_reader',
  password: '',
  knowledgeBaseId: 1,
  syncQuery: 'select id, title, content from enterprise_reports',
  fieldMapping: {
    rowsPath: 'data.items',
    titleField: 'title',
    contentField: 'content',
    method: 'GET',
    authType: 'bearer',
    apiKeyHeader: 'X-API-Key',
    bodyTemplate: '',
    pageParam: '',
    pageStart: 1,
    pageSizeParam: '',
    pageSize: 100,
    maxPages: 1
  } as DataSourceFieldMapping,
  cursorColumn: 'id',
  scheduleEnabled: false,
  scheduleIntervalSeconds: 300,
  maxRetryCount: 3
});
const headersText = ref('');
const savedDataSource = ref<DataSourceSummary | null>(null);
const syncRuns = ref<DataSourceSyncRun[]>([]);
const message = ref('');
const messageType = ref<'success' | 'error'>('success');
const saving = ref(false);
const testing = ref(false);
const syncing = ref(false);

async function save() {
  saving.value = true;
  clearMessage();
  try {
    const fieldMapping = form.value.sourceType === 'api' ? buildFieldMapping() : undefined;
    savedDataSource.value = await knowledgeApi.saveDataSource({
      name: form.value.name,
      sourceType: form.value.sourceType,
      endpoint: form.value.endpoint,
      username: form.value.username,
      password: form.value.password,
      knowledgeBaseId: form.value.knowledgeBaseId,
      syncQuery: form.value.sourceType === 'api' ? undefined : form.value.syncQuery,
      fieldMapping,
      cursorColumn: form.value.cursorColumn,
      scheduleEnabled: form.value.scheduleEnabled,
      scheduleIntervalSeconds: form.value.scheduleIntervalSeconds,
      maxRetryCount: form.value.maxRetryCount
    }) as unknown as DataSourceSummary;
    showSuccess('数据源已保存，敏感凭据不会在页面回显');
  } catch (error) {
    showError(error, '数据源保存失败');
  } finally {
    saving.value = false;
  }
}

async function testConnection() {
  if (!savedDataSource.value) return;
  testing.value = true;
  clearMessage();
  try {
    const result = await knowledgeApi.testConnection({ dataSourceId: savedDataSource.value.dataSourceId }) as unknown as {
      message: string;
    };
    showSuccess(result.message);
  } catch (error) {
    showError(error, '测试连接失败');
  } finally {
    testing.value = false;
  }
}

async function startSync(mode: 'manual' | 'manual_retry') {
  if (!savedDataSource.value) return;
  syncing.value = true;
  clearMessage();
  try {
    await knowledgeApi.startDataSourceSync(String(savedDataSource.value.dataSourceId), { mode });
    const result = await knowledgeApi.listDataSourceSyncRuns(String(savedDataSource.value.dataSourceId), {
      page: 1,
      pageSize: 10
    }) as unknown as { items: DataSourceSyncRun[] };
    syncRuns.value = result.items ?? [];
    showSuccess(mode === 'manual_retry' ? '人工重跑已完成' : '同步任务已完成');
  } catch (error) {
    showError(error, mode === 'manual_retry' ? '人工重跑失败' : '启动同步失败');
  } finally {
    syncing.value = false;
  }
}

function buildFieldMapping(): DataSourceFieldMapping {
  return {
    ...form.value.fieldMapping,
    headers: parseHeaders(headersText.value)
  };
}

function parseHeaders(text: string): Record<string, string> | undefined {
  const headers: Record<string, string> = {};
  text.split(/\r?\n/).forEach((line) => {
    const index = line.indexOf(':');
    if (index > 0) {
      const key = line.slice(0, index).trim();
      const value = line.slice(index + 1).trim();
      if (key && value) {
        headers[key] = value;
      }
    }
  });
  return Object.keys(headers).length ? headers : undefined;
}

function clearMessage() {
  message.value = '';
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
.data-source-page {
  display: grid;
  gap: 16px;
}

.page-header,
.status-panel {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.config-form {
  max-width: 820px;
}

.form-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}

.full-width {
  width: 100%;
}

.subsection {
  display: grid;
  gap: 10px;
  padding-top: 4px;
}

.subsection h3,
.sync-panel h3 {
  margin: 0;
  font-size: 16px;
}

.status-panel {
  padding: 14px 0;
  border-top: 1px solid #e5e7eb;
  border-bottom: 1px solid #e5e7eb;
}

.status-panel div:first-child {
  display: grid;
  gap: 4px;
}

.status-panel span {
  color: #6b7280;
  font-size: 13px;
}

.actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.sync-panel {
  display: grid;
  gap: 10px;
}

@media (max-width: 720px) {
  .page-header,
  .status-panel {
    align-items: flex-start;
    flex-direction: column;
  }

  .form-grid {
    grid-template-columns: 1fr;
  }
}
</style>
