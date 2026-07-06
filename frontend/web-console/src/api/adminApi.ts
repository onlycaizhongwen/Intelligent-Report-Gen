import { apiClient } from './client';
import type { PageResponse } from './types';

export interface AdminUser {
  userId: number | string;
  username: string;
  displayName: string;
  department?: string;
  position?: string;
  status: 'enabled' | 'disabled' | string;
  roles: string[];
}

export interface BatchImportUsersRequest {
  users: Array<{
    username: string;
    displayName?: string;
    department?: string;
    position?: string;
    roles?: string[];
  }>;
}

export interface BatchImportUsersResult {
  imported: number;
  failed: number;
  items: Array<{
    userId?: number | string;
    username?: string;
    displayName?: string;
    department?: string;
    position?: string;
    roles?: string[];
    status: 'imported' | 'failed';
    reason?: string;
  }>;
}

export interface OrganizationDirectoryUser {
  userId: number | string;
  username: string;
  displayName: string;
  department?: string;
  position?: string;
  roles: string[];
}

export interface OrganizationDirectoryPosition {
  position: string;
  roles: string[];
  users: OrganizationDirectoryUser[];
}

export interface OrganizationDirectoryDepartment {
  department: string;
  positions: OrganizationDirectoryPosition[];
}

export interface OrganizationDirectoryRole {
  role: string;
  department: string;
  position: string;
  users: OrganizationDirectoryUser[];
}

export interface OrganizationDirectoryTreePosition {
  positionId: number | string;
  organizationUnitId: number | string;
  code: string;
  name: string;
  roles: string[];
  managerUserId?: number | string | null;
  users?: OrganizationDirectoryUser[];
}

export interface OrganizationDirectoryTreeNode {
  unitId: number | string;
  code: string;
  name: string;
  parentId?: number | string | null;
  unitType: string;
  positions: OrganizationDirectoryTreePosition[];
  children: OrganizationDirectoryTreeNode[];
}

export interface OrganizationDirectory {
  departments: OrganizationDirectoryDepartment[];
  roles: OrganizationDirectoryRole[];
  organizationTree?: OrganizationDirectoryTreeNode[];
}

export interface CreateOrganizationUnitRequest {
  code: string;
  name: string;
  parentId?: number | string | null;
  unitType?: string;
  sortOrder?: number;
}

export interface UpdateOrganizationUnitRequest {
  name?: string;
  parentId?: number | string | null;
  unitType?: string;
  sortOrder?: number;
}

export interface CreateOrganizationPositionRequest {
  organizationUnitId: number | string;
  code: string;
  name: string;
  roles?: string[];
  managerUserId?: number | string | null;
  sortOrder?: number;
}

export interface AssignUserToOrganizationPositionRequest {
  userId: number | string;
  positionId: number | string;
  primary?: boolean;
  activeFrom?: string;
  activeTo?: string;
}

export interface BatchImportOrganizationPositionAssignmentsRequest {
  assignments: AssignUserToOrganizationPositionRequest[];
}

export interface BatchImportOrganizationPositionAssignmentsResult {
  imported: number;
  failed: number;
  items: Array<Partial<OrganizationPositionAssignment> & {
    userId?: number | string | null;
    positionId?: number | string | null;
    status: 'imported' | 'failed' | string;
    reason?: string;
  }>;
}

export interface DisableOrganizationPositionAssignmentRequest {
  reason?: string;
}

export interface UpdateOrganizationPositionAssignmentRequest {
  primary?: boolean;
  activeFrom?: string;
  activeTo?: string;
}

export interface OrganizationPositionAssignment {
  assignmentId?: number | string | null;
  userId: number | string;
  positionId: number | string;
  primary: boolean;
  status: string;
  activeFrom?: string | null;
  activeTo?: string | null;
}

export const adminApi = {
  /** OpenSpec: permission-collaboration / REQ-AUTH-001 */
  listUsers: (params: { page: number; pageSize: number }) =>
    apiClient.get<unknown, PageResponse<AdminUser>>('/users', { params }),

  /** OpenSpec: permission-collaboration / REQ-AUTH-001 */
  batchImportUsers: (payload: BatchImportUsersRequest) =>
    apiClient.post<unknown, BatchImportUsersResult>('/users/batch-import', payload),

  /** OpenSpec: permission-collaboration / REQ-AUTH-001 */
  updateUserStatus: (userId: string | number, payload: { status: 'enabled' | 'disabled' }) =>
    apiClient.put<unknown, AdminUser>(`/users/${userId}/status`, payload),

  /** OpenSpec: permission-collaboration / REQ-AUTH-001 */
  permissionMatrix: () => apiClient.get('/roles/permission-matrix'),

  /** OpenSpec: permission-collaboration / REQ-AUTH-001 */
  organizationDirectory: () => apiClient.get<unknown, OrganizationDirectory>('/organization-directory'),

  /** OpenSpec: permission-collaboration / REQ-AUTH-001 */
  createOrganizationUnit: (payload: CreateOrganizationUnitRequest) =>
    apiClient.post<unknown, OrganizationDirectoryTreeNode>('/organization-units', payload),

  /** OpenSpec: permission-collaboration / REQ-AUTH-001 */
  updateOrganizationUnit: (unitId: number | string, payload: UpdateOrganizationUnitRequest) =>
    apiClient.put<unknown, OrganizationDirectoryTreeNode>(`/organization-units/${unitId}`, payload),

  /** OpenSpec: permission-collaboration / REQ-AUTH-001 */
  createOrganizationPosition: (payload: CreateOrganizationPositionRequest) =>
    apiClient.post<unknown, OrganizationDirectoryTreePosition>('/organization-positions', payload),

  /** OpenSpec: permission-collaboration / REQ-AUTH-001 */
  assignUserToOrganizationPosition: (payload: AssignUserToOrganizationPositionRequest) =>
    apiClient.post<unknown, OrganizationPositionAssignment>('/organization-position-assignments', payload),

  /** OpenSpec: permission-collaboration / REQ-AUTH-001 */
  batchImportOrganizationPositionAssignments: (payload: BatchImportOrganizationPositionAssignmentsRequest) =>
    apiClient.post<unknown, BatchImportOrganizationPositionAssignmentsResult>('/organization-position-assignments/batch-import', payload),

  /** OpenSpec: permission-collaboration / REQ-AUTH-001 */
  updateOrganizationPositionAssignment: (
    assignmentId: number | string,
    payload: UpdateOrganizationPositionAssignmentRequest
  ) => apiClient.put<unknown, OrganizationPositionAssignment>(`/organization-position-assignments/${assignmentId}`, payload),

  /** OpenSpec: permission-collaboration / REQ-AUTH-001 */
  disableOrganizationPositionAssignment: (
    assignmentId: number | string,
    payload: DisableOrganizationPositionAssignmentRequest = {}
  ) => apiClient.post<unknown, OrganizationPositionAssignment>(`/organization-position-assignments/${assignmentId}/disable`, payload),

  /** OpenSpec: audit-history-dashboard / REQ-AUDIT-001 */
  auditLogs: (params: { page: number; pageSize: number }) => apiClient.get('/audit-logs', { params })
};
