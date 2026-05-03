<script setup lang="ts">
import { ref, onMounted } from 'vue';
import { useRouter } from 'vue-router';

const router = useRouter();
const backendStatus = ref<'unknown' | 'up' | 'down'>('unknown');
const backendError = ref<string | null>(null);

async function checkBackend() {
  backendStatus.value = 'unknown';
  backendError.value = null;
  try {
    const res = await fetch('/api/actuator/health', { headers: { Accept: 'application/json' } });
    if (!res.ok) throw new Error(`HTTP ${res.status}`);
    const body = await res.json();
    backendStatus.value = body?.status === 'UP' ? 'up' : 'down';
    if (backendStatus.value !== 'up') {
      backendError.value = JSON.stringify(body);
    }
  } catch (err) {
    backendStatus.value = 'down';
    backendError.value = err instanceof Error ? err.message : String(err);
  }
}

onMounted(checkBackend);
</script>

<template>
  <main class="landing">
    <h1>DeepAudit · 病案首页质控 MVP</h1>
    <p class="tagline">PDF 导入 → 规则配置 → 自动检查 → 结果展示</p>

    <nav class="entry-nav">
      <el-button type="primary" size="large" @click="router.push('/import')">
        进入病案录入
      </el-button>
    </nav>

    <section class="status">
      <h2>Backend Health</h2>
      <p>
        <span :class="['badge', backendStatus]">
          {{ backendStatus === 'up' ? 'UP' : backendStatus === 'down' ? 'DOWN' : '...' }}
        </span>
        <code>/api/actuator/health</code>
      </p>
      <p v-if="backendError" class="error">Last error: {{ backendError }}</p>
      <button @click="checkBackend">Re-check</button>
    </section>

    <footer>
      <small>Browser baseline: Chrome / Edge 最近两版（REQ-NFR-browser / C-env-002）。IE 不支持。</small>
    </footer>
  </main>
</template>

<style scoped>
.landing { max-width: 720px; margin: 4rem auto; padding: 0 1rem; }
.tagline { color: #666; }
.entry-nav { margin-top: 1.5rem; }
.status { margin-top: 2rem; padding: 1rem 1.5rem; border: 1px solid #eee; border-radius: 8px; }
.badge { display: inline-block; padding: 2px 10px; border-radius: 12px; font-size: 0.85em; margin-right: 0.5rem; }
.badge.up { background: #e6f7ec; color: #1f7a3a; }
.badge.down { background: #fdecea; color: #b3261e; }
.badge.unknown { background: #f1f3f4; color: #666; }
.error { color: #b3261e; font-family: monospace; font-size: 0.9em; }
button { margin-top: 0.5rem; }
footer { margin-top: 3rem; color: #999; }
</style>
