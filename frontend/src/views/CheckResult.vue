<script setup lang="ts">
import { computed, onMounted, onUnmounted, reactive, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { ElMessage, ElMessageBox } from 'element-plus';
import {
  ArrowLeft,
  Refresh,
  WarningFilled,
  CircleCheckFilled,
  Edit,
  ChatLineRound,
  CircleClose,
  VideoPlay,
} from '@element-plus/icons-vue';

interface CheckResultItem {
  id: number;
  ruleCode: string;
  ruleName: string;
  dimension: string;
  severity: string;
  fieldPath: string | null;
  fieldValue: string | null;
  hitMessage: string;
  status: 'open' | 'acknowledged' | 'false_positive';
  hasExplanation: boolean;
  createdAt: string;
}

interface SkippedRule {
  ruleCode: string;
  reason: string;
}

interface SummaryStats {
  totalRulesEvaluated: number;
  totalHits: number;
  byDimension: Record<string, number>;
  bySeverity: Record<string, number>;
}

interface CheckSummaryResponse {
  recordId: number;
  recordStatus: 'draft' | 'confirmed' | 'checked';
  checkedAt: string | null;
  summary: SummaryStats;
  results: CheckResultItem[];
  skippedRules: SkippedRule[];
}

const router = useRouter();
const route = useRoute();

const recordId = ref<number | null>(null);
const data = ref<CheckSummaryResponse | null>(null);
const loading = ref(false);
const rechecking = ref(false);
const errorMsg = ref<string | null>(null);

const DIMENSIONS = ['completeness', 'logic', 'standardization', 'consistency'] as const;
const DIMENSION_LABEL: Record<string, string> = {
  completeness: '完整性',
  logic: '逻辑性',
  standardization: '规范性',
  consistency: '一致性',
};
const SEVERITIES = ['mandatory', 'deduction', 'hint'] as const;
const SEVERITY_LABEL: Record<string, string> = {
  mandatory: '强制',
  deduction: '扣分',
  hint: '提示',
};
const SEVERITY_RANK: Record<string, number> = {
  mandatory: 0,
  deduction: 1,
  hint: 2,
};
const SEVERITY_TAG_TYPE: Record<string, 'danger' | 'warning' | 'info'> = {
  mandatory: 'danger',
  deduction: 'warning',
  hint: 'info',
};

function parseRouteId(raw: unknown): number | null {
  if (typeof raw !== 'string') return null;
  return /^\d+$/.test(raw) ? Number(raw) : null;
}

async function load(id: number) {
  loading.value = true;
  errorMsg.value = null;
  try {
    const res = await fetch(`/api/check-results/${id}`);
    if (res.status === 404) {
      ElMessage.warning(`病案 #${id} 不存在或已删除`);
      router.replace({ path: '/records' });
      return;
    }
    if (!res.ok) throw new Error(`HTTP ${res.status} ${res.statusText}`);
    data.value = (await res.json()) as CheckSummaryResponse;
    recordId.value = data.value.recordId;
  } catch (err) {
    errorMsg.value = err instanceof Error ? err.message : String(err);
    ElMessage.error('加载失败：' + errorMsg.value);
  } finally {
    loading.value = false;
  }
}

async function runCheck(id: number) {
  rechecking.value = true;
  errorMsg.value = null;
  try {
    const res = await fetch(`/api/medical-records/${id}/check`, { method: 'POST' });
    if (res.status === 404) {
      ElMessage.warning(`病案 #${id} 不存在或已删除`);
      router.replace({ path: '/records' });
      return;
    }
    if (!res.ok) throw new Error(`HTTP ${res.status} ${res.statusText}`);
    data.value = (await res.json()) as CheckSummaryResponse;
    recordId.value = data.value.recordId;
    ElMessage.success(
      data.value.summary.totalHits === 0
        ? '检查完成，全部通过'
        : `检查完成，命中 ${data.value.summary.totalHits} 条`,
    );
  } catch (err) {
    errorMsg.value = err instanceof Error ? err.message : String(err);
    ElMessage.error('检查失败：' + errorMsg.value);
  } finally {
    rechecking.value = false;
  }
}

async function confirmRecheck() {
  if (recordId.value == null) return;
  try {
    await ElMessageBox.confirm(
      '将清空本病案上一次的检查结果并重新跑全部启用规则。继续？',
      '重新检查',
      { type: 'warning', confirmButtonText: '重新检查', cancelButtonText: '取消' },
    );
  } catch {
    return;
  }
  await runCheck(recordId.value);
}

onMounted(() => {
  const id = parseRouteId(route.query.recordId);
  if (id !== null) void load(id);
});

watch(
  () => route.query.recordId,
  (next) => {
    const id = parseRouteId(next);
    if (id !== null && id !== recordId.value) void load(id);
  },
);

// ---- derived state ----------------------------------------------------------

const sortedResults = computed<CheckResultItem[]>(() => {
  if (!data.value) return [];
  return [...data.value.results].sort((a, b) => {
    const r = (SEVERITY_RANK[a.severity] ?? 9) - (SEVERITY_RANK[b.severity] ?? 9);
    if (r !== 0) return r;
    return new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime();
  });
});

// 4×3 KPI matrix from raw results — backend only sends row/col totals.
const matrix = computed(() => {
  const m: Record<string, Record<string, number>> = {};
  for (const d of DIMENSIONS) {
    m[d] = {};
    for (const s of SEVERITIES) m[d][s] = 0;
  }
  if (data.value) {
    for (const r of data.value.results) {
      if (m[r.dimension] && m[r.dimension][r.severity] != null) {
        m[r.dimension][r.severity] += 1;
      }
    }
  }
  return m;
});

const isUnchecked = computed(
  () =>
    data.value !== null &&
    data.value.recordStatus !== 'checked' &&
    data.value.results.length === 0,
);

const isAllPassed = computed(
  () =>
    data.value !== null &&
    data.value.recordStatus === 'checked' &&
    data.value.results.length === 0,
);

const passedCount = computed(() => {
  if (!data.value) return 0;
  return Math.max(
    0,
    data.value.summary.totalRulesEvaluated - data.value.summary.totalHits,
  );
});

// ---- SSE explanation state --------------------------------------------------

interface ExplainState {
  open: boolean;
  loading: boolean;
  text: string;
  error: string | null;
}

const explanations = reactive<Record<number, ExplainState>>({});
const esMap = new Map<number, EventSource>();

function ensureExplain(id: number): ExplainState {
  if (!explanations[id]) {
    explanations[id] = { open: false, loading: false, text: '', error: null };
  }
  return explanations[id];
}

function toggleExplain(id: number) {
  const s = ensureExplain(id);
  if (s.open) {
    s.open = false;
    return;
  }
  s.open = true;
  if (s.text || s.loading) return; // already fetched / fetching
  openExplain(id);
}

function openExplain(id: number) {
  const s = ensureExplain(id);
  s.loading = true;
  s.error = null;

  const es = new EventSource(`/api/check-results/${id}/explain`);
  esMap.set(id, es);

  es.addEventListener('cached', (e: MessageEvent) => {
    s.text = e.data as string;
    s.loading = false;
    es.close();
    esMap.delete(id);
    // update hasExplanation in results list
    markHasExplanation(id);
  });

  es.addEventListener('token', (e: MessageEvent) => {
    s.text += e.data as string;
  });

  es.addEventListener('done', () => {
    s.loading = false;
    es.close();
    esMap.delete(id);
    markHasExplanation(id);
  });

  es.addEventListener('error', (e: MessageEvent) => {
    s.loading = false;
    s.error = e.data ? String(e.data) : 'LLM 服务暂时不可用';
    es.close();
    esMap.delete(id);
  });

  // network-level error (e.g. server down)
  es.onerror = () => {
    if (s.loading) {
      s.loading = false;
      s.error = '连接中断，请稍后重试';
      es.close();
      esMap.delete(id);
    }
  };
}

function markHasExplanation(id: number) {
  if (!data.value) return;
  const hit = data.value.results.find((r) => r.id === id);
  if (hit) hit.hasExplanation = true;
}

// ---- status patch -----------------------------------------------------------

const statusPending = reactive<Set<number>>(new Set());

async function onPatchStatus(hit: CheckResultItem, newStatus: string) {
  if (statusPending.has(hit.id)) return;
  statusPending.add(hit.id);
  try {
    const res = await fetch(`/api/check-results/${hit.id}/status`, {
      method: 'PATCH',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ status: newStatus }),
    });
    if (!res.ok) throw new Error(`HTTP ${res.status}`);
    const updated = (await res.json()) as CheckResultItem;
    hit.status = updated.status;
  } catch (err) {
    ElMessage.error('状态更新失败：' + (err instanceof Error ? err.message : String(err)));
  } finally {
    statusPending.delete(hit.id);
  }
}

