<script setup lang="ts">
import { ref, reactive, computed, watch, onBeforeUnmount, onMounted } from 'vue';
import { useRouter, useRoute } from 'vue-router';
import { ElMessage, type FormInstance, type UploadRequestOptions } from 'element-plus';
import { UploadFilled, ArrowLeft, Refresh, Check, MagicStick, Files } from '@element-plus/icons-vue';
import {
  emptyRecord,
  GENDER_OPTIONS,
  ICD_VER_OPTIONS,
  ADMISSION_ROUTE_OPTIONS,
  DISCHARGE_STATUS_OPTIONS,
  ANESTHESIA_OPTIONS,
  BUSINESS_FIELDS,
  type MedicalRecord,
} from '@/types/medicalRecord';

const router = useRouter();
const route = useRoute();
const mode = ref<'pdf' | 'manual'>('pdf');
const form = reactive<MedicalRecord>(emptyRecord());
const formRef = ref<FormInstance>();
const submitting = ref(false);
const extractingPdf = ref(false);
const mockFilling = ref(false);
const extractionConfidence = ref<number | null>(null);
const sourcePdfPath = ref<string | null>(null);
const lastError = ref<string | null>(null);

// Edit-mode state. When ?id=X is present we load the existing record into
// the form; subsequent saves go through the same POST endpoint with id set,
// which the backend treats as an update (T3.3 MedicalRecordSaveService).
const recordId = ref<number | null>(null);
const recordStatus = ref<'draft' | 'confirmed' | 'checked' | null>(null);
const recordCreatedAt = ref<string | null>(null);
const loadingRecord = ref(false);

const isEditMode = computed(() => recordId.value !== null);

const STATUS_LABEL: Record<string, string> = {
  draft: '草稿',
  confirmed: '已确认',
  checked: '已检查',
};
const STATUS_TAG_TYPE: Record<string, 'info' | 'warning' | 'success'> = {
  draft: 'info',
  confirmed: 'warning',
  checked: 'success',
};

// Blob URL for the just-uploaded PDF — populated client-side from the
// File the user dropped, so the <embed> preview works without a backend
// roundtrip and even before the record is saved (T3.5).
const pdfBlobUrl = ref<string | null>(null);
function setPdfBlobFromFile(file: File | null) {
  if (pdfBlobUrl.value) {
    URL.revokeObjectURL(pdfBlobUrl.value);
    pdfBlobUrl.value = null;
  }
  if (file) {
    pdfBlobUrl.value = URL.createObjectURL(file);
  }
}
onBeforeUnmount(() => setPdfBlobFromFile(null));

// Auto-derive 住院天数 when both dates present — convenience only;
// backend rule R002 still validates that the user-stored value matches.
watch(
  () => [form.admissionDate, form.dischargeDate],
  ([adm, dis]) => {
    if (adm && dis) {
      const ms = new Date(dis).getTime() - new Date(adm).getTime();
      const days = Math.round(ms / 86400000);
      if (days >= 0) form.lengthOfStay = days;
    }
  },
);

const fillRatio = computed(() => {
  const filled = BUSINESS_FIELDS.filter((k) => {
    const v = form[k];
    return v !== null && v !== undefined && v !== '';
  }).length;
  return { filled, total: BUSINESS_FIELDS.length };
});

const fillPercent = computed(() =>
  Math.round((fillRatio.value.filled / fillRatio.value.total) * 100),
);

const confidencePercent = computed(() => {
  if (extractionConfidence.value == null) return null;
  return Math.round(extractionConfidence.value * 100);
});

const confidenceColor = computed(() => {
  const v = extractionConfidence.value;
  if (v == null) return '';
  if (v >= 0.85) return '#1f7a3a';
  if (v >= 0.6) return '#b88600';
  return '#b3261e';
});

const rules = {
  recordNo: [{ required: true, message: '病案号必填', trigger: 'blur' }],
};

// Pre-flight + post-error message mapping. Pre-flight short-circuits the
// upload when we can detect the problem from the File object alone (saves
// a wasted network round-trip + nginx 413 raw HTML); the response mapper
// handles whatever the server actually rejected.
const MAX_PDF_BYTES = 10 * 1024 * 1024;

