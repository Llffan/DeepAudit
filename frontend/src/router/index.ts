import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router';

// Two top-level entries share the path prefix '/':
//   1. Landing (smoke-test splash with backend health probe) at '/'.
//   2. AppLayout (sidebar + header) wrapping the 3 core workflow pages.
//
// vue-router resolves '/' to the first match (Landing) and longer paths
// like '/rules' / '/import' / '/results' to children of the AppLayout
// route. Both definitions therefore coexist without ambiguity.
const routes: RouteRecordRaw[] = [
  {
    path: '/',
    name: 'landing',
    component: () => import('@/views/Landing.vue'),
  },
  {
    path: '/',
    component: () => import('@/layouts/AppLayout.vue'),
    children: [
      {
        path: 'rules',
        name: 'rules',
        component: () => import('@/views/RuleConfig.vue'),
      },
      {
        path: 'sandbox',
        name: 'rule-sandbox',
        component: () => import('@/views/RuleSandbox.vue'),
      },
      {
        path: 'records',
        name: 'record-list',
        component: () => import('@/views/RecordList.vue'),
      },
      {
        path: 'import',
        name: 'record-import',
        component: () => import('@/views/RecordImport.vue'),
      },
      {
        path: 'results',
        name: 'check-results',
        component: () => import('@/views/CheckResult.vue'),
      },
      {
        path: 'operators',
        name: 'operator-library',
        component: () => import('@/views/OperatorLibrary.vue'),
      },
    ],
  },
];

export default createRouter({
  history: createWebHistory(),
  routes,
});
