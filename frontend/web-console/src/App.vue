<template>
  <el-config-provider :locale="zhCn">
    <router-view v-if="isPublicLayout" />
    <el-container v-else class="app-shell">
      <el-aside width="220px" class="side-nav">
        <div class="brand">
          <span class="brand-dot" />
          <h1>智能报告生成系统</h1>
        </div>
        <el-menu
          router
          class="nav-menu"
          :default-openeds="['reports', 'management']"
          :default-active="$route.query.mode === 'template' ? '/reports/create?mode=template' : $route.path"
        >
          <el-sub-menu index="reports">
            <template #title>报告中心</template>
            <el-menu-item index="/reports/create">智能生成</el-menu-item>
            <el-menu-item index="/reports/create?mode=template">模板填报</el-menu-item>
            <el-menu-item index="/reports">我的报告</el-menu-item>
            <el-menu-item index="/history">历史记录</el-menu-item>
          </el-sub-menu>
          <el-sub-menu index="management">
            <template #title>管理中心</template>
            <el-menu-item index="/knowledge">知识库管理</el-menu-item>
            <el-menu-item index="/dashboard">工作台</el-menu-item>
            <el-menu-item index="/rules">规则引擎</el-menu-item>
            <el-menu-item index="/admin/users">权限协作</el-menu-item>
            <el-menu-item index="/reports/enterprise-export-templates">企业导出模板</el-menu-item>
            <el-menu-item index="/rules/approvals">审批待办</el-menu-item>
            <el-menu-item index="/rules/delegate-rules">审批委托</el-menu-item>
            <el-menu-item index="/rules/approval-templates">审批模板</el-menu-item>
            <el-menu-item index="/admin/organizations">组织管理</el-menu-item>
          </el-sub-menu>
        </el-menu>
        <div class="side-footer">
          <span class="avatar">管</span>
          <span>管理员</span>
        </div>
      </el-aside>
      <el-main>
        <router-view />
      </el-main>
    </el-container>
  </el-config-provider>
</template>

<script setup lang="ts">
import { computed } from 'vue';
import { useRoute } from 'vue-router';
import zhCn from 'element-plus/es/locale/lang/zh-cn';

const route = useRoute();
const isPublicLayout = computed(() => route.meta.layout === 'public');
</script>

<style scoped>
.app-shell {
  min-height: 100vh;
  background: #f0f2f5;
}

.side-nav {
  background: #ffffff;
  border-right: 1px solid #e8e8e8;
  display: flex;
  flex-direction: column;
}

.brand {
  height: 52px;
  padding: 0 16px;
  display: flex;
  align-items: center;
  gap: 8px;
  border-bottom: 1px solid #f0f0f0;
}

.brand-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: #1677ff;
  flex: 0 0 auto;
}

.brand h1 {
  margin: 0;
  color: #1677ff;
  font-size: 16px;
  font-weight: 700;
  line-height: 1.3;
}

.nav-menu {
  flex: 1;
  border-right: 0;
  padding: 8px 0;
}

.side-footer {
  height: 52px;
  padding: 0 16px;
  border-top: 1px solid #f0f0f0;
  display: flex;
  align-items: center;
  gap: 8px;
  color: #666;
  font-size: 13px;
}

.avatar {
  width: 28px;
  height: 28px;
  border-radius: 50%;
  background: #1677ff;
  color: #fff;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  font-size: 12px;
}

:deep(.el-sub-menu__title) {
  height: 40px;
  color: #333;
  font-weight: 600;
}

:deep(.el-menu-item) {
  height: 36px;
  color: #666;
  font-size: 13px;
}

:deep(.el-menu-item.is-active) {
  background: #e6f4ff;
  color: #1677ff;
  font-weight: 600;
}

:deep(.el-main) {
  padding: 0;
  overflow: hidden;
}
</style>