onUnmounted(() => {
  for (const es of esMap.values()) es.close();
  esMap.clear();
});

// -----------------------------------------------------------------------------

function gotoEdit() {
  if (recordId.value == null) return;
  router.push({ path: '/import', query: { id: String(recordId.value) } });
}

function gotoList() {
  router.push('/records');
}

function fmtTs(s: string | null) {
  if (!s) return '—';
  return new Date(s).toLocaleString('zh-CN', { hour12: false });
}
</script>

<template>
  <section class="page" v-loading="loading">
    <header class="page-header">
      <div class="title-row">
        <el-button :icon="ArrowLeft" link @click="gotoList">返回列表</el-button>
        <h2>
          检查结果
          <span v-if="recordId" class="record-pill">#{{ recordId }}</span>
        </h2>
      </div>
      <div class="header-actions">
        <el-button
          v-if="recordId"
          :icon="Edit"
          plain
          @click="gotoEdit"
        >
          回到字段
        </el-button>
        <el-button
          v-if="recordId && data && !isUnchecked"
          :icon="Refresh"
          type="primary"
          plain
          :loading="rechecking"
          @click="confirmRecheck"
        >
          重新检查
        </el-button>
      </div>
    </header>

    <el-alert
      v-if="errorMsg"
      type="error"
      :closable="false"
      :title="`后端错误：${errorMsg}`"
      class="banner"
    />

    <!-- recordId 缺失：用户直接访问 /results -->
    <el-card v-if="recordId === null" shadow="never" class="state-card">
      <el-empty description="未指定病案 ID。从“病案列表”进入或在 URL 加 ?recordId=X">
        <el-button type="primary" @click="gotoList">去病案列表</el-button>
      </el-empty>
    </el-card>

    <template v-else-if="data">
      <!-- 顶部信息条 -->
      <el-card shadow="never" class="meta-card">
        <div class="meta">
          <div>
            <span class="meta-label">病案</span>
            <span class="meta-value">#{{ data.recordId }}</span>
          </div>
          <div>
            <span class="meta-label">病案状态</span>
            <el-tag size="small" :type="data.recordStatus === 'checked' ? 'success' : 'info'">
              {{ data.recordStatus === 'checked' ? '已检查' : (data.recordStatus === 'confirmed' ? '已确认' : '草稿') }}
            </el-tag>
          </div>
          <div>
            <span class="meta-label">最近检查时间</span>
            <span class="meta-value">{{ fmtTs(data.checkedAt) }}</span>
          </div>
          <div class="totals">
            总命中
            <strong :class="{ ok: data.summary.totalHits === 0 }">
              {{ data.summary.totalHits }}
            </strong>
            <span class="dim">/ 通过 {{ passedCount }} / 评估 {{ data.summary.totalRulesEvaluated }}</span>
          </div>
        </div>
      </el-card>

      <!-- 跳过规则 alert -->
      <el-alert
        v-if="data.skippedRules.length > 0"
        type="warning"
        :closable="false"
        show-icon
        class="banner"
      >
        <template #title>
          {{ data.skippedRules.length }} 条规则因 DSL 异常被跳过 — 请到“规则配置”页修复
        </template>
        <ul class="skipped-list">
          <li v-for="r in data.skippedRules" :key="r.ruleCode">
            <strong>{{ r.ruleCode }}</strong>：{{ r.reason }}
          </li>
        </ul>
      </el-alert>

      <!-- 未检查 -->
      <el-card v-if="isUnchecked" shadow="never" class="state-card">
        <el-empty description="本病案尚未执行检查">
          <el-button
            type="primary"
            :icon="VideoPlay"
            :loading="rechecking"
            @click="recordId !== null && runCheck(recordId)"
          >
            立即检查
          </el-button>
        </el-empty>
      </el-card>

      <!-- 全通过 -->
      <el-card v-else-if="isAllPassed" shadow="never" class="state-card pass">
        <div class="pass-content">
          <el-icon class="pass-icon" :size="56" color="#1f7a3a"><CircleCheckFilled /></el-icon>
          <div>
            <h3>本次检查全部通过</h3>
            <p class="dim">在 {{ data.summary.totalRulesEvaluated }} 条启用规则中无命中</p>
          </div>
        </div>
      </el-card>

      <!-- 命中：KPI + 详情 -->
      <template v-else>
        <el-card shadow="never" class="kpi-card">
          <div class="kpi-head">质控维度 × 处置严重度</div>
          <table class="kpi-matrix">
            <thead>
              <tr>
                <th class="rh">维度 \ 严重度</th>
                <th v-for="s in SEVERITIES" :key="s">
                  <el-tag size="small" :type="SEVERITY_TAG_TYPE[s]" effect="plain">
                    {{ SEVERITY_LABEL[s] }}
                  </el-tag>
                </th>
                <th class="row-total">小计</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="d in DIMENSIONS" :key="d">
                <th class="rh">{{ DIMENSION_LABEL[d] }}</th>
                <td v-for="s in SEVERITIES" :key="s" :class="{ zero: matrix[d][s] === 0 }">
                  {{ matrix[d][s] }}
                </td>
                <td class="row-total">
                  {{ data.summary.byDimension[d] ?? 0 }}
                </td>
              </tr>
              <tr class="footer-row">
                <th class="rh">小计</th>
                <td v-for="s in SEVERITIES" :key="s">
                  {{ data.summary.bySeverity[s] ?? 0 }}
                </td>
                <td class="row-total">{{ data.summary.totalHits }}</td>
              </tr>
            </tbody>
          </table>
        </el-card>

        <h3 class="section-title">违规明细（按严重度排序）</h3>
        <div class="hits">
          <article
            v-for="hit in sortedResults"
            :key="hit.id"
            class="hit-card"
            :class="[`sev-${hit.severity}`, hit.status !== 'open' ? 'is-resolved' : '']"
          >
            <header class="hit-head">
              <el-icon class="hit-icon"><WarningFilled /></el-icon>
              <el-tag size="small" :type="SEVERITY_TAG_TYPE[hit.severity]">
                {{ SEVERITY_LABEL[hit.severity] }}
              </el-tag>
              <span class="dim sep">·</span>
              <span class="dim">{{ DIMENSION_LABEL[hit.dimension] }}</span>
              <span class="dim sep">·</span>
              <span class="rule-code">{{ hit.ruleCode }}</span>
              <span class="rule-name">{{ hit.ruleName }}</span>
              <span class="head-spacer" />
              <el-tag
                v-if="hit.status === 'acknowledged'"
                size="small"
                type="success"
                effect="plain"
              >已认可</el-tag>
              <el-tag
                v-else-if="hit.status === 'false_positive'"
                size="small"
                type="info"
                effect="plain"
              >误报</el-tag>
            </header>

            <p class="hit-message">{{ hit.hitMessage }}</p>

            <div v-if="hit.fieldPath" class="hit-field">
              <span class="dim">字段</span>
              <code>{{ hit.fieldPath }}</code>
              <span class="dim">当前值</span>
              <code :class="{ null: hit.fieldValue === null }">
                {{ hit.fieldValue === null ? 'null' : hit.fieldValue }}
              </code>
            </div>

            <footer class="hit-actions">
              <!-- 解释按钮 -->
              <el-button
                size="small"
                :icon="ChatLineRound"
                :loading="explanations[hit.id]?.loading && !explanations[hit.id]?.text"
                @click="toggleExplain(hit.id)"
              >
                {{
                  explanations[hit.id]?.open
                    ? '收起解释'
                    : hit.hasExplanation
                      ? '查看解释（已缓存）'
                      : '查看人话解释'
                }}
              </el-button>

              <el-button size="small" :icon="Edit" @click="gotoEdit">
                跳转到字段修正
              </el-button>

              <!-- 状态切换 -->
              <template v-if="hit.status === 'open'">
                <el-button
                  size="small"
                  type="success"
                  plain
                  :loading="statusPending.has(hit.id)"
                  @click="onPatchStatus(hit, 'acknowledged')"
                >认可</el-button>
                <el-button
                  size="small"
                  type="info"
                  plain
                  :icon="CircleClose"
                  :loading="statusPending.has(hit.id)"
                  @click="onPatchStatus(hit, 'false_positive')"
                >标记误报</el-button>
              </template>
              <template v-else>
                <el-button
                  size="small"
                  plain
                  :loading="statusPending.has(hit.id)"
                  @click="onPatchStatus(hit, 'open')"
                >{{ hit.status === 'acknowledged' ? '取消认可' : '取消误报' }}</el-button>
              </template>
            </footer>

            <!-- SSE 解释面板 -->
            <div v-if="explanations[hit.id]?.open" class="explain-panel">
              <div v-if="explanations[hit.id].error" class="explain-error">
                {{ explanations[hit.id].error }}
              </div>
              <div
                v-else-if="!explanations[hit.id].text && explanations[hit.id].loading"
                class="explain-loading"
              >
                <span class="dot" />
                <span class="dot" />
                <span class="dot" />
              </div>
              <p v-else class="explain-text">
                {{ explanations[hit.id].text
                }}<span v-if="explanations[hit.id].loading" class="cursor" />
              </p>
            </div>
          </article>
        </div>
      </template>
    </template>
  </section>