function preflightPdf(file: File): string | null {
  if (!file.name.toLowerCase().endsWith('.pdf')) {
    return '仅支持 PDF 文件（.pdf 后缀）';
  }
  if (file.size > MAX_PDF_BYTES) {
    return `文件超过 10 MB 上限（实际 ${(file.size / 1024 / 1024).toFixed(1)} MB）`;
  }
  return null;
}

async function describeHttpError(res: Response): Promise<string> {
  // Friendly Chinese message for the three status codes the import endpoint
  // returns for known reasons. Backend ApiExceptionHandler always sends
  // { code, message, errors } so prefer the server message when present.
  let serverMsg: string | null = null;
  try {
    const body = await res.json();
    if (body?.message) serverMsg = body.message;
  } catch {
    // body might be HTML (nginx 413) or empty; fall through to status-based mapping
  }
  if (serverMsg) return serverMsg;
  if (res.status === 415) return '仅支持 PDF 文件';
  if (res.status === 413) return '文件超过 10 MB 上限';
  if (res.status === 400) return '上传被拒绝，请检查文件是否完整或不为空';
  if (res.status === 503) return 'LLM 暂不可用，请稍后再试或联系管理员检查 API Key';
  return `HTTP ${res.status} ${res.statusText}`;
}

async function uploadPdf(opts: UploadRequestOptions) {
  const file = opts.file as File;
  const why = preflightPdf(file);
  if (why) {
    lastError.value = why;
    ElMessage.error(why);
    return;
  }

  extractingPdf.value = true;
  lastError.value = null;
  try {
    const fd = new FormData();
    fd.append('file', file);
    const res = await fetch('/api/medical-records/import', {
      method: 'POST',
      body: fd,
    });
    if (!res.ok) {
      throw new Error(await describeHttpError(res));
    }
    const body = (await res.json()) as {
      fields?: Partial<MedicalRecord>;
      extractionConfidence?: number;
      sourcePdfPath?: string;
      degraded?: boolean;
      degradedReason?: string;
    };
    if (body.fields) Object.assign(form, body.fields);
    extractionConfidence.value = body.extractionConfidence ?? null;
    sourcePdfPath.value = body.sourcePdfPath ?? null;
    setPdfBlobFromFile(file);
    if (body.degraded) {
      // 200 with degraded=true means the PDF saved but extraction failed —
      // operator drops into manual-fill mode (plan §8.7).
      ElMessage.warning(
        `LLM 抽取失败：${body.degradedReason ?? '未知原因'}。已落盘 PDF，请手动补全字段。`,
      );
    } else {
      ElMessage.success('PDF 抽取完成，请核对下方字段');
    }
  } catch (err) {
    lastError.value = err instanceof Error ? err.message : String(err);
    ElMessage.error(`抽取失败：${lastError.value}`);
  } finally {
    extractingPdf.value = false;
  }
}

async function mockFill() {
  mockFilling.value = true;
  lastError.value = null;
  try {
    const res = await fetch('/api/medical-records/mock-fill', { method: 'POST' });
    if (!res.ok) {
      let detail = `${res.status} ${res.statusText}`;
      try {
        const body = await res.json();
        if (body?.message) detail = body.message;
      } catch {
        // ignore JSON parse error; fall back to status line
      }
      throw new Error(detail);
    }
    const body = (await res.json()) as {
      fields?: Partial<MedicalRecord>;
      extractionConfidence?: number;
    };
    if (body.fields) {
      // Wipe stale form first so a previous PDF's residual values don't
      // mix into the new mock record.
      Object.assign(form, emptyRecord());
      Object.assign(form, body.fields);
    }
    extractionConfidence.value = body.extractionConfidence ?? null;
    sourcePdfPath.value = null;
    setPdfBlobFromFile(null);
    formRef.value?.clearValidate();
    ElMessage.success('已生成测试数据，请按需修改后保存');
  } catch (err) {
    lastError.value = err instanceof Error ? err.message : String(err);
    ElMessage.error(`生成失败：${lastError.value}`);
  } finally {
    mockFilling.value = false;
  }
}

