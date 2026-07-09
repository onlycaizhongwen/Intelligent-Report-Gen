<template>
  <section class="page">
    <header class="toolbar">
      <h2>组织管理</h2>
      <el-button type="primary" :loading="loading" @click="loadDirectory">刷新组织树</el-button>
    </header>

    <el-alert v-if="error" :title="error" type="error" :closable="false" />
    <el-alert v-if="message" :title="message" type="success" :closable="false" />

    <section class="forms">
      <form class="form-panel" aria-label="创建组织单元表单" @submit.prevent="submitUnit">
        <h3>创建组织单元</h3>
        <label>
          编码
          <el-input v-model="unitForm.code" aria-label="组织单元编码" />
        </label>
        <label>
          名称
          <el-input v-model="unitForm.name" aria-label="组织单元名称" />
        </label>
        <label>
          上级组织编号
          <el-input v-model="unitForm.parentId" aria-label="上级组织编号" />
        </label>
        <label>
          组织类型
          <el-input v-model="unitForm.unitType" aria-label="组织类型" />
        </label>
        <label>
          排序号
          <el-input v-model="unitForm.sortOrder" aria-label="组织单元排序号" />
        </label>
        <el-button native-type="submit" type="primary" :loading="savingUnit">创建组织单元</el-button>
      </form>

      <form class="form-panel" aria-label="更新组织单元表单" @submit.prevent="submitUpdateUnit">
        <h3>更新组织单元</h3>
        <label>
          组织单元编号
          <el-input v-model="updateUnitForm.unitId" aria-label="更新组织单元编号" />
        </label>
        <label>
          名称
          <el-input v-model="updateUnitForm.name" aria-label="更新组织单元名称" />
        </label>
        <label>
          上级组织编号
          <el-input v-model="updateUnitForm.parentId" aria-label="更新上级组织编号" />
        </label>
        <label>
          组织类型
          <el-input v-model="updateUnitForm.unitType" aria-label="更新组织类型" />
        </label>
        <label>
          排序号
          <el-input v-model="updateUnitForm.sortOrder" aria-label="更新组织单元排序号" />
        </label>
        <el-button native-type="submit" type="primary" :loading="updatingUnit">更新组织单元</el-button>
      </form>

      <form class="form-panel" aria-label="创建岗位表单" @submit.prevent="submitPosition">
        <h3>创建岗位</h3>
        <label>
          所属组织单元编号
          <el-input v-model="positionForm.organizationUnitId" aria-label="岗位所属组织单元编号" />
        </label>
        <label>
          岗位编码
          <el-input v-model="positionForm.code" aria-label="岗位编码" />
        </label>
        <label>
          岗位名称
          <el-input v-model="positionForm.name" aria-label="岗位名称" />
        </label>
        <label>
          角色
          <el-input v-model="positionForm.roles" aria-label="岗位角色" />
        </label>
        <label>
          负责人用户编号
          <el-input v-model="positionForm.managerUserId" aria-label="岗位负责人用户编号" />
        </label>
        <label>
          排序号
          <el-input v-model="positionForm.sortOrder" aria-label="岗位排序号" />
        </label>
        <el-button native-type="submit" type="primary" :loading="savingPosition">创建岗位</el-button>
      </form>

      <form class="form-panel" aria-label="分配用户岗位表单" @submit.prevent="submitAssignment">
        <h3>分配用户岗位</h3>
        <label>
          用户
          <select v-model="assignmentForm.userId" aria-label="分配用户选择">
            <option value="">选择用户</option>
            <option v-for="user in users" :key="user.userId" :value="String(user.userId)">
              {{ user.displayName }} / {{ user.username }} / {{ user.department || '-' }} / {{ user.position || '-' }}
            </option>
          </select>
        </label>
        <label>
          用户编号
          <el-input v-model="assignmentForm.userId" aria-label="分配用户编号" />
        </label>
        <label>
          岗位
          <select v-model="assignmentForm.positionId" aria-label="分配岗位选择">
            <option value="">选择岗位</option>
            <option v-for="position in positionOptions" :key="position.positionId" :value="String(position.positionId)">
              {{ position.unitName }} / {{ position.code }} / {{ position.name }}
            </option>
          </select>
        </label>
        <label>
          岗位编号
          <el-input v-model="assignmentForm.positionId" aria-label="分配岗位编号" />
        </label>
        <label>
          生效开始
          <el-input v-model="assignmentForm.activeFrom" type="datetime-local" aria-label="岗位分配生效开始" />
        </label>
        <label>
          生效结束
          <el-input v-model="assignmentForm.activeTo" type="datetime-local" aria-label="岗位分配生效结束" />
        </label>
        <el-checkbox v-model="assignmentForm.primary" aria-label="主岗位分配">主岗位</el-checkbox>
        <el-button native-type="submit" type="primary" :loading="savingAssignment">分配用户岗位</el-button>
      </form>

      <form class="form-panel" aria-label="批量导入岗位分配表单" @submit.prevent="submitBatchAssignments">
        <h3>批量导入岗位分配</h3>
        <label>
          分配明细
          <el-input
            v-model="batchAssignmentText"
            aria-label="批量导入岗位分配"
            type="textarea"
            :rows="5"
            placeholder="userId,positionId,primary,activeFrom,activeTo"
          />
        </label>
        <el-button native-type="submit" type="primary" :loading="batchImportingAssignments">批量导入岗位分配</el-button>
      </form>

      <form class="form-panel" aria-label="更新岗位分配表单" @submit.prevent="submitUpdateAssignment">
        <h3>更新岗位分配</h3>
        <label>
          分配编号
          <el-input v-model="updateAssignmentForm.assignmentId" aria-label="更新分配编号" />
        </label>
        <label>
          生效开始
          <el-input v-model="updateAssignmentForm.activeFrom" type="datetime-local" aria-label="更新分配生效开始" />
        </label>
        <label>
          生效结束
          <el-input v-model="updateAssignmentForm.activeTo" type="datetime-local" aria-label="更新分配生效结束" />
        </label>
        <el-checkbox v-model="updateAssignmentForm.primary" aria-label="更新为主岗位">主岗位</el-checkbox>
        <el-button native-type="submit" type="primary" :loading="updatingAssignment">更新岗位分配</el-button>
      </form>

      <form class="form-panel" aria-label="停用岗位分配表单" @submit.prevent="submitDisableAssignment">
        <h3>停用岗位分配</h3>
        <label>
          分配编号
          <el-input v-model="disableAssignmentForm.assignmentId" aria-label="停用分配编号" />
        </label>
        <label>
          停用原因
          <el-input v-model="disableAssignmentForm.reason" aria-label="停用分配原因" />
        </label>
        <el-button native-type="submit" type="danger" :loading="disablingAssignment">停用岗位分配</el-button>
      </form>
    </section>

    <section class="tree-panel" aria-label="组织树">
      <h3>组织树</h3>
      <p v-if="organizationTree.length === 0" class="empty">暂无组织单元</p>
      <ul v-else class="tree-list">
        <OrganizationNode v-for="node in organizationTree" :key="node.unitId" :node="node" />
      </ul>
    </section>
  </section>