</template>

<style scoped>
.page {
  max-width: 1100px;
  margin: 0 auto;
  padding: 1.5rem;
}

.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 1rem;
  gap: 1rem;
}
.title-row {
  display: flex;
  align-items: center;
  gap: 0.75rem;
}
.title-row h2 {
  margin: 0;
  font-size: 1.25rem;
  color: #1f1f1f;
  display: inline-flex;
  align-items: center;
  gap: 0.5rem;
}
.record-pill {
  font-size: 0.85rem;
  color: #1677ff;
  background: #eaf3ff;
  padding: 2px 8px;
  border-radius: 999px;
}
.header-actions {
  display: flex;
  gap: 0.5rem;
}

.banner {
  margin-bottom: 1rem;
}
.skipped-list {
  margin: 0.4rem 0 0;
  padding-left: 1.2rem;
  font-size: 0.88rem;
  color: #5a3e00;
}

.state-card {
  background: #fff;
  padding: 2rem 1rem;
}
.state-card.pass {
  background: #f3faf5;
  border: 1px solid #cce8d5;
}
.pass-content {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 1.5rem;
  padding: 1.5rem 0;
}
.pass-content h3 {
  margin: 0 0 0.25rem;
  color: #1f7a3a;
  font-size: 1.2rem;
}