async function save(target: 'draft' | 'confirmed') {
  if (!formRef.value) return;
  try {
    await formRef.value.validate();
  } catch {
    ElMessage.warning('请补全必填项后再保存');
    return;
  }
  submitting.value = true;
  lastError.value = null;
  try {
    const payload = {
      ...form,
      id: recordId.value ?? undefined,
      status: target,
      sourcePdfPath: sourcePdfPath.value,
      extractionConfidence: extractionConfidence.value,
    };
    const res = await fetch('/api/medical-records', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(payload),
    });
    if (!res.ok) throw new Error(`HTTP ${res.status} ${res.statusText}`);
    const saved = (await res.json()) as { id?: number; status?: string };
    ElMessage.success(target === 'draft' ? '已保存为草稿' : '已确认');
    if (saved.id) {
      recordId.value = saved.id;
      recordStatus.value = (saved.status as typeof recordStatus.value) ?? target;
      if (route.query.id !== String(saved.id)) {
        router.replace({ path: '/import', query: { id: String(saved.id) } });
      }
      // T4.1：「确认提交」语义即"确认 + 立即跑规则检查"。保存成功后顺势
      // POST /check 把状态推进到 checked，然后直接跳结果页。失败时停留
      // 在录入页（已是 confirmed），用户可手动重试。
      if (target === 'confirmed') {
        await runCheckAndGoto(saved.id);
      }
    }
  } catch (err) {
    lastError.value = err instanceof Error ? err.message : String(err);
    ElMessage.error(`保存失败：${lastError.value}`);
  } finally {
    submitting.value = false;
  }
}

async function runCheckAndGoto(id: number) {
  try {
    const res = await fetch(`/api/medical-records/${id}/check`, { method: 'POST' });
    if (!res.ok) throw new Error(`HTTP ${res.status} ${res.statusText}`);
    const body = (await res.json()) as { summary?: { totalHits?: number } };
    const hits = body.summary?.totalHits ?? 0;
    ElMessage.success(hits === 0 ? '检查完成，全部通过' : `检查完成，命中 ${hits} 条`);
    recordStatus.value = 'checked';
    void router.push({ path: '/results', query: { recordId: String(id) } });
  } catch (err) {
    const msg = err instanceof Error ? err.message : String(err);
    ElMessage.warning(`已保存但检查失败：${msg}。请到结果页手动重试。`);
  }
}

async function loadRecord(id: number) {
  loadingRecord.value = true;
  lastError.value = null;
  try {
    const res = await fetch(`/api/medical-records/${id}`);
    if (res.status === 404) {
      ElMessage.warning(`病案 #${id} 不存在或已删除`);
      // Drop the bad id from the URL so subsequent reloads start clean.
      router.replace({ path: '/import' });
      return;
    }
    if (!res.ok) throw new Error(`HTTP ${res.status} ${res.statusText}`);
    const dto = (await res.json()) as MedicalRecord & {
      id: number;
      status?: 'draft' | 'confirmed' | 'checked';
      createdAt?: string;
    };
    Object.assign(form, emptyRecord());
    Object.assign(form, dto);
    recordId.value = dto.id;
    recordStatus.value = dto.status ?? 'draft';
    recordCreatedAt.value = dto.createdAt ?? null;
    extractionConfidence.value = dto.extractionConfidence ?? null;
    sourcePdfPath.value = dto.sourcePdfPath ?? null;
    setPdfBlobFromFile(null);
    if (dto.sourcePdfPath) {
      void loadPdfPreview(id);
    }
    formRef.value?.clearValidate();
  } catch (err) {
    lastError.value = err instanceof Error ? err.message : String(err);
    ElMessage.error(`加载失败：${lastError.value}`);
  } finally {
    loadingRecord.value = false;
  }
}

async function loadPdfPreview(id: number) {
  try {
    const res = await fetch(`/api/medical-records/${id}/pdf`);
    if (!res.ok) return;
    const blob = await res.blob();
    if (pdfBlobUrl.value) URL.revokeObjectURL(pdfBlobUrl.value);
    pdfBlobUrl.value = URL.createObjectURL(blob);
  } catch {
    // PDF preview is best-effort — silently degrade if file is missing
  }
}

function gotoList() {
  router.push('/records');
}

function reset() {
  if (recordId.value != null) {
    void loadRecord(recordId.value);
    ElMessage.info('已从服务器重新加载');
    return;
  }
  Object.assign(form, emptyRecord());
  extractionConfidence.value = null;
  sourcePdfPath.value = null;
  setPdfBlobFromFile(null);
  lastError.value = null;
  formRef.value?.clearValidate();
  ElMessage.info('已清空表单');
}

