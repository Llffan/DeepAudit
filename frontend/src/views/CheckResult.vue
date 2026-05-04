<script setup lang="ts">
// T1.6 placeholder. Phase 4 (T4.2 / T4.3 / T4.5 / T4.6) wires the
// per-record results page with violation list + streaming LLM
// explanation (SSE) + false-positive marking. See plan §7.
</script>

<template>
  <section class="page">
    <header class="page-header">
      <h2>检查结果</h2>
      <p class="hint">阶段 4 实现：违规清单 + 流式 LLM 解释（SSE）+ 误报标记。</p>
    </header>

    <el-card shadow="never" class="placeholder-card">
      <el-empty description="占位页（T1.6） — 检查结果列表与流式解释将在阶段 4 实现">
        <template #image>
          <span class="placeholder-icon">🔍</span>
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