/* 顶部信息条 */
.meta-card {
  background: #fff;
  margin-bottom: 1rem;
}
.meta {
  display: flex;
  align-items: center;
  gap: 2.5rem;
  flex-wrap: wrap;
}
.meta > div {
  display: flex;
  align-items: center;
  gap: 0.5rem;
}
.meta-label {
  color: #888;
  font-size: 0.85rem;
}
.meta-value {
  color: #1f1f1f;
  font-weight: 500;
}
.totals {
  margin-left: auto;
  font-size: 0.95rem;
  color: #444;
}
.totals strong {
  color: #d4380d;
  font-size: 1.4rem;
  margin: 0 0.25rem;
}
.totals strong.ok {
  color: #1f7a3a;
}
.dim {
  color: #999;
  font-size: 0.85rem;
}

/* KPI 矩阵 */
.kpi-card {
  background: #fff;
  margin-bottom: 1rem;
}
.kpi-head {
  font-size: 0.95rem;
  color: #555;
  margin-bottom: 0.5rem;
}
.kpi-matrix {
  width: 100%;
  border-collapse: collapse;
  font-size: 0.92rem;
}
.kpi-matrix th,
.kpi-matrix td {
  padding: 0.5rem 0.75rem;
  text-align: center;
  border: 1px solid #eee;
}
.kpi-matrix th.rh {
  background: #fafafa;
  text-align: left;
  width: 130px;
  color: #555;
}
.kpi-matrix td.zero {
  color: #c0c4cc;
}
.kpi-matrix td.row-total,
.kpi-matrix th.row-total {
  background: #fafafa;
  font-weight: 600;
}
.kpi-matrix tr.footer-row th,
.kpi-matrix tr.footer-row td {
  background: #f7f9fc;
  font-weight: 600;
}

