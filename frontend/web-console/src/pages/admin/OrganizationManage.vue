<template>
  <section class="page">
    <header class="toolbar">
      <h2>Organization Management</h2>
      <el-button type="primary" :loading="loading" @click="loadDirectory">Refresh organization tree</el-button>
    </header>

    <el-alert v-if="error" :title="error" type="error" :closable="false" />
    <el-alert v-if="message" :title="message" type="success" :closable="false" />

    <section class="forms">
      <form class="form-panel" aria-label="Create organization unit form" @submit.prevent="submitUnit">
        <h3>Create Organization Unit</h3>
        <label>
          Code
          <el-input v-model="unitForm.code" aria-label="Organization unit code" />
        </label>
        <label>
          Name
          <el-input v-model="unitForm.name" aria-label="Organization unit name" />
        </label>
        <label>
          Parent ID
          <el-input v-model="unitForm.parentId" aria-label="Organization unit parent id" />
        </label>
        <label>
          Unit type
          <el-input v-model="unitForm.unitType" aria-label="Organization unit type" />
        </label>
        <label>
          Sort order
          <el-input v-model="unitForm.sortOrder" aria-label="Organization unit sort order" />
        </label>
        <el-button native-type="submit" type="primary" :loading="savingUnit">Create organization unit</el-button>
      </form>

      <form class="form-panel" aria-label="Update organization unit form" @submit.prevent="submitUpdateUnit">
        <h3>Update Organization Unit</h3>
        <label>
          Unit ID
          <el-input v-model="updateUnitForm.unitId" aria-label="Update organization unit id" />
        </label>
        <label>
          Name
          <el-input v-model="updateUnitForm.name" aria-label="Update organization unit name" />
        </label>
        <label>
          Parent ID
          <el-input v-model="updateUnitForm.parentId" aria-label="Update organization unit parent id" />
        </label>
        <label>
          Unit type
          <el-input v-model="updateUnitForm.unitType" aria-label="Update organization unit type" />
        </label>
        <label>
          Sort order
          <el-input v-model="updateUnitForm.sortOrder" aria-label="Update organization unit sort order" />
        </label>
        <el-button native-type="submit" type="primary" :loading="updatingUnit">Update organization unit</el-button>
      </form>

      <form class="form-panel" aria-label="Create organization position form" @submit.prevent="submitPosition">
        <h3>Create Organization Position</h3>
        <label>
          Organization unit ID
          <el-input v-model="positionForm.organizationUnitId" aria-label="Position organization unit id" />
        </label>
        <label>
          Code
          <el-input v-model="positionForm.code" aria-label="Position code" />
        </label>
        <label>
          Name
          <el-input v-model="positionForm.name" aria-label="Position name" />
        </label>
        <label>
          Roles
          <el-input v-model="positionForm.roles" aria-label="Position roles" />
        </label>
        <label>
          Manager user ID
          <el-input v-model="positionForm.managerUserId" aria-label="Position manager user id" />
        </label>
        <label>
          Sort order
          <el-input v-model="positionForm.sortOrder" aria-label="Position sort order" />
        </label>
        <el-button native-type="submit" type="primary" :loading="savingPosition">Create organization position</el-button>
      </form>

      <form class="form-panel" aria-label="Assign user to organization position form" @submit.prevent="submitAssignment">
        <h3>Assign User To Position</h3>
        <label>
          User
          <select v-model="assignmentForm.userId" aria-label="Assignment user selector">
            <option value="">Select user</option>
            <option v-for="user in users" :key="user.userId" :value="String(user.userId)">
              {{ user.displayName }} / {{ user.username }} / {{ user.department || '-' }} / {{ user.position || '-' }}
            </option>
          </select>
        </label>
        <label>
          User ID
          <el-input v-model="assignmentForm.userId" aria-label="Assignment user id" />
        </label>
        <label>
          Position
          <select v-model="assignmentForm.positionId" aria-label="Assignment position selector">
            <option value="">Select position</option>
            <option v-for="position in positionOptions" :key="position.positionId" :value="String(position.positionId)">
              {{ position.unitName }} / {{ position.code }} / {{ position.name }}
            </option>
          </select>
        </label>
        <label>
          Position ID
          <el-input v-model="assignmentForm.positionId" aria-label="Assignment position id" />
        </label>
        <label>
          Active from
          <el-input v-model="assignmentForm.activeFrom" type="datetime-local" aria-label="Assignment active from" />
        </label>
        <label>
          Active to
          <el-input v-model="assignmentForm.activeTo" type="datetime-local" aria-label="Assignment active to" />
        </label>
        <el-checkbox v-model="assignmentForm.primary" aria-label="Primary position assignment">Primary position</el-checkbox>
        <el-button native-type="submit" type="primary" :loading="savingAssignment">Assign user to position</el-button>
      </form>

      <form class="form-panel" aria-label="Batch import organization position assignments form" @submit.prevent="submitBatchAssignments">
        <h3>Batch Import Position Assignments</h3>
        <label>
          Assignments
          <el-input
            v-model="batchAssignmentText"
            aria-label="Batch import position assignments"
            type="textarea"
            :rows="5"
            placeholder="userId,positionId,primary,activeFrom,activeTo"
          />
        </label>
        <el-button native-type="submit" type="primary" :loading="batchImportingAssignments">Batch import position assignments</el-button>
      </form>

      <form class="form-panel" aria-label="Update organization position assignment form" @submit.prevent="submitUpdateAssignment">
        <h3>Update Position Assignment</h3>
        <label>
          Assignment ID
          <el-input v-model="updateAssignmentForm.assignmentId" aria-label="Update assignment id" />
        </label>
        <label>
          Active from
          <el-input v-model="updateAssignmentForm.activeFrom" type="datetime-local" aria-label="Update assignment active from" />
        </label>
        <label>
          Active to
          <el-input v-model="updateAssignmentForm.activeTo" type="datetime-local" aria-label="Update assignment active to" />
        </label>
        <el-checkbox v-model="updateAssignmentForm.primary" aria-label="Update primary position assignment">Primary position</el-checkbox>
        <el-button native-type="submit" type="primary" :loading="updatingAssignment">Update position assignment</el-button>
      </form>

      <form class="form-panel" aria-label="Disable organization position assignment form" @submit.prevent="submitDisableAssignment">
        <h3>Disable Position Assignment</h3>
        <label>
          Assignment ID
          <el-input v-model="disableAssignmentForm.assignmentId" aria-label="Disable assignment id" />
        </label>
        <label>
          Reason
          <el-input v-model="disableAssignmentForm.reason" aria-label="Disable assignment reason" />
        </label>
        <el-button native-type="submit" type="danger" :loading="disablingAssignment">Disable position assignment</el-button>
      </form>
    </section>

    <section class="tree-panel" aria-label="Organization tree">
      <h3>Organization Tree</h3>
      <p v-if="organizationTree.length === 0" class="empty">No organization units yet.</p>
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
        h('div', `${position.code} / ${position.name} / roles ${position.roles.join(', ')} / manager ${position.managerUserId ?? '-'}`),
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
    error.value = (err as Error).message || 'Failed to load organization directory';
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
    error.value = 'Organization unit code and name are required';
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
    message.value = `Organization unit created: ${created.code}`;
    unitForm.value = { code: '', name: '', parentId: '', unitType: 'department', sortOrder: '0' };
    await loadDirectory();
  } catch (err) {
    error.value = (err as Error).message || 'Failed to create organization unit';
  } finally {
    savingUnit.value = false;
  }
}

