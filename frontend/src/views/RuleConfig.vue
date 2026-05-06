<script setup lang="ts">
import { ref, onMounted } from 'vue';
import { useRouter } from 'vue-router';
import { ElMessage, ElMessageBox } from 'element-plus';
import { Plus, Edit, Delete, Refresh, Promotion } from '@element-plus/icons-vue';
import RuleEditorDrawer from '@/components/RuleEditorDrawer.vue';

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

const drawerOpen = ref(false);
const drawerMode = ref<'create' | 'edit'>('create');
const drawerRule = ref<QcRuleDto | null>(null);

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

function openCreate() {
  drawerMode.value = 'create';
  drawerRule.value = null;
  drawerOpen.value = true;
}

function openEdit(rule: QcRuleDto) {
  drawerMode.value = 'edit';
  drawerRule.value = rule;
  drawerOpen.value = true;
}

async function toggleEnabled(rule: QcRuleDto, target: boolean) {
  try {
    const res = await fetch(`/api/rules/${rule.id}/enabled`, {
      method: 'PATCH',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ enabled: target }),
    });
    if (!res.ok) throw new Error(`HTTP ${res.status}`);
    rule.enabled = target;
    ElMessage.success(target ? '已启用' : '已停用');
  } catch (err) {
    ElMessage.error('启停失败：' + (err instanceof Error ? err.message : String(err)));
    await loadRules();
  }
}

async function confirmDelete(rule: QcRuleDto) {
  try {
    await ElMessageBox.confirm(
      `确定删除规则 ${rule.code}（${rule.name}）？该操作软删除，可由 DBA 恢复。`,
      '删除确认',
      { type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消' },
    );
  } catch {
    return;
  }

  try {
    const res = await fetch(`/api/rules/${rule.id}`, { method: 'DELETE' });
    if (res.status === 409) {
      const body = await res.json();
      ElMessage.warning(body.message ?? '请先停用规则再删除');
      return;
    }
    if (!res.ok) throw new Error(`HTTP ${res.status}`);
    ElMessage.success('已删除');
    await loadRules();
  } catch (err) {
    ElMessage.error('删除失败：' + (err instanceof Error ? err.message : String(err)));
  }
}

const DIMENSION_LABELS: Record<string, string> = {
  completeness: '完整性',
  logic: '逻辑性',
  standardization: '规范性',
  consistency: '一致性',
};

const SEVERITY_TAG_TYPE: Record<string, 'danger' | 'warning' | 'info'> = {
  mandatory: 'danger',
  deduction: 'warning',
  hint: 'info',
};

const SEVERITY_LABELS: Record<string, string> = {
  mandatory: '强制',
  deduction: '扣分',
  hint: '提示',
};
</script>

<template>
  <section class="page">
    <header class="page-header">
      <div>
        <h2>规则配置</h2>
        <p class="hint">
          完整 CRUD 已就位（POST / PUT / DELETE / PATCH）。删除策略：
          <strong>启用中的规则</strong>不可直接删除，需先停用。
          DSL 在保存时由 T2.2 校验器静态扫描，错误会以 JSONPath 路径前缀报回。
        </p>
      </div>
      <div class="header-actions">
        <el-button :icon="Refresh" @click="loadRules" :loading="loading">刷新</el-button>
        <el-button :icon="Plus" type="primary" @click="openCreate">新建规则</el-button>
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
        <el-table-column prop="code" label="编码" width="100" />
        <el-table-column prop="name" label="名称" min-width="180" />

        <el-table-column label="维度" width="100">
          <template #default="{ row }">
            {{ DIMENSION_LABELS[row.dimension] || row.dimension }}
          </template>
        </el-table-column>

        <el-table-column label="严重度" width="100">
          <template #default="{ row }">
            <el-tag size="small" :type="SEVERITY_TAG_TYPE[row.severity] || 'info'">
              {{ SEVERITY_LABELS[row.severity] || row.severity }}
            </el-tag>
          </template>
        </el-table-column>

        <el-table-column label="启用" width="90">
          <template #default="{ row }">
            <el-switch
              :model-value="row.enabled"
              @change="(v: boolean) => toggleEnabled(row, v)"
            />
          </template>
        </el-table-column>

        <el-table-column
          prop="description"
          label="说明"
          min-width="200"
          show-overflow-tooltip
        />

        <el-table-column label="操作" width="240" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" :icon="Edit" @click="openEdit(row)">
              编辑
            </el-button>
            <el-button link type="primary" :icon="Promotion" @click="openSandbox(row)">
              测试
            </el-button>
            <el-button
              link
              type="danger"
              :icon="Delete"
              @click="confirmDelete(row)"
              :disabled="row.enabled"
              :title="row.enabled ? '请先停用再删除' : ''"
            >
              删除
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <RuleEditorDrawer
      v-model="drawerOpen"
      :mode="drawerMode"
      :rule="drawerRule"
      @saved="loadRules"
    />
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
  align-items: flex-start;
  margin-bottom: 1rem;
  gap: 2rem;
}
.page-header h2 {
  margin: 0 0 0.35rem;
  font-size: 1.25rem;
  color: #1f1f1f;
}
.hint {
  color: #888;
  font-size: 0.9rem;
  margin: 0;
  line-height: 1.6;
}
.hint strong {
  color: #b3261e;
  font-weight: 600;
}

.header-actions {
  flex-shrink: 0;
  display: flex;
  gap: 0.5rem;
}

.error-banner {
  margin-bottom: 1rem;
}
.rules-card {
  background: #fff;
}
</style>
