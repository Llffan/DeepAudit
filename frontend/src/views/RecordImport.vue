<script setup lang="ts">
import { ref, reactive, computed, watch, onBeforeUnmount, onMounted } from 'vue';
import { useRouter, useRoute } from 'vue-router';
import { ElMessage, type FormInstance, type UploadRequestOptions } from 'element-plus';
import { UploadFilled, ArrowLeft, Refresh, Check, MagicStick, Files, ChatRound, Printer } from '@element-plus/icons-vue';
import TestingAssistantDialog from '@/components/TestingAssistantDialog.vue';
import {
  emptyRecord,
  emptyOtherDiagnosis,
  GENDER_OPTIONS,
  ICD_VER_OPTIONS,
  ADMISSION_ROUTE_OPTIONS,
  DISCHARGE_STATUS_OPTIONS,
  ANESTHESIA_OPTIONS,
  ID_CARD_TYPE_OPTIONS,
  MARITAL_STATUS_OPTIONS,
  CONTACT_RELATION_OPTIONS,
  ADMISSION_CONDITION_OPTIONS,
  DISCHARGE_CONDITION_OPTIONS,
  BUSINESS_FIELDS,
  type MedicalRecord,
} from '@/types/medicalRecord';

const router = useRouter();
const route = useRoute();
const mode = ref<'pdf' | 'manual'>('pdf');
const assistantRef = ref<InstanceType<typeof TestingAssistantDialog> | null>(null);
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
    ensureMinDiagnoses();
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
      ensureMinDiagnoses();
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
    // 网格里 19 个其他诊断槽位多数为空；提交前过滤掉 diagnosisName 为空的行，
    // 并按过滤后的位置重排 seqNo 让落库的序号连续。
    const cleanedDiagnoses = (form.diagnoses ?? [])
      .filter((d) => d.diagnosisName && d.diagnosisName.trim() !== '')
      .map((d, i) => ({ ...d, seqNo: i + 1 }));

    const payload = {
      ...form,
      diagnoses: cleanedDiagnoses,
      otherDiagnosisCount: cleanedDiagnoses.length,
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
    ensureMinDiagnoses();
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

// ─── 诊断网格（4 列 × 动态行数；主诊固定第 1 行 + N 条其他诊断） ─────
//
// 设计：
//  - 表头 4 列：出院诊断 / 疾病编码 / 入院病情 / 出院情况（+ 操作）
//  - 第 1 行固定主诊，绑 form.mainDiagnosis* 平铺字段（不可删除）
//  - 之后按 form.diagnoses[] 动态渲染，每行末尾"删除"按钮
//  - 底部"+ 新增其他诊断"按钮 push 一个空槽到数组
//  - 初始预填 INITIAL_OTHER_ROWS 个空"其他诊断"槽（让初始视觉感是 4 行）
//  - save() 提交前过滤空诊断行 + 重排 seqNo（已在 save 函数中处理）

const INITIAL_OTHER_ROWS = 3;   // 初始 3 个空其他诊断 + 1 主诊 = 4 行

function ensureMinDiagnoses() {
  if (!Array.isArray(form.diagnoses)) form.diagnoses = [];
  while (form.diagnoses.length < INITIAL_OTHER_ROWS) {
    form.diagnoses.push(emptyOtherDiagnosis(form.diagnoses.length + 1));
  }
}

function addOtherDiagnosis() {
  if (!Array.isArray(form.diagnoses)) form.diagnoses = [];
  form.diagnoses.push(emptyOtherDiagnosis(form.diagnoses.length + 1));
  form.otherDiagnosisCount = form.diagnoses.length;
}

function removeOtherDiagnosis(idx: number) {
  form.diagnoses.splice(idx, 1);
  form.diagnoses.forEach((d, i) => (d.seqNo = i + 1));
  form.otherDiagnosisCount = form.diagnoses.length;
}

// ─── PDF 导出（前端浏览器打印） ────────────────────────────────────────
//
// 走浏览器原生 window.print() 路径：开新窗口写入纯 HTML 表格 → 调 print()
// → 用户在打印对话框选"另存为 PDF"。
//
// 选这个路径而非 jsPDF/pdfmake：
//  - 中文字体直接用系统宋体，不需要嵌入字体文件（~5MB）
//  - 零新依赖
//  - 黑色细线框 + 字段:值 是简单 HTML <table border> 就能表达的样式
function escapeHtml(s: unknown): string {
  if (s === null || s === undefined || s === '') return '&nbsp;';
  return String(s)
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;');
}

function row2(l1: string, v1: unknown, l2: string, v2: unknown): string {
  return `<tr><th>${l1}</th><td>${escapeHtml(v1)}</td><th>${l2}</th><td>${escapeHtml(v2)}</td></tr>`;
}
function row1(label: string, value: unknown, span = 3): string {
  return `<tr><th>${label}</th><td colspan="${span}">${escapeHtml(value)}</td></tr>`;
}
// 小标题已按需求移除，section 仅作为内容分组工具，输出纯表格
function section(_title: string, body: string): string {
  return `<table>${body}</table>`;
}

// 14 行布局每行 1 个独立 <table>，相邻表通过 CSS 负 margin 黏合。
// 每对 [label, value, weight?] —— weight 默认 1，按 weight 比例分配 td 列宽，
// 让长字段（地址、诊断名）占更宽空间、短字段（邮编、年龄）占更窄空间。
// th 列宽由 CSS 自动按字段名长度收缩（width:1px + nowrap = shrink-to-fit）。
type RowPair = [string, unknown] | [string, unknown, number];
function rowTable(...pairs: RowPair[]): string {
  const weights = pairs.map((p) => (typeof p[2] === 'number' ? p[2] : 1));
  const total = weights.reduce((s, w) => s + w, 0);
  const cells = pairs
    .map(([l, v], i) => {
      const tdPct = (weights[i] / total) * 100;
      return `<th>${l}</th><td style="width:${tdPct.toFixed(2)}%">${escapeHtml(v)}</td>`;
    })
    .join('');
  return `<table class="row"><tr>${cells}</tr></table>`;
}

function buildPrintHtml(r: MedicalRecord): string {
  // 病案号头（必填主键，单独一行）— 来源医院字段较长给 weight 4
  const head = rowTable(['病案号', r.recordNo, 1], ['来源医院', r.sourceHospital, 4]);

  // 严格按用户列出的 14 行布局，行 9/13 跳过；行 1~14 每行一个 rowTable
  // weight 按字段值的常见字符长度分配（例如：地址 5、诊断名 3、邮编 1）
  const r1  = rowTable(['姓名', r.name, 3], ['性别', r.gender, 1], ['出生日期', r.birthDate, 2], ['年龄', r.age, 1], ['国籍', r.nationality, 2]);
  const r2  = rowTable(['(不足1岁的)年龄(天)', r.ageDays, 1], ['新生儿出生体重 (g)', r.newbornBirthWeight, 1], ['新生儿入院体重 (g)', r.newbornAdmissionWeight, 1]);
  const r3  = rowTable(['出生地', r.birthPlace, 2], ['籍贯', r.nativePlace, 2], ['民族', r.ethnicity, 1]);
  const r4  = rowTable(['证件类型', r.idCardType, 2], ['证件号', r.idCardMasked, 4], ['职业', r.occupation, 2], ['婚姻', r.maritalStatus, 1]);
  const r5  = rowTable(['现住址', r.currentAddress, 5], ['电话', r.currentPhone, 2], ['邮编', r.currentZip, 1]);
  const r6  = rowTable(['户口地址', r.registeredAddress, 5], ['邮编', r.registeredZip, 1]);
  const r7  = rowTable(['工作单位及地址', r.workplace, 5], ['单位电话', r.workPhone, 2], ['邮编', r.workZip, 1]);
  const r8  = rowTable(['联系人姓名', r.contactName, 2], ['关系', r.contactRelation, 1], ['地址', r.contactAddress, 4], ['电话', r.contactPhone, 2]);
  const r10 = rowTable(['入院途径', r.admissionRoute, 1]);
  const r11 = rowTable(['入院时间', r.admissionDate, 2], ['入院科别', r.admissionDept, 2], ['病房', r.admissionWard, 1], ['转科科别', r.specialtyDept, 2]);
  const r12 = rowTable(['出院时间', r.dischargeDate, 2], ['出院科别', r.dischargeDept, 2], ['病房', r.dischargeWard, 1], ['实际住院(天)', r.lengthOfStay, 1]);
  const r14 = rowTable(['门(急)诊诊断', r.outpatientDiagnosis, 3], ['疾病编码', r.outpatientDiagnosisCode, 2], ['入院情况', r.outpatientAdmissionCondition, 1], ['入院后确诊日期', r.confirmedAfterAdmissionDate, 2]);
  const upper = head + r1 + r2 + r3 + r4 + r5 + r6 + r7 + r8 + r10 + r11 + r12 + r14;

  // ─── 出院诊断网格（4 列 × 动态行，与前端同布局）──────────────────────
  // 行 1 = 主诊；行 2..N = r.diagnoses[]（已 filter 空行 / 提交前已 reseq）
  // PDF 也只渲染有内容的诊断行（filter diagnosisName 空白）；保证打印简洁
  const diagRows: Array<[string | null, string | null, string | null, string | null]> = [];
  if (r.mainDiagnosisName && r.mainDiagnosisName.trim() !== '') {
    diagRows.push([r.mainDiagnosisName, r.mainDiagnosisCode, r.mainAdmissionCondition, r.mainDischargeCondition]);
  }
  for (const d of r.diagnoses ?? []) {
    if (!d.diagnosisName || d.diagnosisName.trim() === '') continue;
    diagRows.push([d.diagnosisName, d.diagnosisCode, d.admissionCondition, d.dischargeCondition]);
  }
  // 至少打印一行空白以保留视觉节奏（避免诊断网格塌成只有表头）
  if (diagRows.length === 0) diagRows.push([null, null, null, null]);

  const diagBody = diagRows.map(([name, code, adm, dis]) => `<tr>
    <td>${escapeHtml(name)}</td>
    <td>${escapeHtml(code)}</td>
    <td>${escapeHtml(adm)}</td>
    <td>${escapeHtml(dis)}</td>
  </tr>`).join('');

  const diagGrid = `<table class="diag">
    <thead>
      <tr>
        <th>出院诊断</th><th>疾病编码</th><th>入院病情</th><th>出院情况</th>
      </tr>
    </thead>
    <tbody>${diagBody}</tbody>
  </table>`;

  const diagLegend = `<table class="legend"><tr>
    <td><b>入院病情：</b>1.有 &nbsp; 2.临床未确定 &nbsp; 3.情况不明 &nbsp; 4.无</td>
    <td><b>出院情况：</b>1.治愈 &nbsp; 2.好转 &nbsp; 3.未愈 &nbsp; 4.死亡 &nbsp; 5.其他</td>
  </tr></table>`;

  const mainDiag = diagGrid + diagLegend;
  const otherDiag = '';   // 已合并到 diagGrid 中

  const operation = section('主要手术',
    row2('主手术编码', r.mainOperationCode, '主手术名称', r.mainOperationName) +
    row2('手术日期', r.operationDate, '麻醉方式', r.anesthesiaMethod) +
    row1('手术医生', r.operator),
  );

  const cost = section('费用',
    row2('总费用 (¥)', r.totalCost, '药品费 (¥)', r.drugCost) +
    row2('手术费 (¥)', r.operationCost, '医疗服务费 (¥)', r.medicalServiceCost),
  );

  return `<!DOCTYPE html>
<html lang="zh">
<head>
<meta charset="UTF-8">
<title>住院病案首页 ${escapeHtml(r.recordNo)}</title>
<style>
  body { font-family: 'SimSun', '宋体', serif; color: #000; padding: 20px; font-size: 12px; line-height: 1.4; }
  h1 { font-size: 18px; text-align: center; margin: 0 0 16px; letter-spacing: 4px; }
  /* 区块标题已按需求移除，所有 <table> 上下直接拼接，靠 1px 黑线区分 */
  table { width: 100%; border-collapse: collapse; margin-bottom: 0; border-top: none; table-layout: auto; }
  table + table { margin-top: -1px; }      /* 相邻 table 共享一条黑线，视觉上形成连续表格 */
  th, td { border: 1px solid #000; padding: 4px 8px; vertical-align: middle; height: 22px; }
  th { font-weight: 600; background: #fff; text-align: left; }
  thead th { text-align: center; }

  /* 14 行 row 表：th 列宽自动按字段名长度收缩；td 列宽由 inline style 按 weight 分配；
     单行高度恒定，超长内容自动省略号截断（不换行、不撑高） */
  table.row { table-layout: auto; }
  table.row th {
    width: 1%;                    /* + nowrap = shrink-to-fit；浏览器按 th 内容宽度自动收缩 */
    white-space: nowrap;
    padding: 4px 10px 4px 8px;
  }
  table.row td {
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
  }

  /* 诊断网格：4 列按内容长短分配（出院诊断 40% / 疾病编码 20% / 入院病情 20% / 出院情况 20%） */
  table.diag { table-layout: fixed; }
  table.diag th, table.diag td {
    text-align: left; padding: 4px 6px;
    white-space: nowrap; overflow: hidden; text-overflow: ellipsis;
  }
  table.diag th:nth-child(1), table.diag td:nth-child(1) { width: 40%; }
  table.diag th:nth-child(2), table.diag td:nth-child(2) { width: 20%; }
  table.diag th:nth-child(3), table.diag td:nth-child(3) { width: 20%; }
  table.diag th:nth-child(4), table.diag td:nth-child(4) { width: 20%; }
  table.diag thead th { text-align: center; background: #f5f5f5; font-weight: 600; }

  /* 字典脚注：紧贴诊断网格下方，无外框、左右两列 */
  table.legend { border: none; margin-top: 4px; }
  table.legend td { border: none; padding: 2px 6px; font-size: 11px; color: #333; width: 50%; height: auto; }
  @media print {
    body { padding: 0; }
    h1 { margin-bottom: 12px; }
  }
</style>
</head>
<body>
<h1>住院病案首页</h1>
${upper}
${mainDiag}
${otherDiag}
${operation}
${cost}
</body>
</html>`;
}

function exportPdf() {
  const html = buildPrintHtml(form);
  const w = window.open('', '_blank', 'width=900,height=1000');
  if (!w) {
    ElMessage.warning('浏览器拦截了新窗口，请允许弹窗后重试');
    return;
  }
  w.document.open();
  w.document.write(html);
  w.document.close();
  // 等 DOM 加载完毕再触发打印；某些浏览器立即调用会拿不到字体度量
  const triggerPrint = () => {
    try {
      w.focus();
      w.print();
    } catch {
      // ignore
    }
  };
  if (w.document.readyState === 'complete') {
    setTimeout(triggerPrint, 100);
  } else {
    w.addEventListener('load', () => setTimeout(triggerPrint, 100));
  }
}

function reset() {
  if (recordId.value != null) {
    void loadRecord(recordId.value);
    ElMessage.info('已从服务器重新加载');
    return;
  }
  Object.assign(form, emptyRecord());
  ensureMinDiagnoses();
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
  ensureMinDiagnoses();
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
      <div class="mode-actions">
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
        <el-button
          :icon="ChatRound"
          plain
          size="default"
          @click="assistantRef?.open()"
        >
          测试助手
        </el-button>
      </div>
    </section>

    <TestingAssistantDialog ref="assistantRef" />

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
            单个 PDF · ≤ 10 MB · 调用 DeepSeek V3 自动抽取 57 个 HQMS 国标字段
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
      label-position="left"
      label-width="auto"
      class="record-form"
    >
      <!-- 病案号 + 来源医院（必填头，固定在 14 行布局之前） -->
      <el-row :gutter="16">
        <el-col :span="8">
          <el-form-item label="病案号" prop="recordNo" required>
            <el-input v-model="form.recordNo" placeholder="必填" clearable />
          </el-form-item>
        </el-col>
        <el-col :span="16">
          <el-form-item label="来源医院">
            <el-input v-model="form.sourceHospital" placeholder="可选" clearable />
          </el-form-item>
        </el-col>
      </el-row>

      <!-- 行 1: 姓名 / 性别 / 出生日期 / 年龄 / 国籍 -->
      <el-row :gutter="16">
        <el-col :span="5">
          <el-form-item label="姓名">
            <el-input v-model="form.name" placeholder="患者姓名" clearable />
          </el-form-item>
        </el-col>
        <el-col :span="4">
          <el-form-item label="性别">
            <el-select v-model="form.gender" clearable placeholder="">
              <el-option v-for="o in GENDER_OPTIONS" :key="o.value" :label="o.label" :value="o.value" />
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
        <el-col :span="4">
          <el-form-item label="年龄">
            <el-input-number v-model="form.age" :min="0" :max="150" :controls="false" style="width: 100%" />
          </el-form-item>
        </el-col>
        <el-col :span="6">
          <el-form-item label="国籍">
            <el-input v-model="form.nationality" placeholder="如 中国" clearable />
          </el-form-item>
        </el-col>
      </el-row>

      <!-- 行 2: (年龄不足一周岁的)年龄_天 / 新生儿出生体重_g / 新生儿入院体重_g -->
      <el-row :gutter="16">
        <el-col :span="8">
          <el-form-item label="(不足1岁的)年龄(天)">
            <el-input-number v-model="form.ageDays" :min="0" :max="364" :controls="false" style="width: 100%" />
          </el-form-item>
        </el-col>
        <el-col :span="8">
          <el-form-item label="新生儿出生体重 (g)">
            <el-input-number v-model="form.newbornBirthWeight" :min="0" :max="10000" :controls="false" style="width: 100%" />
          </el-form-item>
        </el-col>
        <el-col :span="8">
          <el-form-item label="新生儿入院体重 (g)">
            <el-input-number v-model="form.newbornAdmissionWeight" :min="0" :max="10000" :controls="false" style="width: 100%" />
          </el-form-item>
        </el-col>
      </el-row>

      <!-- 行 3: 出生地 / 籍贯 / 民族 -->
      <el-row :gutter="16">
        <el-col :span="8">
          <el-form-item label="出生地">
            <el-input v-model="form.birthPlace" placeholder="省市县" clearable />
          </el-form-item>
        </el-col>
        <el-col :span="8">
          <el-form-item label="籍贯">
            <el-input v-model="form.nativePlace" placeholder="省市" clearable />
          </el-form-item>
        </el-col>
        <el-col :span="8">
          <el-form-item label="民族">
            <el-input v-model="form.ethnicity" placeholder="如 汉族" clearable />
          </el-form-item>
        </el-col>
      </el-row>

      <!-- 行 4: 证件类型 / 证件号 / 职业 / 婚姻 -->
      <el-row :gutter="16">
        <el-col :span="5">
          <el-form-item label="证件类型">
            <el-select v-model="form.idCardType" clearable placeholder="">
              <el-option v-for="o in ID_CARD_TYPE_OPTIONS" :key="o.value" :label="o.label" :value="o.value" />
            </el-select>
          </el-form-item>
        </el-col>
        <el-col :span="7">
          <el-form-item label="证件号">
            <el-input v-model="form.idCardMasked" placeholder="如 110101********0011" clearable />
          </el-form-item>
        </el-col>
        <el-col :span="6">
          <el-form-item label="职业">
            <el-input v-model="form.occupation" clearable />
          </el-form-item>
        </el-col>
        <el-col :span="6">
          <el-form-item label="婚姻">
            <el-select v-model="form.maritalStatus" clearable placeholder="">
              <el-option v-for="o in MARITAL_STATUS_OPTIONS" :key="o.value" :label="o.label" :value="o.value" />
            </el-select>
          </el-form-item>
        </el-col>
      </el-row>

      <!-- 行 5: 现住址 / 电话 / 邮编 -->
      <el-row :gutter="16">
        <el-col :span="12">
          <el-form-item label="现住址">
            <el-input v-model="form.currentAddress" clearable />
          </el-form-item>
        </el-col>
        <el-col :span="7">
          <el-form-item label="电话">
            <el-input v-model="form.currentPhone" clearable />
          </el-form-item>
        </el-col>
        <el-col :span="5">
          <el-form-item label="邮编">
            <el-input v-model="form.currentZip" clearable />
          </el-form-item>
        </el-col>
      </el-row>

      <!-- 行 6: 户口地址 / 邮编 -->
      <el-row :gutter="16">
        <el-col :span="18">
          <el-form-item label="户口地址">
            <el-input v-model="form.registeredAddress" clearable />
          </el-form-item>
        </el-col>
        <el-col :span="6">
          <el-form-item label="邮编">
            <el-input v-model="form.registeredZip" clearable />
          </el-form-item>
        </el-col>
      </el-row>

      <!-- 行 7: 工作单位及地址 / 单位电话 / 邮编 -->
      <el-row :gutter="16">
        <el-col :span="12">
          <el-form-item label="工作单位及地址">
            <el-input v-model="form.workplace" clearable />
          </el-form-item>
        </el-col>
        <el-col :span="7">
          <el-form-item label="单位电话">
            <el-input v-model="form.workPhone" clearable />
          </el-form-item>
        </el-col>
        <el-col :span="5">
          <el-form-item label="邮编">
            <el-input v-model="form.workZip" clearable />
          </el-form-item>
        </el-col>
      </el-row>

      <!-- 行 8: 联系人姓名 / 关系 / 地址 / 电话 -->
      <el-row :gutter="16">
        <el-col :span="5">
          <el-form-item label="联系人姓名">
            <el-input v-model="form.contactName" clearable />
          </el-form-item>
        </el-col>
        <el-col :span="4">
          <el-form-item label="关系">
            <el-select v-model="form.contactRelation" clearable placeholder="">
              <el-option v-for="o in CONTACT_RELATION_OPTIONS" :key="o.value" :label="o.label" :value="o.value" />
            </el-select>
          </el-form-item>
        </el-col>
        <el-col :span="8">
          <el-form-item label="地址">
            <el-input v-model="form.contactAddress" clearable />
          </el-form-item>
        </el-col>
        <el-col :span="7">
          <el-form-item label="电话">
            <el-input v-model="form.contactPhone" clearable />
          </el-form-item>
        </el-col>
      </el-row>

      <!-- 行 10: 入院途径 -->
      <el-row :gutter="16">
        <el-col :span="12">
          <el-form-item label="入院途径">
            <el-select v-model="form.admissionRoute" clearable placeholder="">
              <el-option v-for="o in ADMISSION_ROUTE_OPTIONS" :key="o.value" :label="o.label" :value="o.value" />
            </el-select>
          </el-form-item>
        </el-col>
        <el-col :span="12" />
      </el-row>

      <!-- 行 11: 入院时间 / 入院科别 / 病房 / 转科科别 -->
      <el-row :gutter="16">
        <el-col :span="6">
          <el-form-item label="入院时间">
            <el-date-picker v-model="form.admissionDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
          </el-form-item>
        </el-col>
        <el-col :span="6">
          <el-form-item label="入院科别">
            <el-input v-model="form.admissionDept" clearable />
          </el-form-item>
        </el-col>
        <el-col :span="6">
          <el-form-item label="病房">
            <el-input v-model="form.admissionWard" placeholder="如 心内一病区" clearable />
          </el-form-item>
        </el-col>
        <el-col :span="6">
          <el-form-item label="转科科别">
            <el-input v-model="form.specialtyDept" clearable />
          </el-form-item>
        </el-col>
      </el-row>

      <!-- 行 12: 出院时间 / 出院科别 / 病房 / 实际住院_天 -->
      <el-row :gutter="16">
        <el-col :span="6">
          <el-form-item label="出院时间">
            <el-date-picker v-model="form.dischargeDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
          </el-form-item>
        </el-col>
        <el-col :span="6">
          <el-form-item label="出院科别">
            <el-input v-model="form.dischargeDept" clearable />
          </el-form-item>
        </el-col>
        <el-col :span="6">
          <el-form-item label="病房">
            <el-input v-model="form.dischargeWard" clearable />
          </el-form-item>
        </el-col>
        <el-col :span="6">
          <el-form-item label="实际住院(天)">
            <el-input-number v-model="form.lengthOfStay" :min="0" :controls="false" style="width: 100%" />
          </el-form-item>
        </el-col>
      </el-row>

      <!-- 行 14: 门(急)诊诊断 / 疾病编码 / 入院情况 / 入院后确诊日期 -->
      <el-row :gutter="16">
        <el-col :span="8">
          <el-form-item label="门(急)诊诊断">
            <el-input v-model="form.outpatientDiagnosis" clearable />
          </el-form-item>
        </el-col>
        <el-col :span="6">
          <el-form-item label="疾病编码">
            <el-input v-model="form.outpatientDiagnosisCode" clearable />
          </el-form-item>
        </el-col>
        <el-col :span="4">
          <el-form-item label="入院情况">
            <el-select v-model="form.outpatientAdmissionCondition" clearable placeholder="">
              <el-option v-for="o in ADMISSION_CONDITION_OPTIONS" :key="o.value" :label="o.label" :value="o.value" />
            </el-select>
          </el-form-item>
        </el-col>
        <el-col :span="6">
          <el-form-item label="入院后确诊日期">
            <el-date-picker v-model="form.confirmedAfterAdmissionDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
          </el-form-item>
        </el-col>
      </el-row>

      <!-- 出院诊断网格：4 列 × 动态行
           行 1 = 主诊（绑 form.mainDiagnosis*，不可删除）
           行 2..N = form.diagnoses[]；可点"+ 新增其他诊断"加新行 -->
      <table class="diag-grid">
        <thead>
          <tr>
            <th>出院诊断</th>
            <th>疾病编码</th>
            <th>入院病情</th>
            <th>出院情况</th>
            <th class="op-col">操作</th>
          </tr>
        </thead>
        <tbody>
          <!-- 主诊（固定第 1 行） -->
          <tr>
            <td>
              <el-input
                size="small"
                :model-value="form.mainDiagnosisName"
                @update:model-value="(v) => form.mainDiagnosisName = v || null"
                placeholder="主要诊断"
              />
            </td>
            <td>
              <el-input
                size="small"
                :model-value="form.mainDiagnosisCode"
                @update:model-value="(v) => form.mainDiagnosisCode = v || null"
              />
            </td>
            <td>
              <el-select
                size="small"
                :model-value="form.mainAdmissionCondition"
                @update:model-value="(v) => form.mainAdmissionCondition = v || null"
                clearable
                placeholder=""
              >
                <el-option v-for="o in ADMISSION_CONDITION_OPTIONS" :key="o.value" :label="o.label" :value="o.value" />
              </el-select>
            </td>
            <td>
              <el-select
                size="small"
                :model-value="form.mainDischargeCondition"
                @update:model-value="(v) => form.mainDischargeCondition = v || null"
                clearable
                placeholder=""
              >
                <el-option v-for="o in DISCHARGE_CONDITION_OPTIONS" :key="o.value" :label="o.label" :value="o.value" />
              </el-select>
            </td>
            <td class="op-col"><span class="dim">主诊</span></td>
          </tr>

          <!-- 其他诊断（动态行） -->
          <tr v-for="(d, idx) in form.diagnoses" :key="idx">
            <td><el-input size="small" v-model="d.diagnosisName" /></td>
            <td><el-input size="small" v-model="d.diagnosisCode" /></td>
            <td>
              <el-select size="small" v-model="d.admissionCondition" clearable placeholder="">
                <el-option v-for="o in ADMISSION_CONDITION_OPTIONS" :key="o.value" :label="o.label" :value="o.value" />
              </el-select>
            </td>
            <td>
              <el-select size="small" v-model="d.dischargeCondition" clearable placeholder="">
                <el-option v-for="o in DISCHARGE_CONDITION_OPTIONS" :key="o.value" :label="o.label" :value="o.value" />
              </el-select>
            </td>
            <td class="op-col">
              <el-button link type="danger" size="small" @click="removeOtherDiagnosis(idx)">删除</el-button>
            </td>
          </tr>
        </tbody>
      </table>
      <div class="diag-actions">
        <el-button size="small" @click="addOtherDiagnosis">+ 新增其他诊断</el-button>
      </div>
      <div class="diag-legend">
        <span><b>入院病情：</b>1.有 &nbsp; 2.临床未确定 &nbsp; 3.情况不明 &nbsp; 4.无</span>
        <span><b>出院情况：</b>1.治愈 &nbsp; 2.好转 &nbsp; 3.未愈 &nbsp; 4.死亡 &nbsp; 5.其他</span>
      </div>

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
            <el-select v-model="form.anesthesiaMethod" clearable placeholder="">
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
      <el-button :icon="Printer" @click="exportPdf" :disabled="submitting">导出 PDF</el-button>
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
.mode-actions {
  display: flex;
  gap: 8px;
  flex-shrink: 0;
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
  padding: 0.4rem 1.25rem 1rem;
}
/* el-form-item 行间距：8px → 12px，每行视觉节奏更舒展 */
.record-form :deep(.el-form-item) {
  margin-bottom: 12px;
}
/* 诊断网格：10 行 × 2 列固定布局，左 4 列 + 右 4 列 */
.diag-grid {
  width: 100%;
  border-collapse: collapse;
  margin: 8px 0;
  table-layout: fixed;
}
.diag-grid th,
.diag-grid td {
  border: 1px solid #d0d7de;
  padding: 2px;
  vertical-align: middle;
}
.diag-grid thead th {
  background: #f5f6f8;
  font-size: 0.78rem;
  font-weight: 600;
  color: #444;
  text-align: center;
  padding: 6px 2px;
}
/* 4 主列等宽，操作列固定窄 */
.diag-grid th, .diag-grid td { width: 24%; }
.diag-grid .op-col { width: 4%; text-align: center; }
.diag-grid :deep(.el-input__wrapper),
.diag-grid :deep(.el-select__wrapper) {
  box-shadow: none;
  background: transparent;
  padding: 2px 6px;
  min-height: 26px;
}
.diag-grid :deep(.el-input__inner),
.diag-grid :deep(.el-select__placeholder) {
  font-size: 0.82rem;
}
.diag-actions {
  margin: 6px 0 4px;
}
.diag-grid .op-col .dim {
  color: #aaa;
  font-size: 0.78rem;
}
.diag-legend {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  margin: 4px 4px 18px;
  font-size: 0.78rem;
  color: #555;
}
.diag-legend b { color: #333; font-weight: 600; }
.record-form :deep(.el-divider--horizontal) {
  margin: 12px 0 8px;
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
  padding-right: 10px;         /* label 与输入框固定 10px 间距 */
  line-height: 1.3;
  white-space: nowrap;         /* 字段名不换行：长字段如"工作单位及地址"保持单行显示 */
}
/* 非首列：标签按自身内容收缩、紧贴输入框；
   首列保留 label-width="auto" 计算出的统一宽度以维持纵向对齐。 */
.record-form :deep(.el-row > .el-col:not(:first-child) .el-form-item__label) {
  width: auto !important;
  min-width: 0 !important;
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
