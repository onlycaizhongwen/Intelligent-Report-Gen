import { createRouter, createWebHistory } from 'vue-router';

export const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/', redirect: '/dashboard' },
    { path: '/login', component: () => import('../pages/Login.vue') },
    { path: '/dashboard', component: () => import('../pages/Dashboard.vue') },
    { path: '/reports/create', component: () => import('../pages/reports/ReportCreate.vue') },
    { path: '/reports/enterprise-export-templates', component: () => import('../pages/reports/EnterpriseExportTemplates.vue') },
    { path: '/reports/:id/outline', component: () => import('../pages/reports/ReportOutline.vue') },
    { path: '/reports/:id', component: () => import('../pages/reports/ReportDetail.vue') },
    { path: '/knowledge', component: () => import('../pages/knowledge/KnowledgeList.vue') },
    { path: '/knowledge/upload', component: () => import('../pages/knowledge/KnowledgeUpload.vue') },
    { path: '/knowledge/data-sources', component: () => import('../pages/knowledge/DataSourceConfig.vue') },
    { path: '/rules', component: () => import('../pages/rules/RuleDesigner.vue') },
    { path: '/rules/approvals', component: () => import('../pages/rules/ApprovalInbox.vue') },
    { path: '/rules/delegate-rules', component: () => import('../pages/rules/ApprovalDelegateRules.vue') },
    { path: '/rules/approval-templates', component: () => import('../pages/rules/ApprovalTemplates.vue') },
    { path: '/audit', component: () => import('../pages/audit/AuditDashboard.vue') },
    { path: '/admin/users', component: () => import('../pages/admin/UserManage.vue') },
    { path: '/admin/organizations', component: () => import('../pages/admin/OrganizationManage.vue') },
    { path: '/share/:token', component: () => import('../pages/share/ShareAccess.vue') }
  ]
});
