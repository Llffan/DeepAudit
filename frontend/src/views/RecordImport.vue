<script setup lang="ts">
import { ref, reactive, computed, watch } from 'vue';
import { useRouter } from 'vue-router';
import { ElMessage, type FormInstance, type UploadRequestOptions } from 'element-plus';
import { UploadFilled, ArrowLeft, Refresh, Check } from '@element-plus/icons-vue';
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
const mode = ref<'pdf' | 'manual'>('pdf');
const form = reactive<MedicalRecord>(emptyRecord());
const formRef = ref<FormInstance>();
const submitting = ref(false);
const extractingPdf = ref(false);
const extractionConfidence = ref<number | null>(null);
const sourcePdfPath = ref<string | null>(null);
const lastError = ref<string | null>(null);

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

async function uploadPdf(opts: UploadRequestOptions) {
  const file = opts.file as File;
  extractingPdf.value = true;
  lastError.value = null;
  try {
    const fd = new FormData();
    fd.append('file', file);
    const res = await fetch('/api/medical-records/import', {
      method: 'POST',
      body: fd,
    });
    if (!res.ok) throw new Error(`HTTP ${res.status} ${res.statusText}`);
    const body = (await res.json()) as {
      fields?: Partial<MedicalRecord>;
      extractionConfidence?: number;
      sourcePdfPath?: string;
    };
    if (body.fields) Object.assign(form, body.fields);
    extractionConfidence.value = body.extractionConfidence ?? null;
    sourcePdfPath.value = body.sourcePdfPath ?? null;
    ElMessage.success('PDF 抽取完成，请核对下方字段');
  } catch (err) {
    lastError.value = err instanceof Error ? err.message : String(err);
    ElMessage.error(`抽取失败：${lastError.value}`);
  } finally {
    extractingPdf.value = false;
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
    const saved = (await res.json()) as { id?: number };
    ElMessage.success(target === 'draft' ? '已保存为草稿' : '已确认，可进入规则检查');
    if (target === 'confirmed' && saved.id) {
      // Phase 4 will register /records/:id/results — for now just log.
      console.info('Saved confirmed record id:', saved.id);
    }
  } catch (err) {
    lastError.value = err instanceof Error ? err.message : String(err);
    ElMessage.error(`保存失败：${lastError.value}`);
  } finally {
    submitting.value = false;
  }
}

function reset() {
  Object.assign(form, emptyRecord());
  extractionConfidence.value = null;
  sourcePdfPath.value = null;
  lastError.value = null;
  formRef.value?.clearValidate();
  ElMessage.info('已清空表单');
}
</script>

<template>
  <div class="page">
    <header class="topbar">
      <el-button :icon="ArrowLeft" link @click="router.push('/')">返回首页</el-button>
      <h1>病案首页录入</h1>
      <div class="progress-pill">
        <span class="progress-num">{{ fillRatio.filled }} / {{ fillRatio.total }}</span>
        <span class="progress-pct">{{ fillPercent }}%</span>
      </div>
    </header>

    <section class="mode-switch">
      <el-segmented
        v-model="mode"
        :options="[
          { label: 'PDF 自动抽取', value: 'pdf' },
          { label: '手动录入', value: 'manual' },
        ]"
      />
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
            单个 PDF 文件 · 调用多模态 LLM（qwen-vl-max）自动抽取字段并填入下方表单
          </p>
        </template>
      </el-upload>

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
  margin-bottom: 1.25rem;
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