async function submitUpdateUnit() {
  error.value = '';
  message.value = '';
  if (!updateUnitForm.value.unitId.trim() || !updateUnitForm.value.name.trim()) {
    error.value = 'Organization unit id and name are required';
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
    message.value = `Organization unit updated: ${updated.code}`;
    updateUnitForm.value = { unitId: '', name: '', parentId: '', unitType: 'department', sortOrder: '0' };
    await loadDirectory();
  } catch (err) {
    error.value = (err as Error).message || 'Failed to update organization unit';
  } finally {
    updatingUnit.value = false;
  }
}

async function submitPosition() {
  error.value = '';
  message.value = '';
  if (!positionForm.value.organizationUnitId.trim() || !positionForm.value.code.trim() || !positionForm.value.name.trim()) {
    error.value = 'Position organization unit id, code and name are required';
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
    message.value = `Organization position created: ${created.code}`;
    positionForm.value = { organizationUnitId: '', code: '', name: '', roles: '', managerUserId: '', sortOrder: '0' };
    await loadDirectory();
  } catch (err) {
    error.value = (err as Error).message || 'Failed to create organization position';
  } finally {
    savingPosition.value = false;
  }
}

async function submitAssignment() {
  error.value = '';
  message.value = '';
  if (!assignmentForm.value.userId.trim() || !assignmentForm.value.positionId.trim()) {
    error.value = 'Assignment user id and position id are required';
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
    message.value = `Position assignment created: user ${created.userId} -> position ${created.positionId}`;
    assignmentForm.value = { userId: '', positionId: '', activeFrom: '', activeTo: '', primary: false };
    await loadDirectory();
  } catch (err) {
    error.value = (err as Error).message || 'Failed to assign user to position';
  } finally {
    savingAssignment.value = false;
  }
}

async function submitBatchAssignments() {
  error.value = '';
  message.value = '';
  const payload = parseBatchAssignments(batchAssignmentText.value);
  if (payload.assignments.length === 0) {
    error.value = 'At least one position assignment row is required';
    return;
  }
  batchImportingAssignments.value = true;
  try {
    const result = await adminApi.batchImportOrganizationPositionAssignments(payload);
    message.value = `Position assignments imported: ${result.imported} succeeded, ${result.failed} failed`;
    batchAssignmentText.value = '';
    await loadDirectory();
  } catch (err) {
    error.value = (err as Error).message || 'Failed to batch import position assignments';
  } finally {
    batchImportingAssignments.value = false;
  }
}

async function submitUpdateAssignment() {
  error.value = '';
  message.value = '';
  if (!updateAssignmentForm.value.assignmentId.trim()) {
    error.value = 'Assignment id is required';
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
    message.value = `Position assignment updated: ${updated.assignmentId}`;
    updateAssignmentForm.value = { assignmentId: '', activeFrom: '', activeTo: '', primary: false };
    await loadDirectory();
  } catch (err) {
    error.value = (err as Error).message || 'Failed to update position assignment';
  } finally {
    updatingAssignment.value = false;
  }
}

async function submitDisableAssignment() {
  error.value = '';
  message.value = '';
  if (!disableAssignmentForm.value.assignmentId.trim()) {
    error.value = 'Assignment id is required';
    return;
  }
  disablingAssignment.value = true;
  try {
    const assignmentId = numberOrString(disableAssignmentForm.value.assignmentId);
    const disabled = await adminApi.disableOrganizationPositionAssignment(assignmentId, {
      reason: disableAssignmentForm.value.reason.trim() || undefined
    });
    message.value = `Position assignment disabled: ${disabled.assignmentId}`;
    disableAssignmentForm.value = { assignmentId: '', reason: '' };
    await loadDirectory();
  } catch (err) {
    error.value = (err as Error).message || 'Failed to disable position assignment';
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
