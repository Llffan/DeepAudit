<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Edit, Delete, Switch, Cpu, MagicStick } from '@element-plus/icons-vue'

interface OperatorTemplate {
  id: number
  code: string
  name: string
  description: string | null
  parameterNames: string[]
  bodyDsl: unknown
  enabled: boolean
  createdAt: string | null
  updatedAt: string | null
}

// 病案对象可用作算子参数的字段（与后端 FieldAccessor 白名单一致）。
// 分组方便在下拉里快速定位；value 是 camelCase 字段名，label 是中文展示。
interface FieldOption { value: string; label: string }
interface FieldGroup  { group: string; options: FieldOption[] }
const FIELD_GROUPS: FieldGroup[] = [
  { group: '基本信息', options: [
    { value: 'recordNo',     label: '病案号' },
    { value: 'name',         label: '姓名' },
    { value: 'gender',       label: '性别' },
    { value: 'birthDate',    label: '出生日期' },
    { value: 'age',          label: '年龄' },
    { value: 'idCardMasked', label: '证件号' },
    { value: 'idCardType',   label: '证件类型' },
    { value: 'nationality',  label: '国籍' },
    { value: 'ethnicity',    label: '民族' },
    { value: 'maritalStatus',label: '婚姻' },
    { value: 'occupation',   label: '职业' },
    { value: 'birthPlace',   label: '出生地' },
    { value: 'nativePlace',  label: '籍贯' },
    { value: 'ageDays',      label: '(不足1岁的)年龄(天)' },
    { value: 'newbornBirthWeight',     label: '新生儿出生体重 (g)' },
    { value: 'newbornAdmissionWeight', label: '新生儿入院体重 (g)' },
  ]},
  { group: '住址与联系', options: [
    { value: 'currentAddress',    label: '现住址' },
    { value: 'currentPhone',      label: '电话' },
    { value: 'currentZip',        label: '邮编' },
    { value: 'registeredAddress', label: '户口地址' },
    { value: 'registeredZip',     label: '户口邮编' },
    { value: 'workplace',         label: '工作单位及地址' },
    { value: 'workPhone',         label: '单位电话' },
    { value: 'workZip',           label: '单位邮编' },
    { value: 'contactName',       label: '联系人姓名' },
    { value: 'contactRelation',   label: '联系人关系' },
    { value: 'contactAddress',    label: '联系人地址' },
    { value: 'contactPhone',      label: '联系人电话' },
  ]},
  { group: '入出院', options: [
    { value: 'admissionDate',  label: '入院时间' },
    { value: 'dischargeDate',  label: '出院时间' },
    { value: 'lengthOfStay',   label: '实际住院(天)' },
    { value: 'admissionDept',  label: '入院科别' },
    { value: 'dischargeDept',  label: '出院科别' },
    { value: 'admissionRoute', label: '入院途径' },
    { value: 'dischargeStatus',label: '离院方式' },
    { value: 'admissionWard',  label: '入院病房' },
    { value: 'dischargeWard',  label: '出院病房' },
    { value: 'specialtyDept',  label: '转科科别' },
  ]},
  { group: '门急诊与诊断', options: [
    { value: 'outpatientDiagnosis',          label: '门(急)诊诊断' },
    { value: 'outpatientDiagnosisCode',      label: '门(急)诊疾病编码' },
    { value: 'outpatientAdmissionCondition', label: '门(急)诊入院情况' },
    { value: 'confirmedAfterAdmissionDate',  label: '入院后确诊日期' },
    { value: 'mainDiagnosisCode',            label: '主诊编码' },
    { value: 'mainDiagnosisName',            label: '主诊名称' },
    { value: 'mainDiagnosisIcdVer',          label: '主诊 ICD 版本' },
    { value: 'mainAdmissionCondition',       label: '主诊入院病情' },
    { value: 'mainDischargeCondition',       label: '主诊出院情况' },
    { value: 'mainNote',                     label: '主诊备注' },
    { value: 'otherDiagnosisCount',          label: '其他诊断数' },
  ]},
  { group: 'V9 补充字段', options: [
    { value: 'injuryPoisoningCause',      label: '损伤、中毒外因' },
    { value: 'injuryPoisoningCode',       label: '损伤、中毒编码' },
    { value: 'pathologicalDiagnosis',     label: '病理诊断' },
    { value: 'pathologicalDiagnosisCode', label: '病理编码' },
    { value: 'pathologyNumber',           label: '病理号' },
    { value: 'drugAllergy',               label: '药物过敏' },
    { value: 'allergyDrugs',              label: '过敏药物' },
    { value: 'autopsy',                   label: '死亡患者尸检' },
    { value: 'bloodType',                 label: '血型' },
    { value: 'rhBloodType',               label: 'Rh' },
    { value: 'departmentDirector',        label: '科主任' },
    { value: 'chiefPhysician',            label: '主(副主)任医生' },
    { value: 'attendingPhysician',        label: '主治医生' },
    { value: 'residentPhysician',         label: '住院医生' },
    { value: 'responsibleNurse',          label: '责任护士' },
    { value: 'traineePhysician',          label: '进修医生' },
    { value: 'internPhysician',           label: '实习医生' },
    { value: 'coder',                     label: '编码员' },
    { value: 'recordQuality',             label: '病案质量' },
    { value: 'qcPhysician',               label: '质控医师' },
    { value: 'qcNurse',                   label: '质控护士' },
    { value: 'qcDate',                    label: '质控日期' },
  ]},
  { group: '元信息', options: [
    { value: 'sourceHospital',       label: '来源医院' },
    { value: 'extractionConfidence', label: '抽取置信度' },
  ]},
]
const FIELD_LABEL: Record<string, string> = Object.fromEntries(
  FIELD_GROUPS.flatMap(g => g.options.map(o => [o.value, o.label] as const))
)

