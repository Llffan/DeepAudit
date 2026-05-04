<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue';
import { useRouter } from 'vue-router';
import { ElMessage } from 'element-plus';
import { Refresh, Search, Plus, View, Document } from '@element-plus/icons-vue';

interface RecordListItem {
  id: number;
  recordNo: string;
  name: string | null;
  gender: string | null;
  age: number | null;
  admissionDate: string | null;
  dischargeDate: string | null;
  lengthOfStay: number | null;
  mainDiagnosisName: string | null;
  status: 'draft' | 'confirmed' | 'checked';
  extractionConfidence: number | null;
  hasPdf: boolean;
  createdAt: string;
  updatedAt: string;
}

interface PageResp<T> {
  content: T[];
  total: number;
  page: number;
  size: number;
}

const router = useRouter();

const rows = ref<RecordListItem[]>([]);
const total = ref(0);
const page = ref(1);                 // el-pagination is 1-based; backend is 0-based
const size = ref(20);
const status = ref<string>('');      // '', 'draft', 'confirmed', 'checked'
const keyword = ref('');
const loading = ref(false);
const errorMsg = ref<string | null>(null);

const STATUS_OPTIONS = [
  { value: '',          label: '全部状态' },
  { value: 'draft',     label: '草稿' },
  { value: 'confirmed', label: '已确认' },
  { value: 'checked',   label: '已检查' },
];

const STATUS_TAG_TYPE: Record<string, 'info' | 'warning' | 'success'> = {
  draft: 'info',
  confirmed: 'warning',
  checked: 'success',
};

const STATUS_LABEL: Record<string, string> = {
  draft: '草稿',
  confirmed: '已确认',
  checked: '已检查',
};

async function load() {
  loading.value = true;
  errorMsg.value = null;
  try {
    const params = new URLSearchParams();
    if (status.value)  params.set('status', status.value);
    if (keyword.value) params.set('keyword', keyword.value.trim());
    params.set('page', String(page.value - 1));
    params.set('size', String(size.value));

    const res = await fetch(`/api/medical-records?${params.toString()}`);
    if (!res.ok) throw new Error(`HTTP ${res.status} ${res.statusText}`);
    const body = (await res.json()) as PageResp<RecordListItem>;
    rows.value = body.content;
    total.value = body.total;
  } catch (err) {
    errorMsg.value = err instanceof Error ? err.message : String(err);
    ElMessage.error('病案列表加载失败：' + errorMsg.value);
  } finally {
    loading.value = false;
  }
}

onMounted(load);

// Reset to page 1 whenever filters change.
watch([status, size], () => {
  page.value = 1;
  load();
});

function onSearch() {
  page.value = 1;
  load();
}

function onPageChange(p: number) {
  page.value = p;
  load();
}

function gotoImport() {
  router.push('/import');
}

function viewPdf(row: RecordListItem) {
  window.open(`/api/medical-records/${row.id}/pdf`, '_blank');
}

function viewDetail(row: RecordListItem) {
  // Phase 4 will register /records/:id/results; until then, show a JSON peek
  // by hitting GET /{id}. Cheapest UX that doesn't lie about a missing page.
  router.push({ path: '/import', query: { id: String(row.id) } });
}

const fmtDate = (s: string | null) => (s ? s : '—');
const fmtPct = (v: number | null) => (v == null ? '—' : (Number(v) * 100).toFixed(0) + '%');

const tableHeight = computed(() => 'calc(100vh - 290px)');
</script>