</template>

<script setup lang="ts">
import { computed, defineComponent, h, onMounted, ref, type PropType, type VNode } from 'vue';
import {
  adminApi,
  type AdminUser,
  type OrganizationDirectoryTreeNode,
  type OrganizationDirectoryTreePosition,
  type OrganizationDirectoryUser
} from '../../api/adminApi';
import { isLocalPreviewUnauthorizedError } from '../../api/client';

const OrganizationNode = defineComponent({
  name: 'OrganizationNode',
  props: {
    node: {
      type: Object as PropType<OrganizationDirectoryTreeNode>,
      required: true
    }
  },
  setup(props) {
    const renderUser = (user: OrganizationDirectoryUser) =>
      h('li', { class: 'assignment-row', key: user.userId }, [
        `${user.displayName} / ${user.username} / ${user.department ?? '-'} / ${user.position ?? '-'}`
      ]);

    const renderPosition = (position: OrganizationDirectoryTreePosition) =>
      h('li', { class: 'position-row', key: position.positionId }, [
        h('div', `${position.code} / ${position.name} / 角色 ${position.roles.join(', ')} / 负责人 ${position.managerUserId ?? '-'}`),
        position.users && position.users.length > 0
          ? h('ul', { class: 'assignment-list' }, position.users.map(renderUser))
          : null
      ]);

    const renderNode = (node: OrganizationDirectoryTreeNode): VNode =>
      h('li', { class: 'unit-node' }, [
        h('div', { class: 'unit-title' }, `${node.code} / ${node.name} / ${node.unitType}`),
        node.positions.length > 0 ? h('ul', { class: 'position-list' }, node.positions.map(renderPosition)) : null,
        node.children.length > 0 ? h('ul', { class: 'tree-list' }, node.children.map(renderNode)) : null
      ]);

    return () => renderNode(props.node);
  }
});

