import { apiClient } from './client';

export interface CurrentUserProfile {
  userId: number;
  displayName: string;
  roles: string[];
  permissions: string[];
  status: string;
  authProvider: string;
}

export const authApi = {
  /** OpenSpec: permission-collaboration / REQ-AUTH-001 */
  me: () => apiClient.get<unknown, CurrentUserProfile>('/auth/me')
};
