<script setup lang="ts">
import { ref, watch, onMounted } from 'vue';
import { useRoute } from 'vue-router';
import { ElMessage } from 'element-plus';
import { CircleCheck, CircleClose, Warning, Tools } from '@element-plus/icons-vue';

// ---------- preset DSLs ----------

interface DslPreset { code: string; label: string; dsl: string; }

const DSL_PRESETS: DslPreset[] = [
  {
    code: 'R002',
    label: 'R002 入出院日期与住院天数逻辑',
    dsl: JSON.stringify({
      op: 'and',
      args: [
        { op: 'dateBefore', field: 'admissionDate', rhs: { field: 'dischargeDate' } },
        {
          op: 'eq',
          field: 'lengthOfStay',
          rhs: { op: 'dateDiffDays', from: 'admissionDate', to: 'dischargeDate' },
        },
      ],
    }, null, 2),
  },
  {
    code: 'V9-PATHOLOGY',
    label: 'V9 病理诊断 + 编码同时存在',
    dsl: JSON.stringify({
      when:   { op: 'notNull', field: 'pathologicalDiagnosis' },
      assert: { op: 'notNull', field: 'pathologicalDiagnosisCode' },
    }, null, 2),
  },
  {
    code: 'CUSTOM',
    label: '自定义 / 留空',
    dsl: '',
  },
];

// ---------- preset records ----------

interface RecordPreset { id: string; label: string; record: Record<string, unknown>; }

const FULL_VALID_RECORD = {
  recordNo:                  'PA20260401-001',
  name:                      '张三',
  gender:                    '男',
  age:                       45,
  admissionDate:             '2026-04-01',
  dischargeDate:             '2026-04-08',
  lengthOfStay:              7,
  admissionDept:             '外科',
  dischargeDept:             '外科',
  mainDiagnosisCode:         'N20.0',
  mainDiagnosisName:         '肾结石',
  // V9 supplementary 示例
  pathologicalDiagnosis:     '肾结石伴慢性肾盂肾炎',
  pathologicalDiagnosisCode: 'N20.0',
  pathologyNumber:           'P20260402-017',
  bloodType:                 'A',
  rhBloodType:               '阳',
  drugAllergy:               '无',
  recordQuality:             '甲',
};

const RECORD_PRESETS: RecordPreset[] = [
  {
    id: 'all-pass',
    label: '全字段正确（R002 + V9 病理示例都通过）',
    record: { ...FULL_VALID_RECORD },
  },
  {
    id: 'r002-hit-length',
    label: 'R002 命中：住院天数 ≠ 实际差',
    record: { ...FULL_VALID_RECORD, lengthOfStay: 10 },
  },
  {
    id: 'r002-hit-inverted',
    label: 'R002 命中：出院日期早于入院日期',
    record: { ...FULL_VALID_RECORD, admissionDate: '2026-04-08', dischargeDate: '2026-04-01' },
  },
  {
    id: 'v9-pathology-hit',
    label: 'V9 病理示例命中：有诊断名但缺编码',
    record: { ...FULL_VALID_RECORD, pathologicalDiagnosisCode: null },
  },
  {
    id: 'empty',
    label: '空记录（验证 null 防御）',
    record: {},
  },
];

// ---------- state ----------

const route = useRoute();

const dslPresetCode = ref<string>('R002');
const recordPresetId = ref<string>('all-pass');

const dslText = ref<string>(DSL_PRESETS[0].dsl);
const recordText = ref<string>(JSON.stringify(RECORD_PRESETS[0].record, null, 2));

const loading = ref(false);
const dialogOpen = ref(false);

interface DryRunResponse {
  status: 'satisfied' | 'hit' | 'invalid' | 'error';
  satisfied: boolean | null;
  validationErrors: string[];
  evaluationError: string | null;
}
const result = ref<DryRunResponse | null>(null);

// ---------- preset sync ----------

