<template>
  <section class="page">
    <header class="toolbar">
      <h2>用户权限</h2>
      <el-button type="primary" :loading="loading" @click="loadUsers">刷新</el-button>
    </header>

    <el-alert v-if="error" :title="error" type="error" :closable="false" />
    <el-alert v-if="importSummary" :title="importSummary" type="success" :closable="false" />

    <section class="import-panel" aria-labelledby="batch-import-title">
      <h3 id="batch-import-title">批量导入</h3>
      <el-input
        v-model="batchText"
        aria-label="批量导入用户"
        type="textarea"
        :rows="4"
        placeholder="账号,显示名,角色；每行一个用户，例如 analyst.one,Analyst One,analyst"
      />
      <el-button type="primary" :loading="importing" @click="submitBatchImport">执行导入</el-button>
    </section>

    <el-table :data="users" row-key="userId">
      <el-table-column prop="username" label="账号" min-width="160" />
      <el-table-column prop="displayName" label="姓名" min-width="140" />
      <el-table-column label="角色" min-width="180">
        <template #default="{ row }">
          {{ row.roles.join(', ') }}
        </template>
      </el-table-column>
      <el-table-column prop="status" label="状态" width="120" />
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
  </section>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue';
import { adminApi, type AdminUser, type BatchImportUsersRequest } from '../../api/adminApi';

type UserRow = AdminUser;

const users = ref<UserRow[]>([]);
const batchText = ref('');
const importSummary = ref('');
const error = ref('');
const loading = ref(false);
const importing = ref(false);

onMounted(loadUsers);

async function loadUsers() {
  loading.value = true;
  error.value = '';
  try {
    const response = await adminApi.listUsers({ page: 1, pageSize: 20 });
    users.value = response.items.map(normalizeUser);
  } catch (err) {
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
      const [username = '', displayName = '', roles = 'viewer'] = line.split(',').map((part) => part.trim());
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
    userId: user.userId,
    username: user.username,
    displayName: user.displayName || user.username,
    status: user.status || 'enabled',
    roles: Array.isArray(user.roles) ? user.roles : []
  };
}
</script>

<style scoped>
.toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
}

.import-panel {
  display: grid;
  gap: 10px;
  margin: 14px 0;
}

.import-panel h3 {
  margin: 0;
  font-size: 16px;
}
</style>
