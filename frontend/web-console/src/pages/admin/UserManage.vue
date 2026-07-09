<template>
  <section class="page permission-page">
    <header class="toolbar">
      <div>
        <h2>权限协作</h2>
        <p>聚合用户管理、权限矩阵、报告协作和操作日志。</p>
      </div>
      <el-button type="primary" :loading="loading" @click="loadUsers">刷新</el-button>
    </header>

    <el-alert v-if="error" :title="error" type="error" :closable="false" />
    <el-alert v-if="importSummary" :title="importSummary" type="success" :closable="false" />

    <el-tabs v-model="activeTab">
      <el-tab-pane label="用户管理" name="users">
        <section class="import-panel" aria-labelledby="batch-import-title">
          <div class="section-heading">
            <h3 id="batch-import-title">用户管理</h3>
            <div class="actions">
              <el-button>+ 添加用户</el-button>
              <el-button>批量导入</el-button>
            </div>
          </div>
          <el-input
            v-model="batchText"
            aria-label="批量导入用户"
            type="textarea"
            :rows="4"
            placeholder="账号,显示名,角色；每行一个用户，例如 analyst.one,张三,分析师"
          />
          <el-button type="primary" :loading="importing" @click="submitBatchImport">执行导入</el-button>
        </section>

        <el-table :data="users" row-key="userId" empty-text="暂无数据">
          <el-table-column prop="username" label="用户名" min-width="160" />
          <el-table-column prop="displayName" label="姓名" min-width="140" />
          <el-table-column label="角色" min-width="180">
            <template #default="{ row }">{{ row.roles.join(', ') }}</template>
          </el-table-column>
          <el-table-column prop="department" label="部门" min-width="140" />
          <el-table-column label="状态" width="120">
            <template #default="{ row }">{{ userStatusLabel(row.status) }}</template>
          </el-table-column>
          <el-table-column label="操作" width="120">
            <template #default="{ row }">
              <el-button
                size="small"
                :type="row.status === 'disabled' ? 'success' : 'danger'"
                @click="toggleStatus(row)"
              >
                {{ row.status === 'disabled' ? '启用' : '禁用' }}
              </el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>

      <el-tab-pane label="权限矩阵" name="matrix">
        <section class="matrix-panel">
          <h3>角色权限矩阵</h3>
          <el-table :data="permissionMatrix" border>
            <el-table-column prop="permission" label="权限项" min-width="180" />
            <el-table-column prop="admin" label="管理员" width="130" />
            <el-table-column prop="seniorAnalyst" label="高级分析师" width="140" />
            <el-table-column prop="analyst" label="分析师" width="120" />
            <el-table-column prop="viewer" label="查看者" width="120" />
          </el-table>
          <p class="hint">允许 = 允许访问，禁止 = 不允许访问。</p>
        </section>
      </el-tab-pane>

      <el-tab-pane label="报告协作" name="collaboration">
        <section class="collaboration-panel">
          <h3>报告协作 — 「Q3华东区销售分析报告」</h3>
          <article class="report-preview">
            <h4>Q3华东区销售分析报告</h4>
            <p>一、概述：2026年Q3华东区整体销售表现强劲，实现营收 1.2亿元，同比增长15.3%。</p>
            <p>二、区域分析：上海区域贡献最大，达到 6800万元；杭州区域贡献3800万元，同比增长22.1%。</p>
            <p>三、客户结构：大客户贡献占比 62.3%，新客户贡献占比17.8%。</p>
          </article>
          <section class="collaboration-grid">
            <article>
              <h4>链接分享</h4>
              <p>https://report.example.com/s/abc123def456</p>
              <el-button>生成</el-button>
            </article>
            <article>
              <h4>批注(3)</h4>
              <p>张三：建议补充分区域对比数据</p>
              <p>王五：营收数据与财报库不一致，请核实</p>
            </article>
            <article>
              <h4>任务</h4>
              <p>核实营收数据 · 指派给：张三 · 进行中</p>
              <p>补充南京区域分析 · 指派给：李四 · 待开始</p>
            </article>
          </section>
        </section>
      </el-tab-pane>

      <el-tab-pane label="操作日志" name="logs">
        <section class="log-panel">
          <h3>操作日志</h3>
          <el-table :data="operationLogs" row-key="id" empty-text="暂无操作日志">
            <el-table-column prop="time" label="时间" width="180" />
            <el-table-column prop="type" label="操作类型" width="140" />
            <el-table-column prop="content" label="内容" min-width="260" />
            <el-table-column prop="operator" label="操作人" width="120" />
          </el-table>
        </section>
      </el-tab-pane>
    </el-tabs>
  </section>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue';
import { adminApi, type AdminUser, type BatchImportUsersRequest } from '../../api/adminApi';
import { isLocalPreviewUnauthorizedError } from '../../api/client';

type UserRow = AdminUser & { department?: string };

const activeTab = ref('users');
const users = ref<UserRow[]>([]);
const batchText = ref('');
const importSummary = ref('');
const error = ref('');
const loading = ref(false);
const importing = ref(false);