watch(dslPresetCode, (code) => {
  if (code === 'CUSTOM') return;  // keep whatever's in the editor
  const found = DSL_PRESETS.find((p) => p.code === code);
  if (found) dslText.value = found.dsl;
});

watch(recordPresetId, (id) => {
  const found = RECORD_PRESETS.find((p) => p.id === id);
  if (found) recordText.value = JSON.stringify(found.record, null, 2);
});

// Honor ?ruleCode=R001 from RuleConfig deep-link
onMounted(() => {
  const code = route.query.ruleCode;
  if (typeof code === 'string') {
    const preset = DSL_PRESETS.find((p) => p.code === code);
    if (preset) dslPresetCode.value = code;
  }
});

// ---------- dry-run call ----------

async function runDryRun() {
  let expression: unknown;
  let record: unknown;
  try {
    expression = JSON.parse(dslText.value);
  } catch {
    ElMessage.error('DSL 不是合法的 JSON');
    return;
  }
  try {
    record = recordText.value.trim() ? JSON.parse(recordText.value) : {};
  } catch {
    ElMessage.error('病案数据不是合法的 JSON');
    return;
  }

  loading.value = true;
  try {
    const res = await fetch('/api/rules/dry-run', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ expression, record }),
    });
    if (!res.ok) {
      ElMessage.error(`后端请求失败：HTTP ${res.status}`);
      return;
    }
    result.value = await res.json();
    dialogOpen.value = true;
  } catch (err) {
    ElMessage.error('网络错误：' + (err instanceof Error ? err.message : String(err)));
  } finally {
    loading.value = false;
  }
}

// ---------- result-dialog appearance ----------

interface ResultMeta { icon: unknown; color: string; title: string; subtitle: string; }

function metaFor(r: DryRunResponse): ResultMeta {
  switch (r.status) {
    case 'satisfied':
      return { icon: CircleCheck, color: '#1f7a3a', title: '规则满足',
               subtitle: '该病案在此规则下没有违规命中。' };
    case 'hit':
      return { icon: CircleClose, color: '#b3261e', title: '规则命中',
               subtitle: '该病案触发了此规则 — 后续会写入 check_result。' };
    case 'invalid':
      return { icon: Warning, color: '#d97706', title: 'DSL 校验失败',
               subtitle: `${r.validationErrors.length} 项结构错误，规则无法持久化。` };
    case 'error':
      return { icon: Tools, color: '#7c2d12', title: '求值时异常',
               subtitle: '校验通过但 evaluator 抛出 — 类型不匹配等运行时错误。' };
  }
}
</script>

