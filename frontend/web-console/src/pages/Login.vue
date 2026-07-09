<template>
  <section class="login">
    <h1>智能报告生成系统</h1>
    <p>统一身份认证由 Higress / OIDC 入口提供，业务系统读取当前用户与 RBAC 上下文。</p>
    <el-button type="primary" @click="redirectToOidc">进入统一登录</el-button>
  </section>
</template>

<script setup lang="ts">
import { useRouter } from 'vue-router';

const router = useRouter();

type DevLoginResponse = {
  code: number;
  data?: {
    accessToken?: string;
  };
};

const redirectToOidc = async () => {
  const oidcLoginUrl = import.meta.env.VITE_OIDC_LOGIN_URL;
  if (oidcLoginUrl) {
    window.location.href = oidcLoginUrl;
    return;
  }

  const devToken = await requestDevToken();
  window.localStorage.setItem('accessToken', devToken || 'local-preview-token');
  window.localStorage.setItem('authMode', devToken ? 'local-dev' : 'local-preview');
  await router.push('/reports/create');
};

async function requestDevToken() {
  try {
    const response = await fetch('/api/v1/auth/dev-login', { method: 'POST' });
    if (!response.ok) return '';
    const body = await response.json() as DevLoginResponse;
    return body.code === 200 ? body.data?.accessToken ?? '' : '';
  } catch {
    return '';
  }
}
</script>

<style scoped>
.login {
  max-width: 420px;
  margin: 15vh auto;
  display: grid;
  gap: 16px;
}
</style>