interface PositionOption {
  positionId: number | string;
  code: string;
  name: string;
  unitName: string;
}

const organizationTree = ref<OrganizationDirectoryTreeNode[]>([]);
const users = ref<AdminUser[]>([]);
const loading = ref(false);
const savingUnit = ref(false);
const updatingUnit = ref(false);
const savingPosition = ref(false);
const savingAssignment = ref(false);
const batchImportingAssignments = ref(false);
const updatingAssignment = ref(false);
const disablingAssignment = ref(false);
const error = ref('');
const message = ref('');
const batchAssignmentText = ref('');

const unitForm = ref({
  code: '',
  name: '',
  parentId: '',
  unitType: 'department',
  sortOrder: '0'
});

const updateUnitForm = ref({
  unitId: '',
  name: '',
  parentId: '',
  unitType: 'department',
  sortOrder: '0'
});

const positionForm = ref({
  organizationUnitId: '',
  code: '',
  name: '',
  roles: '',
  managerUserId: '',
  sortOrder: '0'
});

const assignmentForm = ref({
  userId: '',
  positionId: '',
  activeFrom: '',
  activeTo: '',
  primary: false
});

const updateAssignmentForm = ref({
  assignmentId: '',
  activeFrom: '',
  activeTo: '',
  primary: false
});

const disableAssignmentForm = ref({
  assignmentId: '',
  reason: ''
});

onMounted(loadDirectory);

async function loadDirectory() {
  loading.value = true;
  error.value = '';
  try {
    const [directory, userPage] = await Promise.all([
      adminApi.organizationDirectory(),
      adminApi.listUsers({ page: 1, pageSize: 100 })
    ]);
    organizationTree.value = directory.organizationTree ?? [];
    users.value = userPage.items.filter((user) => user.status === 'enabled');
  } catch (err) {
    if (isLocalPreviewUnauthorizedError(err)) {
      organizationTree.value = [];
      users.value = [];
      return;
    }
    error.value = (err as Error).message || '组织目录加载失败';
  } finally {
    loading.value = false;
  }
}

const positionOptions = computed<PositionOption[]>(() => flattenPositionOptions(organizationTree.value));

function flattenPositionOptions(nodes: OrganizationDirectoryTreeNode[]): PositionOption[] {
  return nodes.flatMap((node) => [
    ...node.positions.map((position) => ({
      positionId: position.positionId,
      code: position.code,
      name: position.name,
      unitName: node.name
    })),
    ...flattenPositionOptions(node.children)
  ]);
}

async function submitUnit() {
  error.value = '';
  message.value = '';
  if (!unitForm.value.code.trim() || !unitForm.value.name.trim()) {
    error.value = '组织单元编码和名称必填';
    return;
  }
  savingUnit.value = true;
  try {
    const payload = {
      code: unitForm.value.code.trim(),
      name: unitForm.value.name.trim(),
      parentId: optionalNumber(unitForm.value.parentId),
      unitType: unitForm.value.unitType.trim() || 'department',
      sortOrder: numberOrZero(unitForm.value.sortOrder)
    };
    const created = await adminApi.createOrganizationUnit(payload);
    message.value = `组织单元已创建：${created.code}`;
    unitForm.value = { code: '', name: '', parentId: '', unitType: 'department', sortOrder: '0' };
    await loadDirectory();
  } catch (err) {
    error.value = (err as Error).message || '组织单元创建失败';
  } finally {
    savingUnit.value = false;
  }
}