<template>
  <section class="page">
    <header class="page-header">
      <h2>规则沙盒</h2>
      <p class="hint">
        加载预置 DSL + 病案数据 → 调 <code>POST /api/rules/dry-run</code> → 看求值结果。
        不持久化任何数据，可用作 T2.1/T2.2 的端到端冒烟测试。
      </p>
    </header>

    <div class="grid">
      <el-card class="col" shadow="never">
        <template #header>
          <div class="col-header">
            <span class="col-title">规则 DSL</span>
            <el-select v-model="dslPresetCode" size="small" style="width: 240px">
              <el-option
                v-for="p in DSL_PRESETS"
                :key="p.code"
                :label="p.label"
                :value="p.code"
              />
            </el-select>
          </div>
        </template>
        <el-input
          v-model="dslText"
          type="textarea"
          :rows="22"
          resize="none"
          spellcheck="false"
          class="json-editor"
        />
      </el-card>

      <el-card class="col" shadow="never">
        <template #header>
          <div class="col-header">
            <span class="col-title">病案数据</span>
            <el-select v-model="recordPresetId" size="small" style="width: 240px">
              <el-option
                v-for="p in RECORD_PRESETS"
                :key="p.id"
                :label="p.label"
                :value="p.id"
              />
            </el-select>
          </div>
        </template>
        <el-input
          v-model="recordText"
          type="textarea"
          :rows="22"
          resize="none"
          spellcheck="false"
          class="json-editor"
        />
      </el-card>
    </div>

    <div class="actions">
      <el-button
        type="primary"
        size="large"
        :loading="loading"
        @click="runDryRun"
      >
        求值（dry-run）
      </el-button>
      <span class="actions-hint">
        快捷选择上方预置组合可在 1 秒内复现 R001/R002 的命中与通过状态。
      </span>
    </div>

    <el-dialog
      v-model="dialogOpen"
      title="求值结果"
      width="640"
      align-center
      destroy-on-close
    >
      <template v-if="result">
        <div class="result">
          <el-icon :size="56" :style="{ color: metaFor(result).color }">
            <component :is="metaFor(result).icon" />
          </el-icon>
          <h3 :style="{ color: metaFor(result).color }">{{ metaFor(result).title }}</h3>
          <p class="result-subtitle">{{ metaFor(result).subtitle }}</p>

          <el-divider v-if="result.status !== 'satisfied' && result.status !== 'hit'" />

          <div v-if="result.status === 'invalid'" class="error-list">
            <h4>校验错误（来自 T2.2 RuleDslValidator）</h4>
            <ul>
              <li v-for="(err, i) in result.validationErrors" :key="i">
                <code>{{ err }}</code>
              </li>
            </ul>
          </div>

          <div v-if="result.status === 'error'" class="error-list">
            <h4>运行时异常（来自 T2.1 RuleEvaluator）</h4>
            <pre><code>{{ result.evaluationError }}</code></pre>
          </div>
        </div>
      </template>

      <template #footer>
        <el-button @click="dialogOpen = false">关闭</el-button>
      </template>
    </el-dialog>
  </section>
</template>

<style scoped>
.page { max-width: 1100px; margin: 0 auto; padding: 1.5rem; }

.page-header { margin-bottom: 1rem; }
.page-header h2 { margin: 0 0 0.35rem; font-size: 1.25rem; color: #1f1f1f; }
.hint { color: #888; font-size: 0.9rem; margin: 0; line-height: 1.6; }
.hint code {
  background: #f1f3f4; padding: 1px 6px; border-radius: 4px;
  font-family: 'SFMono-Regular', Consolas, monospace; font-size: 0.85em;
}

.grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 1rem;
  margin-bottom: 1rem;
}
.col { background: #fff; }
.col-header {
  display: flex; align-items: center; justify-content: space-between;
}
.col-title { font-weight: 600; }

.json-editor :deep(.el-textarea__inner) {
  font-family: 'SFMono-Regular', Consolas, 'Liberation Mono', monospace;
  font-size: 13px;
  line-height: 1.5;
  background: #fafbfc;
}

.actions {
  display: flex; align-items: center; gap: 1rem;
  padding: 1rem; background: #fff; border-radius: 8px;
}
.actions-hint { color: #888; font-size: 0.85rem; }

/* result dialog body */
.result { text-align: center; padding: 0.5rem 0.5rem 0.25rem; }
.result h3 { margin: 0.5rem 0 0.25rem; font-size: 1.4rem; }
.result-subtitle { color: #555; margin: 0; }
.error-list {
  text-align: left;
  background: #fafbfc;
  border-radius: 6px;
  padding: 0.75rem 1rem;
  margin-top: 0.5rem;
}
.error-list h4 { margin: 0 0 0.5rem; font-size: 0.9rem; color: #444; }
.error-list ul { margin: 0; padding-left: 1.2rem; }
.error-list li { margin: 0.25rem 0; }
.error-list code, .error-list pre {
  font-family: 'SFMono-Regular', Consolas, monospace;
  font-size: 0.85rem;
  color: #b3261e;
}
.error-list pre { margin: 0; white-space: pre-wrap; word-break: break-word; }
</style>