<template>
  <section class="page">
    <header class="page-header">
      <div>
        <h2>病案列表</h2>
        <p class="hint">
          已落库的病案首页（{{ total }} 条）。状态流转：
          <el-tag size="small" type="info">草稿</el-tag>
          → <el-tag size="small" type="warning">已确认</el-tag>
          → <el-tag size="small" type="success">已检查</el-tag>。
          点"查看"返回录入页（带 id 进入编辑模式）。
        </p>
      </div>
      <div class="header-actions">
        <el-button :icon="Refresh" @click="load" :loading="loading">刷新</el-button>
        <el-button :icon="Plus" type="primary" @click="gotoImport">新建病案</el-button>
      </div>
    </header>

    <el-card shadow="never" class="filter-card">
      <div class="filters">
        <el-select v-model="status" class="filter-status" placeholder="状态">
          <el-option v-for="o in STATUS_OPTIONS" :key="o.value" :label="o.label" :value="o.value" />
        </el-select>
        <el-input
          v-model="keyword"
          placeholder="按病案号 / 姓名 / 主诊断名 模糊搜索"
          clearable
          class="filter-keyword"
          @keyup.enter="onSearch"
          @clear="onSearch"
        >
          <template #prefix><el-icon><Search /></el-icon></template>
        </el-input>
        <el-button type="primary" :icon="Search" @click="onSearch">查询</el-button>
      </div>
    </el-card>

    <el-alert
      v-if="errorMsg"
      type="error"
      :closable="false"
      :title="`后端错误：${errorMsg}`"
      class="error-banner"
    />

    <el-card shadow="never" class="rows-card">
      <el-table
        :data="rows"
        v-loading="loading"
        :height="tableHeight"
        empty-text="暂无病案，去“病案导入”页上传 PDF 或一键模拟填充"
        stripe
      >
        <el-table-column prop="id" label="ID" width="70" />
        <el-table-column prop="recordNo" label="病案号" width="140" show-overflow-tooltip />
        <el-table-column prop="name" label="姓名" width="100" />
        <el-table-column label="性别/年龄" width="100">
          <template #default="{ row }">
            <span>{{ row.gender || '—' }} / {{ row.age ?? '—' }}</span>
          </template>
        </el-table-column>

        <el-table-column label="入/出院日期" width="200">
          <template #default="{ row }">
            <div class="dates">
              <span>{{ fmtDate(row.admissionDate) }}</span>
              <span class="dash">→</span>
              <span>{{ fmtDate(row.dischargeDate) }}</span>
              <span v-if="row.lengthOfStay != null" class="los">（{{ row.lengthOfStay }}天）</span>
            </div>
          </template>
        </el-table-column>

        <el-table-column prop="mainDiagnosisName" label="主诊断" min-width="180" show-overflow-tooltip />

        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag size="small" :type="STATUS_TAG_TYPE[row.status]">
              {{ STATUS_LABEL[row.status] || row.status }}
            </el-tag>
          </template>
        </el-table-column>

        <el-table-column label="抽取置信度" width="110">
          <template #default="{ row }">
            <span :class="{ low: row.extractionConfidence != null && Number(row.extractionConfidence) < 0.7 }">
              {{ fmtPct(row.extractionConfidence) }}
            </span>
          </template>
        </el-table-column>

        <el-table-column label="更新时间" width="170">
          <template #default="{ row }">
            <span>{{ new Date(row.updatedAt).toLocaleString('zh-CN', { hour12: false }) }}</span>
          </template>
        </el-table-column>

        <el-table-column label="操作" width="170" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" :icon="View" @click="viewDetail(row)">查看</el-button>
            <el-button
              link
              type="primary"
              :icon="Document"
              :disabled="!row.hasPdf"
              :title="row.hasPdf ? '查看原始 PDF' : '该病案无原始 PDF（手填或模拟生成）'"
              @click="viewPdf(row)"
            >
              PDF
            </el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="pagination">
        <el-pagination
          background
          layout="total, sizes, prev, pager, next, jumper"
          :total="total"
          :current-page="page"
          :page-size="size"
          :page-sizes="[10, 20, 50, 100]"
          @current-change="onPageChange"
          @update:page-size="(v: number) => (size = v)"
        />
      </div>
    </el-card>
  </section>
</template>

<style scoped>
.page {
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
  display: flex;
  align-items: center;
  gap: 0.35rem;
  flex-wrap: wrap;
}
.header-actions {
  flex-shrink: 0;
  display: flex;
  gap: 0.5rem;
}

.filter-card {
  margin-bottom: 1rem;
  background: #fff;
}
.filters {
  display: flex;
  align-items: center;
  gap: 0.75rem;
}
.filter-status {
  width: 140px;
}
.filter-keyword {
  flex: 1;
  max-width: 480px;
}

.error-banner {
  margin-bottom: 1rem;
}
.rows-card {
  background: #fff;
}

.dates {
  display: inline-flex;
  align-items: center;
  gap: 0.35rem;
  white-space: nowrap;
}
.dash {
  color: #aaa;
}
.los {
  color: #999;
  font-size: 0.85rem;
}
.low {
  color: #d4380d;
  font-weight: 600;
}

.pagination {
  margin-top: 1rem;
  display: flex;
  justify-content: flex-end;
}
</style>