async function submitUpdateUnit() {
  error.value = '';
  message.value = '';
  if (!updateUnitForm.value.unitId.trim() || !updateUnitForm.value.name.trim()) {
    error.value = '组织单元编号和名称必填';
    return;
  }
  updatingUnit.value = true;
  try {
    const unitId = numberOrString(updateUnitForm.value.unitId);
    const updated = await adminApi.updateOrganizationUnit(unitId, {
      name: updateUnitForm.value.name.trim(),
      parentId: optionalNumber(updateUnitForm.value.parentId),
      unitType: updateUnitForm.value.unitType.trim() || 'department',
      sortOrder: numberOrZero(updateUnitForm.value.sortOrder)
    });
    message.value = `组织单元已更新：${updated.code}`;
    updateUnitForm.value = { unitId: '', name: '', parentId: '', unitType: 'department', sortOrder: '0' };
    await loadDirectory();
  } catch (err) {
    error.value = (err as Error).message || '组织单元更新失败';
  } finally {
    updatingUnit.value = false;
  }
}

async function submitPosition() {
  error.value = '';
  message.value = '';
  if (!positionForm.value.organizationUnitId.trim() || !positionForm.value.code.trim() || !positionForm.value.name.trim()) {
    error.value = '岗位所属组织单元编号、岗位编码和岗位名称必填';
    return;
  }
  savingPosition.value = true;
  try {
    const payload = {
      organizationUnitId: numberOrString(positionForm.value.organizationUnitId),
      code: positionForm.value.code.trim(),
      name: positionForm.value.name.trim(),
      roles: positionForm.value.roles.split(',')
        .map((role) => role.trim())
        .filter(Boolean),
      managerUserId: optionalNumber(positionForm.value.managerUserId),
      sortOrder: numberOrZero(positionForm.value.sortOrder)
    };
    const created = await adminApi.createOrganizationPosition(payload);
    message.value = `岗位已创建：${created.code}`;
    positionForm.value = { organizationUnitId: '', code: '', name: '', roles: '', managerUserId: '', sortOrder: '0' };
    await loadDirectory();
  } catch (err) {
    error.value = (err as Error).message || '岗位创建失败';
  } finally {
    savingPosition.value = false;
  }
}

async function submitAssignment() {
  error.value = '';
  message.value = '';
  if (!assignmentForm.value.userId.trim() || !assignmentForm.value.positionId.trim()) {
    error.value = '用户编号和岗位编号必填';
    return;
  }
  savingAssignment.value = true;
  try {
    const payload = {
      userId: numberOrString(assignmentForm.value.userId),
      positionId: numberOrString(assignmentForm.value.positionId),
      primary: assignmentForm.value.primary,
      activeFrom: optionalDateTime(assignmentForm.value.activeFrom),
      activeTo: optionalDateTime(assignmentForm.value.activeTo)
    };
    const created = await adminApi.assignUserToOrganizationPosition(payload);
    message.value = `岗位分配已创建：用户 ${created.userId} -> 岗位 ${created.positionId}`;
    assignmentForm.value = { userId: '', positionId: '', activeFrom: '', activeTo: '', primary: false };
    await loadDirectory();
  } catch (err) {
    error.value = (err as Error).message || '用户岗位分配失败';
  } finally {
    savingAssignment.value = false;
  }
}

async function submitBatchAssignments() {
  error.value = '';
  message.value = '';
  const payload = parseBatchAssignments(batchAssignmentText.value);
  if (payload.assignments.length === 0) {
    error.value = '至少需要一行岗位分配数据';
    return;
  }
  batchImportingAssignments.value = true;
  try {
    const result = await adminApi.batchImportOrganizationPositionAssignments(payload);
    message.value = `岗位分配导入完成：成功 ${result.imported} 条，失败 ${result.failed} 条`;
    batchAssignmentText.value = '';
    await loadDirectory();
  } catch (err) {
    error.value = (err as Error).message || '岗位分配批量导入失败';
  } finally {
    batchImportingAssignments.value = false;
  }
}