function parseRouteId(raw: unknown): number | null {
  if (typeof raw !== 'string') return null;
  return /^\d+$/.test(raw) ? Number(raw) : null;
}

onMounted(() => {
  const id = parseRouteId(route.query.id);
  if (id !== null) void loadRecord(id);
});

// Reload when the user clicks a different record in the list (?id changes
// in-place without remounting the component).
watch(
  () => route.query.id,
  (next) => {
    const id = parseRouteId(next);
    if (id !== null && id !== recordId.value) {
      void loadRecord(id);
    }
  },
);
</script>

<template>
  <div class="page" :class="{ 'has-preview': !!pdfBlobUrl }" v-loading="loadingRecord">
    <header class="topbar">
      <el-button :icon="ArrowLeft" link @click="router.push('/')">返回首页</el-button>
      <el-button v-if="isEditMode" :icon="Files" link @click="gotoList">返回列表</el-button>
      <h1>{{ isEditMode ? '病案首页编辑' : '病案首页录入' }}</h1>
      <el-tag
        v-if="isEditMode && recordStatus"
        size="small"
        :type="STATUS_TAG_TYPE[recordStatus]"
        class="edit-badge"
      >
        #{{ recordId }} · {{ STATUS_LABEL[recordStatus] }}
      </el-tag>
      <div class="progress-pill">
        <span class="progress-num">{{ fillRatio.filled }} / {{ fillRatio.total }}</span>
        <span class="progress-pct">{{ fillPercent }}%</span>
      </div>
    </header>

    <div class="content-grid">
      <aside v-if="pdfBlobUrl" class="pdf-preview">
        <div class="pdf-preview-head">
          <span>原文 PDF</span>
          <el-button link type="primary" size="small" @click="setPdfBlobFromFile(null)">
            收起预览
          </el-button>
        </div>
        <embed :src="pdfBlobUrl" type="application/pdf" />
      </aside>

      <div class="form-pane">

    <section class="mode-switch">
      <el-segmented
        v-model="mode"
        :options="[
          { label: 'PDF 自动抽取', value: 'pdf' },
          { label: '手动录入', value: 'manual' },
        ]"
      />
      <el-button
        :icon="MagicStick"
        :loading="mockFilling"
        :disabled="extractingPdf || submitting"
        plain
        type="primary"
        size="default"
        class="mock-btn"
        @click="mockFill"
      >
        🎲 LLM 生成测试数据
      </el-button>
    </section>

    <section v-if="mode === 'pdf'" class="upload-zone">
      <el-upload
        drag
        accept=".pdf"
        :limit="1"
        :show-file-list="false"
        :http-request="uploadPdf"
        :disabled="extractingPdf"
      >
        <el-icon class="upload-icon" :size="44"><UploadFilled /></el-icon>
        <div class="upload-text">
          <strong>{{ extractingPdf ? '抽取中…' : '将 PDF 拖到此处' }}</strong>
          <span>或 <em>点击选择文件</em></span>
        </div>
        <template #tip>
          <p class="upload-tip">
            单个 PDF · ≤ 10 MB · 调用 Gemini 2.5 Flash 多模态自动抽取 30 字段
          </p>
        </template>
      </el-upload>

      <div class="source-hospital-row">
        <span class="source-label">来源医院</span>
        <el-input
          v-model="form.sourceHospital"
          placeholder="可选，例如 XX 第二人民医院"
          clearable
          :disabled="extractingPdf"
          class="source-input"
        />
      </div>

      <div v-if="confidencePercent != null" class="confidence-row">
        <span class="confidence-label">抽取置信度</span>
        <el-progress
          :percentage="confidencePercent"
          :color="confidenceColor"
          :stroke-width="10"
          class="confidence-bar"
        />
        <span class="confidence-value" :style="{ color: confidenceColor }">
          {{ confidencePercent }}%
        </span>
      </div>
    </section>

    <el-form
      ref="formRef"
      :model="form"
      :rules="rules"
      label-position="top"
      class="record-form"
    >
      <el-divider content-position="left">基本信息</el-divider>
      <el-row :gutter="16">
        <el-col :span="6">
          <el-form-item label="病案号" prop="recordNo" required>
            <el-input v-model="form.recordNo" placeholder="必填" clearable />
          </el-form-item>
        </el-col>
        <el-col :span="6">
          <el-form-item label="姓名">
            <el-input v-model="form.name" placeholder="患者姓名" clearable />
          </el-form-item>
        </el-col>
        <el-col :span="3">
          <el-form-item label="性别">
            <el-select v-model="form.gender" clearable placeholder="">
              <el-option
                v-for="o in GENDER_OPTIONS"
                :key="o.value"
                :label="o.label"
                :value="o.value"
              />
            </el-select>
          </el-form-item>
        </el-col>
        <el-col :span="5">
          <el-form-item label="出生日期">
            <el-date-picker
              v-model="form.birthDate"
              type="date"
              value-format="YYYY-MM-DD"
              style="width: 100%"
            />
          </el-form-item>
        </el-col>
        <el-col :span="2">
          <el-form-item label="年龄">
            <el-input-number
              v-model="form.age"
              :min="0"
              :max="150"
              :controls="false"
              style="width: 100%"
            />
          </el-form-item>
        </el-col>
        <el-col :span="6">
          <el-form-item label="身份证（脱敏）">
            <el-input
              v-model="form.idCardMasked"
              placeholder="如 110101********0011"
              clearable
            />
          </el-form-item>
        </el-col>
      </el-row>

      <el-divider content-position="left">入出院信息</el-divider>
      <el-row :gutter="16">
        <el-col :span="6">
          <el-form-item label="入院日期">
            <el-date-picker
              v-model="form.admissionDate"
              type="date"
              value-format="YYYY-MM-DD"
              style="width: 100%"
            />
          </el-form-item>
        </el-col>
        <el-col :span="6">
          <el-form-item label="出院日期">
            <el-date-picker
              v-model="form.dischargeDate"
              type="date"
              value-format="YYYY-MM-DD"
              style="width: 100%"
            />
          </el-form-item>
        </el-col>
        <el-col :span="3">
          <el-form-item label="住院天数">
            <el-input-number
              v-model="form.lengthOfStay"
              :min="0"
              :controls="false"
              style="width: 100%"
            />
          </el-form-item>
        </el-col>
        <el-col :span="9" />

        <el-col :span="6">
          <el-form-item label="入院科室">
            <el-input v-model="form.admissionDept" clearable />
          </el-form-item>
        </el-col>
        <el-col :span="6">
          <el-form-item label="出院科室">
            <el-input v-model="form.dischargeDept" clearable />
          </el-form-item>
        </el-col>
        <el-col :span="6">
          <el-form-item label="入院途径">
            <el-select v-model="form.admissionRoute" clearable>
              <el-option
                v-for="o in ADMISSION_ROUTE_OPTIONS"
                :key="o.value"
                :label="o.label"
                :value="o.value"
              />
            </el-select>
          </el-form-item>
        </el-col>
        <el-col :span="6">
          <el-form-item label="离院方式">
            <el-select v-model="form.dischargeStatus" clearable>
              <el-option
                v-for="o in DISCHARGE_STATUS_OPTIONS"
                :key="o.value"
                :label="o.label"
                :value="o.value"
              />
            </el-select>
          </el-form-item>
        </el-col>
      </el-row>

      <el-divider content-position="left">主要诊断</el-divider>
      <el-row :gutter="16">
        <el-col :span="6">
          <el-form-item label="主诊编码">
            <el-input
              v-model="form.mainDiagnosisCode"
              placeholder="如 J42.x00"
              clearable
            />
          </el-form-item>
        </el-col>
        <el-col :span="10">
          <el-form-item label="主诊名称">
            <el-input
              v-model="form.mainDiagnosisName"
              placeholder="如 慢性支气管炎"
              clearable
            />
          </el-form-item>
        </el-col>
        <el-col :span="4">
          <el-form-item label="ICD 版本">
            <el-select v-model="form.mainDiagnosisIcdVer" clearable>
              <el-option
                v-for="o in ICD_VER_OPTIONS"
                :key="o.value"
                :label="o.label"
                :value="o.value"
              />
            </el-select>
          </el-form-item>
        </el-col>
        <el-col :span="4">
          <el-form-item label="其他诊断数">
            <el-input-number
              v-model="form.otherDiagnosisCount"
              :min="0"
              :controls="false"
              style="width: 100%"
            />
          </el-form-item>
        </el-col>
        <el-col :span="14">
          <el-form-item label="病理诊断">
            <el-input v-model="form.pathologicalDiagnosis" clearable />
          </el-form-item>
        </el-col>
      </el-row>

      <el-divider content-position="left">主要手术</el-divider>
      <el-row :gutter="16">
        <el-col :span="6">
          <el-form-item label="主手术编码">
            <el-input
              v-model="form.mainOperationCode"
              placeholder="ICD-9-CM-3"
              clearable
            />
          </el-form-item>
        </el-col>
        <el-col :span="10">
          <el-form-item label="主手术名称">
            <el-input v-model="form.mainOperationName" clearable />
          </el-form-item>
        </el-col>
        <el-col :span="4">
          <el-form-item label="手术日期">
            <el-date-picker
              v-model="form.operationDate"
              type="date"
              value-format="YYYY-MM-DD"
              style="width: 100%"
            />
          </el-form-item>
        </el-col>
        <el-col :span="4">
          <el-form-item label="麻醉方式">
            <el-select v-model="form.anesthesiaMethod" clearable>
              <el-option
                v-for="o in ANESTHESIA_OPTIONS"
                :key="o.value"
                :label="o.label"
                :value="o.value"
              />
            </el-select>
          </el-form-item>
        </el-col>
        <el-col :span="6">
          <el-form-item label="手术医生">
            <el-input v-model="form.operator" clearable />
          </el-form-item>
        </el-col>
      </el-row>

      <el-divider content-position="left">费用</el-divider>
      <el-row :gutter="16">
        <el-col :span="6">
          <el-form-item label="总费用 (¥)">
            <el-input-number
              v-model="form.totalCost"
              :min="0"
              :precision="2"
              :controls="false"
              style="width: 100%"
            />
          </el-form-item>
        </el-col>
        <el-col :span="6">
          <el-form-item label="药品费 (¥)">
            <el-input-number
              v-model="form.drugCost"
              :min="0"
              :precision="2"
              :controls="false"
              style="width: 100%"
            />
          </el-form-item>
        </el-col>
        <el-col :span="6">
          <el-form-item label="手术费 (¥)">
            <el-input-number
              v-model="form.operationCost"
              :min="0"
              :precision="2"
              :controls="false"
              style="width: 100%"
            />
          </el-form-item>
        </el-col>
        <el-col :span="6">
          <el-form-item label="医疗服务费 (¥)">
            <el-input-number
              v-model="form.medicalServiceCost"
              :min="0"
              :precision="2"
              :controls="false"
              style="width: 100%"
            />
          </el-form-item>
        </el-col>
      </el-row>
    </el-form>
      </div>
    </div>

    <footer class="actions">
      <span v-if="lastError" class="last-error" :title="lastError">
        最近一次错误：{{ lastError }}
      </span>
      <span v-else class="actions-spacer" />
      <el-button :icon="Refresh" @click="reset" :disabled="submitting">清空</el-button>
      <el-button @click="save('draft')" :loading="submitting">保存草稿</el-button>
      <el-button :icon="Check" type="primary" @click="save('confirmed')" :loading="submitting">
        确认提交
      </el-button>
    </footer>
  </div>
