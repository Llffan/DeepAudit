<script setup lang="ts">
// T1.6 placeholder. Phase 2 (T2.3 / T2.5 / T2.6) wires CRUD list, edit
// drawer with 4 tabs (basic / DSL / AI-write / sandbox), enable toggle,
// delete-when-disabled guard, and AI-NL-to-DSL panel. See plan §5.
</script>

<template>
  <section class="page">
    <header class="page-header">
      <h2>规则配置</h2>
      <p class="hint">阶段 2 实现：列表 / 新增 / 修改 / 启停切换 / 删除拦截 / AI 写规则 / 测试沙盒。</p>
    </header>

    <el-card shadow="never" class="placeholder-card">
      <el-empty description="占位页（T1.6） — 规则列表与编辑抽屉将在阶段 2 实现">
        <template #image>
          <span class="placeholder-icon">📋</span>
        </template>
      </el-empty>
    </el-card>
  </section>
</template>

<style scoped>
.page { padding: 1.5rem; }
.page-header { margin-bottom: 1rem; }
.page-header h2 { margin: 0 0 0.35rem; font-size: 1.25rem; color: #1f1f1f; }
.hint { color: #888; font-size: 0.9rem; margin: 0; }
.placeholder-card { background: #fff; }
.placeholder-icon { font-size: 4rem; opacity: 0.6; }
</style>
