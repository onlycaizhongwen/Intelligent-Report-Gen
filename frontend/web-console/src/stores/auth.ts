import { defineStore } from 'pinia';
import { ref } from 'vue';
import { authApi } from '../api/authApi';

export const useAuthStore = defineStore('auth', () => {
  const userId = ref<number | null>(null);
  const displayName = ref('');
  const roles = ref<string[]>([]);
  const permissions = ref<string[]>([]);

  const hasPermission = (permission: string) => permissions.value.includes(permission);

  const loadCurrentUser = async () => {
    const profile = await authApi.me();
    userId.value = profile.userId;
    displayName.value = profile.displayName;
    roles.value = profile.roles;
    permissions.value = profile.permissions;
    return profile;
  };

  return { userId, displayName, roles, permissions, hasPermission, loadCurrentUser };
});