async function submitUpdateAssignment() {
  error.value = '';
  message.value = '';
  if (!updateAssignmentForm.value.assignmentId.trim()) {
    error.value = '分配编号必填';
    return;
  }
  updatingAssignment.value = true;
  try {
    const assignmentId = numberOrString(updateAssignmentForm.value.assignmentId);
    const updated = await adminApi.updateOrganizationPositionAssignment(assignmentId, {
      primary: updateAssignmentForm.value.primary,
      activeFrom: optionalDateTime(updateAssignmentForm.value.activeFrom),
      activeTo: optionalDateTime(updateAssignmentForm.value.activeTo)
    });
    message.value = `岗位分配已更新：${updated.assignmentId}`;
    updateAssignmentForm.value = { assignmentId: '', activeFrom: '', activeTo: '', primary: false };
    await loadDirectory();
  } catch (err) {
    error.value = (err as Error).message || '岗位分配更新失败';
  } finally {
    updatingAssignment.value = false;
  }
}

async function submitDisableAssignment() {
  error.value = '';
  message.value = '';
  if (!disableAssignmentForm.value.assignmentId.trim()) {
    error.value = '分配编号必填';
    return;
  }
  disablingAssignment.value = true;
  try {
    const assignmentId = numberOrString(disableAssignmentForm.value.assignmentId);
    const disabled = await adminApi.disableOrganizationPositionAssignment(assignmentId, {
      reason: disableAssignmentForm.value.reason.trim() || undefined
    });
    message.value = `岗位分配已停用：${disabled.assignmentId}`;
    disableAssignmentForm.value = { assignmentId: '', reason: '' };
    await loadDirectory();
  } catch (err) {
    error.value = (err as Error).message || '岗位分配停用失败';
  } finally {
    disablingAssignment.value = false;
  }
}

function numberOrZero(value: string): number {
  const parsed = Number(value);
  return Number.isFinite(parsed) ? parsed : 0;
}

function numberOrString(value: string): number | string {
  const trimmed = value.trim();
  const parsed = Number(trimmed);
  return trimmed !== '' && Number.isFinite(parsed) ? parsed : trimmed;
}

function optionalNumber(value: string): number | null {
  const trimmed = value.trim();
  if (!trimmed) return null;
  const parsed = Number(trimmed);
  return Number.isFinite(parsed) ? parsed : null;
}

function optionalDateTime(value: string): string | undefined {
  const trimmed = value.trim();
  return trimmed ? new Date(trimmed).toISOString() : undefined;
}

function parseBatchAssignments(text: string) {
  return {
    assignments: text.split(/\r?\n/)
      .map((line) => line.trim())
      .filter(Boolean)
      .map((line) => {
        const [userId = '', positionId = '', primary = 'false', activeFrom = '', activeTo = ''] = line.split(',').map((part) => part.trim());
        return {
          userId: numberOrString(userId),
          positionId: numberOrString(positionId),
          primary: primary.toLowerCase() === 'true',
          activeFrom: optionalDateTime(activeFrom),
          activeTo: optionalDateTime(activeTo)
        };
      })
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

.forms {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(280px, 1fr));
  gap: 16px;
  margin: 14px 0;
}

.form-panel {
  display: grid;
  gap: 10px;
  padding: 16px;
  background: #ffffff;
  border: 1px solid #dcdfe6;
  border-radius: 6px;
}

.form-panel h3,
.tree-panel h3 {
  margin: 0;
  font-size: 16px;
}

.form-panel label {
  display: grid;
  gap: 6px;
  font-size: 13px;
  color: #4b5563;
}

.tree-panel {
  padding: 16px;
  background: #ffffff;
  border: 1px solid #dcdfe6;
  border-radius: 6px;
}

.tree-list,
.position-list,
.assignment-list {
  display: grid;
  gap: 8px;
  margin: 10px 0 0 18px;
  padding: 0;
}

.unit-node,
.position-row,
.assignment-row {
  list-style: none;
}

.unit-title {
  font-weight: 700;
  color: #1f2937;
}

.position-row {
  color: #4b5563;
}

.assignment-row {
  color: #1f2937;
}

.empty {
  color: #6b7280;
}
</style>
