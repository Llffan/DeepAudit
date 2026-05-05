<script setup lang="ts">
import { computed } from 'vue';
import { useRoute } from 'vue-router';
import { Document, Upload, DataLine, MagicStick, Files, Cpu } from '@element-plus/icons-vue';

const route = useRoute();
const activeIndex = computed(() => route.path);

const navItems = [
  { index: '/rules',     icon: Document,   label: '规则配置' },
  { index: '/operators', icon: Cpu,        label: '算子库' },
  { index: '/sandbox',   icon: MagicStick, label: '规则沙盒' },
  { index: '/import',    icon: Upload,     label: '病案导入' },
  { index: '/records',   icon: Files,      label: '病案列表' },
  { index: '/results',   icon: DataLine,   label: '检查结果' },
];
</script>

<template>
  <el-container class="app-layout">
    <el-aside width="220px" class="aside">
      <div class="brand">
        <span class="brand-name">DeepAudit</span>
        <span class="brand-sub">病案首页质控 MVP</span>
      </div>
      <el-menu :default-active="activeIndex" router class="aside-menu">
        <el-menu-item v-for="item in navItems" :key="item.index" :index="item.index">
          <el-icon><component :is="item.icon" /></el-icon>
          <span>{{ item.label }}</span>
        </el-menu-item>
      </el-menu>
    </el-aside>

    <el-container>
      <el-header class="header">
        <span class="header-title">{{ navItems.find((i) => i.index === route.path)?.label || '工作台' }}</span>
        <router-link to="/" class="health-link">
          <el-icon><Odometer /></el-icon>
          <span>健康检查</span>
        </router-link>
      </el-header>
      <el-main class="main">
        <router-view />
      </el-main>
    </el-container>
  </el-container>
</template>

<style scoped>
.app-layout {
  height: 100vh;
}

.aside {
  background: #001529;
  color: #fff;
  display: flex;
  flex-direction: column;
}

.brand {
  padding: 1.25rem 1rem;
  border-bottom: 1px solid rgba(255, 255, 255, 0.08);
  display: flex;
  flex-direction: column;
  gap: 2px;
}
.brand-name { font-size: 1.05rem; font-weight: 700; color: #fff; letter-spacing: 0.5px; }
.brand-sub  { font-size: 0.75rem; color: rgba(255, 255, 255, 0.55); }

.aside-menu { flex: 1; }
.aside :deep(.el-menu) {
  background: transparent;
  border-right: none;
}
.aside :deep(.el-menu-item) {
  color: rgba(255, 255, 255, 0.75);
}
.aside :deep(.el-menu-item:hover) {
  background: rgba(255, 255, 255, 0.06);
  color: #fff;
}
.aside :deep(.el-menu-item.is-active) {
  background: #1677ff;
  color: #fff;
}
.aside :deep(.el-menu-item .el-icon) {
  color: inherit;
}

.header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  background: #fff;
  border-bottom: 1px solid #ececec;
  padding: 0 1.5rem;
}
.header-title {
  font-size: 1.05rem;
  font-weight: 600;
  color: #1f1f1f;
}
.health-link {
  color: #666;
  text-decoration: none;
  font-size: 0.9rem;
  display: inline-flex;
  align-items: center;
  gap: 0.35rem;
}
.health-link:hover { color: #1677ff; }

.main {
  background: #f5f6f8;
  padding: 0;
}
</style>