/* 命中卡片 */
.section-title {
  margin: 1.25rem 0 0.75rem;
  font-size: 1rem;
  color: #1f1f1f;
}
.hits {
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
}
.hit-card {
  background: #fff;
  border-left: 4px solid #ddd;
  border-radius: 4px;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.04);
  padding: 1rem 1.25rem;
}
.hit-card.sev-mandatory { border-left-color: #f5222d; }
.hit-card.sev-deduction { border-left-color: #fa8c16; }
.hit-card.sev-hint      { border-left-color: #409eff; }

.hit-head {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  margin-bottom: 0.5rem;
  font-size: 0.9rem;
}
.hit-icon {
  color: #faad14;
}
.hit-card.sev-mandatory .hit-icon { color: #f5222d; }
.hit-card.sev-hint      .hit-icon { color: #409eff; }
.sep { margin: 0 0.1rem; }
.rule-code {
  color: #1677ff;
  font-weight: 600;
  font-family: 'JetBrains Mono', Consolas, monospace;
}
.rule-name {
  color: #444;
}
.hit-message {
  margin: 0.25rem 0 0.6rem;
  color: #1f1f1f;
  line-height: 1.5;
}
.hit-field {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  margin-bottom: 0.6rem;
  font-size: 0.85rem;
}
.hit-field code {
  background: #f5f7fa;
  padding: 1px 6px;
  border-radius: 3px;
  font-family: 'JetBrains Mono', Consolas, monospace;
  color: #444;
}
.hit-field code.null {
  color: #d4380d;
}
.hit-actions {
  display: flex;
  gap: 0.5rem;
  flex-wrap: wrap;
}

/* resolved dimming */
.hit-card.is-resolved {
  opacity: 0.65;
}

/* header spacer pushes status tag to right */
.head-spacer {
  flex: 1;
}

/* SSE explanation panel */
.explain-panel {
  margin-top: 0.75rem;
  padding: 0.75rem 1rem;
  background: #f7f9fc;
  border-radius: 4px;
  font-size: 0.9rem;
  line-height: 1.7;
  color: #333;
  border: 1px solid #e6eaf0;
}
.explain-error {
  color: #d4380d;
}
.explain-loading {
  display: flex;
  gap: 5px;
  align-items: center;
  padding: 0.25rem 0;
}
.explain-loading .dot {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: #1677ff;
  animation: dot-bounce 1.2s infinite ease-in-out both;
}
.explain-loading .dot:nth-child(2) { animation-delay: 0.2s; }
.explain-loading .dot:nth-child(3) { animation-delay: 0.4s; }
@keyframes dot-bounce {
  0%, 80%, 100% { transform: scale(0.6); opacity: 0.4; }
  40%            { transform: scale(1);   opacity: 1;   }
}
.explain-text {
  margin: 0;
  white-space: pre-wrap;
}
.cursor {
  display: inline-block;
  width: 2px;
  height: 1em;
  background: #1677ff;
  margin-left: 2px;
  vertical-align: text-bottom;
  animation: blink 0.8s step-end infinite;
}
@keyframes blink {
  0%, 100% { opacity: 1; }
  50%       { opacity: 0; }
}
</style>