</template>

<style scoped>
.page {
  max-width: 1100px;
  margin: 2rem auto 6rem;
  padding: 0 1.5rem;
  transition: max-width 200ms ease;
}
/* When the PDF preview is open we need elbow room for two columns. */
.page.has-preview {
  max-width: 1600px;
}

.content-grid {
  display: grid;
  grid-template-columns: 1fr;
  gap: 1.25rem;
  align-items: flex-start;
}
.has-preview .content-grid {
  grid-template-columns: minmax(360px, 7fr) minmax(0, 9fr);
}

.pdf-preview {
  position: sticky;
  top: 1rem;
  height: calc(100vh - 6rem);
  display: flex;
  flex-direction: column;
  border: 1px solid #e2e6ea;
  border-radius: 8px;
  background: #f5f6f8;
  overflow: hidden;
}
.pdf-preview-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 6px 12px;
  background: #fff;
  border-bottom: 1px solid #e2e6ea;
  font-size: 0.85rem;
  color: #444;
  font-weight: 500;
}
.pdf-preview embed {
  flex: 1;
  width: 100%;
  border: none;
  background: #525659;
}
@media (max-width: 1280px) {
  /* Below ~1280px, stacking the preview above the form preserves
     readability instead of squeezing both into unusable widths. */
  .has-preview .content-grid {
    grid-template-columns: 1fr;
  }
  .pdf-preview {
    position: relative;
    top: 0;
    height: 60vh;
  }
}