const BUILTIN_OPS = [
  { op: 'notNull',      kind: '布尔', params: 'field',                desc: '字段不为空' },
  { op: 'isNull',       kind: '布尔', params: 'field',                desc: '字段为空' },
  { op: 'eq / ne',      kind: '布尔', params: 'field, rhs',           desc: '等于 / 不等于' },
  { op: 'gt / gte',     kind: '布尔', params: 'field, rhs',           desc: '大于 / 大于等于' },
  { op: 'lt / lte',     kind: '布尔', params: 'field, rhs',           desc: '小于 / 小于等于' },
  { op: 'dateBefore',   kind: '布尔', params: 'field, rhs',           desc: '日期早于 rhs' },
  { op: 'dateAfter',    kind: '布尔', params: 'field, rhs',           desc: '日期晚于 rhs' },
  { op: 'and / or',     kind: '布尔', params: 'args[]',               desc: '逻辑与 / 或（数组）' },
  { op: 'not',          kind: '布尔', params: 'arg',                  desc: '逻辑非' },
  { op: 'custom',         kind: '布尔', params: 'code, args{}',         desc: '调用自定义算子模板' },
  { op: 'icdCodeExists',  kind: '布尔', params: 'field, category',          desc: '字段值是否在 ICD 字典 (icd9cm3/icd10) 中' },
  { op: 'icdNameMatches', kind: '布尔', params: 'codeField, nameField, category', desc: 'code 对应字典标准名是否与 name 字段一致（空白折叠）' },
  { op: 'dateDiffDays',   kind: '值',   params: 'from, to',                 desc: '两日期相差天数（整数）' },
  { op: 'ageYears',       kind: '值',   params: 'birthDate, refDate',   desc: '按周岁计算年龄（整数）' },
]

const templates = ref<OperatorTemplate[]>([])
const drawerVisible = ref(false)
const saving = ref(false)
const isEdit = ref(false)
const editingId = ref<number | null>(null)

const form = ref({
  code: '',
  name: '',
  description: '',
  parameterNames: [] as string[],
  bodyDslText: '',
})

async function load() {
  const res = await fetch('/api/operators')
  templates.value = (await res.json()) as OperatorTemplate[]
}

onMounted(load)

function openCreate() {
  isEdit.value = false
  editingId.value = null
  form.value = { code: '', name: '', description: '', parameterNames: [], bodyDslText: '' }
  drawerVisible.value = true
}

function openEdit(t: OperatorTemplate) {
  isEdit.value = true
  editingId.value = t.id
  form.value = {
    code: t.code,
    name: t.name,
    description: t.description ?? '',
    parameterNames: [...t.parameterNames],
    bodyDslText: JSON.stringify(t.bodyDsl, null, 2),
  }
  drawerVisible.value = true
}

async function save() {
  let bodyDsl: unknown
  try {
    bodyDsl = JSON.parse(form.value.bodyDslText)
  } catch {
    ElMessage.error('body_dsl 不是合法 JSON')
    return
  }
  saving.value = true
  try {
    const url = isEdit.value ? `/api/operators/${editingId.value}` : '/api/operators'
    const method = isEdit.value ? 'PUT' : 'POST'
    const res = await fetch(url, {
      method,
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        code: form.value.code,
        name: form.value.name,
        description: form.value.description || null,
        parameterNames: form.value.parameterNames,
        bodyDsl,
      }),
    })
    if (!res.ok) {
      const err = await res.json().catch(() => ({}))
      ElMessage.error((err as { message?: string }).message ?? `HTTP ${res.status}`)
      return
    }
    ElMessage.success(isEdit.value ? '已更新' : '已创建')
    drawerVisible.value = false
    await load()
  } finally {
    saving.value = false
  }
}

