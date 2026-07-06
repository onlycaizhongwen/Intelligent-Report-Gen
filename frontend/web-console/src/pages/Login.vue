<template>
  <section class="login">
    <h1>智能报告生成系统</h1>
    <p>统一身份认证由 Higress / OIDC 入口提供，业务系统读取当前用户与 RBAC 上下文。</p>
    <el-button type="primary" @click="redirectToOidc">进入统一登录</el-button>
  </section>
</template>

<script setup lang="ts">
const redirectToOidc = () => {
  const fallback = `/api/v1/auth/oidc/login?redirect_uri=${encodeURIComponent(window.location.origin)}`;
  window.location.href = import.meta.env.VITE_OIDC_LOGIN_URL || fallback;
};
</script>

<style scoped>
.login {
  max-width: 420px;
  margin: 15vh auto;
  display: grid;
  gap: 16px;
}
</style>