.form-pane {
  display: flex;
  flex-direction: column;
}

.topbar {
  display: flex;
  align-items: center;
  gap: 1rem;
  margin-bottom: 1.25rem;
}
.topbar h1 {
  flex: 1;
  font-size: 1.4rem;
  margin: 0;
  color: #222;
  font-weight: 600;
}
.progress-pill {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  font-size: 0.85rem;
  background: #f4f6f8;
  padding: 5px 14px;
  border-radius: 12px;
}
.progress-num {
  color: #555;
  font-variant-numeric: tabular-nums;
}
.progress-pct {
  color: #2563eb;
  font-weight: 600;
}

.mode-switch {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 1rem;
  margin-bottom: 1.25rem;
}
.mock-btn {
  flex-shrink: 0;
}

.upload-zone {
  margin-bottom: 1.25rem;
}
.upload-zone :deep(.el-upload-dragger) {
  padding: 28px 24px;
  background: #fafbfc;
  border-radius: 8px;
  border-color: #e0e4e8;
}
.upload-zone :deep(.el-upload-dragger:hover) {
  border-color: #409eff;
  background: #f7faff;
}
.upload-icon {
  color: #b0b7c0;
}
.upload-text {
  display: flex;
  flex-direction: column;
  gap: 4px;
  margin-top: 8px;
  color: #555;
}
.upload-text strong {
  font-size: 1.05rem;
  color: #222;
  font-weight: 500;
}
.upload-text em {
  color: #409eff;
  font-style: normal;
}
.upload-tip {
  font-size: 0.8rem;
  color: #999;
  margin-top: 6px;
  text-align: left;
}