const generating = ref(false)

async function generateBodyDsl() {
  if (form.value.parameterNames.length === 0) {
    ElMessage.warning('请先在上方选好参数列表')
    return
  }
  if (!form.value.description.trim()) {
    ElMessage.warning('请先填写说明，AI 需要理解算子要校验什么')
    return
  }
  generating.value = true
  try {
    const res = await fetch('/api/operators/generate-body-dsl', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        parameterNames: form.value.parameterNames,
        description: form.value.description,
      }),
    })
    if (!res.ok) {
      const err = await res.json().catch(() => ({}))
      ElMessage.error((err as { message?: string }).message ?? `HTTP ${res.status}`)
      return
    }
    const data = (await res.json()) as {
      suggestedCode: string | null
      suggestedName: string | null
      suggestedDescription: string | null
      bodyDsl: unknown
      errors: string[]
      rawOutput: string | null
      ok: boolean
    }
    if (!data.ok || !data.bodyDsl) {
      ElMessage.error(data.errors?.[0] ?? 'AI 生成失败')
      return
    }
    // bodyDsl 总是用 AI 输出覆盖；code / name / description 仅在用户字段为空时填入，
    // 防止覆盖用户已经手填的内容。
    form.value.bodyDslText = JSON.stringify(data.bodyDsl, null, 2)
    if (!form.value.code.trim() && data.suggestedCode) {
      form.value.code = data.suggestedCode
    }
    if (!form.value.name.trim() && data.suggestedName) {
      form.value.name = data.suggestedName
    }
    if (!form.value.description.trim() && data.suggestedDescription) {
      form.value.description = data.suggestedDescription
    }
    if (data.errors && data.errors.length > 0) {
      ElMessage.warning(`AI 已生成，但有 ${data.errors.length} 条提示，请复核`)
    } else {
      ElMessage.success('AI 已生成 code / name / 说明 / bodyDsl，请复核后保存')
    }
  } finally {
    generating.value = false
  }
}

async function toggle(t: OperatorTemplate) {
  await fetch(`/api/operators/${t.id}/toggle`, { method: 'PATCH' })
  await load()
}

async function remove(t: OperatorTemplate) {
  try {
    await ElMessageBox.confirm(`确认删除算子 "${t.code}"？已引用此算子的规则将在运行时报错。`, '删除算子', {
      type: 'warning', confirmButtonText: '确认删除', cancelButtonText: '取消',
    })
  } catch { return }
  const res = await fetch(`/api/operators/${t.id}`, { method: 'DELETE' })
  if (res.ok) {
    ElMessage.success('已删除')
    await load()
  }
}

function fmtTime(s: string | null) {
  if (!s) return '—'
  return new Date(s).toLocaleString('zh-CN', { hour12: false })
}
</script>