const permissionMatrix = [
  { permission: '报告生成', admin: '允许', seniorAnalyst: '允许', analyst: '允许', viewer: '禁止' },
  { permission: '知识库管理', admin: '允许', seniorAnalyst: '允许', analyst: '禁止', viewer: '禁止' },
  { permission: '规则引擎', admin: '允许', seniorAnalyst: '允许', analyst: '禁止', viewer: '禁止' },
  { permission: '企业导出模板', admin: '允许', seniorAnalyst: '允许', analyst: '禁止', viewer: '禁止' },
  { permission: '用户权限', admin: '允许', seniorAnalyst: '禁止', analyst: '禁止', viewer: '禁止' }
];

const operationLogs = [
  { id: 'log-1', time: '2026-06-11 10:30', type: '添加用户', content: '新增高级分析师张三', operator: '管理员' },
  { id: 'log-2', time: '2026-06-11 09:50', type: '分享报告', content: '分享 Q3华东区销售分析报告', operator: '张三' },
  { id: 'log-3', time: '2026-06-10 16:00', type: '权限变更', content: '调整分析师导出权限', operator: '管理员' }
];

onMounted(loadUsers);

async function loadUsers() {
  loading.value = true;
  error.value = '';
  try {
    const response = await adminApi.listUsers({ page: 1, pageSize: 20 });
    users.value = response.items.map(normalizeUser);
  } catch (err) {
    if (isLocalPreviewUnauthorizedError(err)) {
      users.value = previewUsers();
      return;
    }
    error.value = (err as Error).message || '无权限';
  } finally {
    loading.value = false;
  }
}

async function submitBatchImport() {
  error.value = '';
  importSummary.value = '';
  const payload = parseBatchText(batchText.value);
  if (payload.users.length === 0) {
    error.value = '请填写至少一个用户';
    return;
  }
  importing.value = true;
  try {
    const result = await adminApi.batchImportUsers(payload);
    importSummary.value = `导入成功 ${result.imported} 个，失败 ${result.failed} 个`;
    batchText.value = '';
    await loadUsers();
  } catch (err) {
    error.value = (err as Error).message || '批量导入失败';
  } finally {
    importing.value = false;
  }
}

async function toggleStatus(user: UserRow) {
  error.value = '';
  const nextStatus = user.status === 'disabled' ? 'enabled' : 'disabled';
  try {
    const updated = await adminApi.updateUserStatus(user.userId, { status: nextStatus });
    users.value = users.value.map((item) =>
      item.userId === user.userId ? normalizeUser({ ...item, ...updated }) : item
    );
  } catch (err) {
    error.value = (err as Error).message || '更新用户状态失败';
  }
}

function parseBatchText(text: string): BatchImportUsersRequest {
  const users = text.split(/\r?\n/)
    .map((line) => line.trim())
    .filter(Boolean)
    .map((line) => {
      const [username = '', displayName = '', roles = '查看者'] = line.split(',').map((part) => part.trim());
      return {
        username,
        displayName: displayName || username,
        roles: roles.split(/[|;]/).map((role) => role.trim()).filter(Boolean)
      };
    });
  return { users };
}

function normalizeUser(user: UserRow): UserRow {
  return {
    ...user,
    userId: user.userId,
    username: user.username,
    displayName: user.displayName || user.username,
    status: user.status || 'enabled',
    roles: Array.isArray(user.roles) ? user.roles : [],
    department: user.department || '-'
  };
}

function previewUsers(): UserRow[] {
  return [
    { userId: 1, username: 'zhangsan', displayName: '张三', status: 'enabled', roles: ['高级分析师'], department: '销售分析部' },
    { userId: 2, username: 'lisi', displayName: '李四', status: 'enabled', roles: ['分析师'], department: '数据治理部' },
    { userId: 3, username: 'wangwu', displayName: '王五', status: 'disabled', roles: ['查看者'], department: '财务中心' }
  ];
}

function userStatusLabel(status: string) {
  const labels: Record<string, string> = {
    enabled: '已启用',
    disabled: '已禁用'
  };
  return labels[status] ?? status;
}
</script>

<style scoped>
.permission-page {
  display: grid;
  gap: 16px;
}

.toolbar,
.section-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.toolbar h2,
.toolbar p,
.section-heading h3,
.matrix-panel h3,
.collaboration-panel h3,
.log-panel h3 {
  margin: 0;
}

.toolbar p,
.hint {
  color: #606266;
  font-size: 13px;
}

.import-panel,
.matrix-panel,
.collaboration-panel,
.log-panel {
  display: grid;
  gap: 12px;
}

.actions,
.collaboration-grid {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}

.report-preview,
.collaboration-grid article {
  padding: 12px;
  border: 1px solid #e5e7eb;
  border-radius: 6px;
  background: #fff;
}

.report-preview h4,
.collaboration-grid h4,
.report-preview p,
.collaboration-grid p {
  margin: 0 0 8px;
}

.collaboration-grid article {
  flex: 1 1 240px;
}
</style>
