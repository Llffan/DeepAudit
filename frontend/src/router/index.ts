import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router';

const routes: RouteRecordRaw[] = [
  {
    path: '/',
    name: 'landing',
    component: () => import('@/views/Landing.vue'),
  },
  {
    path: '/import',
    name: 'record-import',
    component: () => import('@/views/RecordImport.vue'),
  },
  // Phase 2 will register: { path: '/rules', name: 'rules', component: ... }
  // Phase 3 will register: { path: '/records/:id/confirm', name: 'record-confirm', ... }
  // Phase 4 will register: { path: '/records/:id/results', name: 'record-results', ... }
];

export default createRouter({
  history: createWebHistory(),
  routes,
});
