<template>
  <el-container class="app-shell">
    <el-aside width="232px" class="side-nav">
      <h1>智能报告</h1>
      <el-menu router :default-active="$route.path">
        <el-menu-item index="/dashboard">工作台</el-menu-item>
        <el-menu-item index="/reports/create">报告生成</el-menu-item>
        <el-menu-item index="/reports/enterprise-export-templates">Enterprise export templates</el-menu-item>
        <el-menu-item index="/knowledge">知识库</el-menu-item>
        <el-menu-item index="/knowledge/upload">文档上传</el-menu-item>
        <el-menu-item index="/rules">规则编排</el-menu-item>
        <el-menu-item index="/rules/approvals">审批待办</el-menu-item>
        <el-menu-item index="/rules/delegate-rules">Approval delegates</el-menu-item>
        <el-menu-item index="/rules/approval-templates">Approval templates</el-menu-item>
        <el-menu-item index="/audit">审计</el-menu-item>
        <el-menu-item index="/admin/users">用户权限</el-menu-item>
        <el-menu-item index="/admin/organizations">Organization management</el-menu-item>
      </el-menu>
    </el-aside>
    <el-main>
      <router-view />
    </el-main>
  </el-container>
</template>

<style scoped>
.app-shell {
  min-height: 100vh;
  background: #f7f8fa;
}

.side-nav {
  background: #ffffff;
  border-right: 1px solid #dcdfe6;
}

.side-nav h1 {
  margin: 20px 18px;
  font-size: 20px;
  font-weight: 700;
}
</style>