.source-hospital-row {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-top: 12px;
  padding: 10px 14px;
  background: #fafbfc;
  border-radius: 6px;
  border: 1px solid #eee;
}
.source-label {
  font-size: 0.85rem;
  color: #666;
  min-width: 80px;
  flex-shrink: 0;
}
.source-input {
  flex: 1;
}

.confidence-row {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-top: 12px;
  padding: 10px 14px;
  background: #fafbfc;
  border-radius: 6px;
  border: 1px solid #eee;
}
.confidence-label {
  font-size: 0.85rem;
  color: #666;
  min-width: 80px;
}
.confidence-bar {
  flex: 1;
}
.confidence-value {
  font-size: 0.85rem;
  font-weight: 600;
  font-variant-numeric: tabular-nums;
  min-width: 44px;
  text-align: right;
}

.record-form {
  background: #fff;
  border: 1px solid #eee;
  border-radius: 8px;
  padding: 0.5rem 1.5rem 1.5rem;
}
.record-form :deep(.el-divider--horizontal) {
  margin: 22px 0 14px;
}
.record-form :deep(.el-divider__text) {
  color: #444;
  font-weight: 600;
  font-size: 0.95rem;
  background: #fff;
  padding-left: 0;
}
.record-form :deep(.el-form-item__label) {
  font-size: 0.85rem;
  color: #555;
  padding: 0 0 4px;
  line-height: 1.4;
}

.actions {
  position: sticky;
  bottom: 0;
  margin-top: 1.25rem;
  padding: 0.85rem 1.5rem;
  background: #fff;
  border: 1px solid #eee;
  border-radius: 8px;
  display: flex;
  align-items: center;
  gap: 12px;
  z-index: 10;
  box-shadow: 0 -2px 12px rgba(0, 0, 0, 0.04);
}
.actions-spacer {
  flex: 1;
}
.last-error {
  flex: 1;
  font-size: 0.8rem;
  color: #b3261e;
  font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
</style>
