<script setup lang="ts">
import { ref, onMounted } from 'vue';
import { useRouter } from 'vue-router';
import { ElMessage } from 'element-plus';
import { Refresh, Promotion } from '@element-plus/icons-vue';

// Plan §5.2 list page (read-only slice). Full CRUD + edit drawer
// (T2.3 + T2.6) lands in the next pass; for now the sandbox link is
// the primary interaction so users can immediately see what each
// rule does against sample records.

interface QcRuleDto {
  id: number;
  code: string;
  name: string;
  description: string | null;
  dimension: string;
  severity: string;
  expression: unknown;
  errorMessageTemplate: string;
  enabled: boolean;
  version: number;
  effectiveFrom: string | null;
  effectiveTo: string | null;
  createdAt: string;
  updatedAt: string;
}

const router = useRouter();
const rules = ref<QcRuleDto[]>([]);
const loading = ref(false);
const errorMsg = ref<string | null>(null);

async function loadRules() {
  loading.value = true;
  errorMsg.value = null;
  try {
    const res = await fetch('/api/rules');
    if (!res.ok) throw new Error(`HTTP ${res.status}`);
    rules.value = await res.json();
  } catch (err) {
    errorMsg.value = err instanceof Error ? err.message : String(err);
    ElMessage.error('规则列表加载失败');
  } finally {
    loading.value = false;
  }
}

onMounted(loadRules);

function openSandbox(rule: QcRuleDto) {
  router.push({ path: '/sandbox', query: { ruleCode: rule.code } });
}

const DIMENSION_LABELS: Record<string, string> = {
  completeness:    '完整性',
  logic:           '逻辑性',
  standardization: '规范性',
  consistency:     '一致性',
};

const SEVERITY_TAG_TYPE: Record<string, 'danger' | 'warning' | 'info'> = {
  mandatory:  'danger',
  deduction:  'warning',
  hint:       'info',
};

const SEVERITY_LABELS: Record<string, string> = {
  mandatory: '强制',
  deduction: '扣分',
  hint:      '提示',
};
</script>

<template>
  <section class="page">
    <header class="page-header">
      <div>
        <h2>规则配置</h2>
        <p class="hint">
          阶段 2 当前已可读取规则集（GET /api/rules）。完整 CRUD + 编辑抽屉 + AI 写规则在 T2.3 / T2.5 / T2.6 之后落地；
          现在用左侧 <strong>规则沙盒</strong> 即可对每条规则单独跑求值。
        </p>
      </div>
      <div class="header-actions">
        <el-button :icon="Refresh" @click="loadRules" :loading="loading">刷新</el-button>
      </div>
    </header>

    <el-alert
      v-if="errorMsg"
      type="error"
      :closable="false"
      :title="`后端错误：${errorMsg}`"
      class="error-banner"
    />

    <el-card shadow="never" class="rules-card">
      <el-table
        :data="rules"
        v-loading="loading"
        empty-text="暂无规则（V1__init_schema.sql 已植入 R001 + R002，请确认数据库已 Flyway 迁移）"
        stripe
      >
        <el-table-column prop="code"  label="编码"   width="100" />
        <el-table-column prop="name"  label="名称"   min-width="200" />

        <el-table-column label="维度" width="100">
          <template #default="{ row }">
            {{ DIMENSION_LABELS[row.dimension] || row.dimension }}
          </template>
        </el-table-column>

        <el-table-column label="严重度" width="100">
          <template #default="{ row }">
            <el-tag
              size="small"
              :type="SEVERITY_TAG_TYPE[row.severity] || 'info'"
            >
              {{ SEVERITY_LABELS[row.severity] || row.severity }}
            </el-tag>
          </template>
        </el-table-column>

        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag :type="row.enabled ? 'success' : 'info'" size="small">
              {{ row.enabled ? '启用中' : '已停用' }}
            </el-tag>
          </template>
        </el-table-column>

        <el-table-column prop="description" label="说明" min-width="280" show-overflow-tooltip />

        <el-table-column label="操作" width="140" fixed="right">
          <template #default="{ row }">
            <el-button
              link
              type="primary"
              :icon="Promotion"
              @click="openSandbox(row)"
            >
              测试沙盒
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>
  </section>
</template>

<style scoped>
.page { padding: 1.5rem; }

.page-header {
  display: flex; justify-content: space-between; align-items: flex-start;
  margin-bottom: 1rem; gap: 2rem;
}
.page-header h2 { margin: 0 0 0.35rem; font-size: 1.25rem; color: #1f1f1f; }
.hint { color: #888; font-size: 0.9rem; margin: 0; line-height: 1.6; }
.hint strong { color: #1677ff; font-weight: 600; }

.header-actions { flex-shrink: 0; }

.error-banner { margin-bottom: 1rem; }
.rules-card { background: #fff; }
</style>