<template>
  <section class="page">
    <!-- 内置算子参考 -->
    <el-card shadow="never" class="section-card">
      <template #header>
        <div class="card-head">
          <el-icon><Cpu /></el-icon>
          <span>内置算子参考</span>
          <span class="dim">（只读，引擎硬编码）</span>
        </div>
      </template>
      <el-table :data="BUILTIN_OPS" size="small" stripe>
        <el-table-column prop="op"     label="算子"   width="160">
          <template #default="{ row }">
            <code class="op-code">{{ row.op }}</code>
          </template>
        </el-table-column>
        <el-table-column prop="kind"   label="类型"   width="70">
          <template #default="{ row }">
            <el-tag size="small" :type="row.kind === '值' ? 'warning' : 'primary'" effect="plain">
              {{ row.kind }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="params" label="参数"   width="220">
          <template #default="{ row }"><code class="dim-code">{{ row.params }}</code></template>
        </el-table-column>
        <el-table-column prop="desc"   label="说明" />
      </el-table>
    </el-card>

    <!-- 自定义算子模板 -->
    <el-card shadow="never" class="section-card">
      <template #header>
        <div class="card-head">
          <span>自定义算子模板</span>
          <el-button type="primary" :icon="Plus" size="small" @click="openCreate">新建算子</el-button>
        </div>
      </template>

      <el-empty v-if="templates.length === 0" description="暂无自定义算子" />

      <el-table v-else :data="templates" size="small" stripe>
        <el-table-column prop="code" label="code" width="160">
          <template #default="{ row }">
            <code class="op-code">{{ row.code }}</code>
          </template>
        </el-table-column>
        <el-table-column prop="name" label="名称" width="160" />
        <el-table-column label="参数" width="220">
          <template #default="{ row }">
            <span v-if="row.parameterNames.length === 0" class="dim">无</span>
            <el-tag
              v-for="p in row.parameterNames"
              :key="p"
              size="small"
              class="param-tag"
              :title="p"
            >{{ FIELD_LABEL[p] ?? p }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="description" label="说明" show-overflow-tooltip />
        <el-table-column label="状态" width="80" align="center">
          <template #default="{ row }">
            <el-tag size="small" :type="row.enabled ? 'success' : 'info'" effect="plain">
              {{ row.enabled ? '启用' : '禁用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="更新时间" width="160">
          <template #default="{ row }">{{ fmtTime(row.updatedAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="150" align="right">
          <template #default="{ row }">
            <el-button size="small" :icon="Edit"   link @click="openEdit(row)">编辑</el-button>
            <el-button size="small" :icon="Switch" link @click="toggle(row)">
              {{ row.enabled ? '禁用' : '启用' }}
            </el-button>
            <el-button size="small" :icon="Delete" link type="danger" @click="remove(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 编辑抽屉 -->
    <el-drawer
      v-model="drawerVisible"
      :title="isEdit ? '编辑算子模板' : '新建算子模板'"
      size="580px"
      destroy-on-close
    >
      <el-form label-width="90px" class="op-form">
        <el-form-item label="code">
          <el-input v-model="form.code" :disabled="isEdit" placeholder="ageConsistent" />
          <div class="hint">字母开头，仅字母/数字/下划线。编辑时不可改 code。</div>
        </el-form-item>

        <el-form-item label="名称">
          <el-input v-model="form.name" placeholder="出生日期与年龄一致" />
        </el-form-item>

        <el-form-item label="说明">
          <el-input v-model="form.description" type="textarea" :rows="2" />
        </el-form-item>

        <el-form-item label="参数列表">
          <el-select
            v-model="form.parameterNames"
            multiple
            filterable
            collapse-tags
            collapse-tags-tooltip
            placeholder="从病案字段中选择算子参数（支持搜索 + 多选）"
            style="width: 100%"
          >
            <el-option-group v-for="g in FIELD_GROUPS" :key="g.group" :label="g.group">
              <el-option
                v-for="o in g.options"
                :key="o.value"
                :label="`${o.label} (${o.value})`"
                :value="o.value"
              />
            </el-option-group>
          </el-select>
          <div class="hint">在 body_dsl 中用 <code>{"$ref":"参数名"}</code> 引用所选字段名。</div>
        </el-form-item>

        <el-form-item label="body_dsl">
          <div class="dsl-toolbar">
            <el-button
              :icon="MagicStick"
              :loading="generating"
              size="small"
              type="primary"
              plain
              @click="generateBodyDsl"
            >
              AI 推荐 bodyDsl
            </el-button>
            <span class="dsl-toolbar-hint">基于上方参数列表与说明让 LLM 推荐</span>
          </div>
          <el-input
            v-model="form.bodyDslText"
            type="textarea"
            :rows="14"
            :style="{ fontFamily: 'JetBrains Mono, Consolas, monospace', fontSize: '12px' }"
            placeholder='{"op":"eq","field":{"$ref":"ageField"},"rhs":{"op":"ageYears","birthDate":{"$ref":"birthDateField"},"refDate":{"$ref":"refDateField"}}}'
          />
          <div class="hint">JSON 格式的算子 DSL 体，<code>{"$ref":"X"}</code> 作为字段名占位符。</div>
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="drawerVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="save">保存</el-button>
      </template>
    </el-drawer>
  </section>
</template>

<style scoped>
.page { max-width: 1100px; margin: 0 auto; padding: 1.5rem; display: flex; flex-direction: column; gap: 1rem; }

.section-card { background: #fff; }

.card-head {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  font-weight: 600;
}
.card-head .el-button { margin-left: auto; }

.op-code {
  font-family: 'JetBrains Mono', Consolas, monospace;
  font-size: 0.85rem;
  color: #1677ff;
}
.dim-code {
  font-family: 'JetBrains Mono', Consolas, monospace;
  font-size: 0.82rem;
  color: #888;
}
.dim { color: #aaa; font-size: 0.82rem; }

.param-tag { margin: 0 4px 4px 0; }

.dsl-toolbar {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 6px;
}
.dsl-toolbar-hint {
  font-size: 0.78rem;
  color: #999;
}

.op-form { padding: 0 1rem; }

.hint {
  font-size: 0.78rem;
  color: #999;
  margin-top: 4px;
  line-height: 1.5;
}
</style>
