<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Edit, Delete, Switch, Cpu } from '@element-plus/icons-vue'

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
  { op: 'custom',       kind: '布尔', params: 'code, args{}',         desc: '调用自定义算子模板' },
  { op: 'dateDiffDays', kind: '值',   params: 'from, to',             desc: '两日期相差天数（整数）' },
  { op: 'ageYears',     kind: '值',   params: 'birthDate, refDate',   desc: '按周岁计算年龄（整数）' },
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

const paramInput = ref('')

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

function addParam() {
  const p = paramInput.value.trim()
  if (p && !form.value.parameterNames.includes(p)) {
    form.value.parameterNames.push(p)
  }
  paramInput.value = ''
}

function removeParam(p: string) {
  form.value.parameterNames = form.value.parameterNames.filter(x => x !== p)
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
            >{{ p }}</el-tag>
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
          <div class="param-list">
            <el-tag
              v-for="p in form.parameterNames"
              :key="p"
              closable
              size="small"
              @close="removeParam(p)"
              class="param-tag"
            >{{ p }}</el-tag>
            <el-input
              v-model="paramInput"
              size="small"
              placeholder="输入参数名 Enter 添加"
              style="width:160px"
              @keydown.enter.prevent="addParam"
            />
          </div>
          <div class="hint">在 body_dsl 中用 <code>{"$ref":"参数名"}</code> 引用。</div>
        </el-form-item>

        <el-form-item label="body_dsl">
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
.page { padding: 1.5rem; display: flex; flex-direction: column; gap: 1rem; }

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

.param-list {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 4px;
}

.op-form { padding: 0 1rem; }

.hint {
  font-size: 0.78rem;
  color: #999;
  margin-top: 4px;
  line-height: 1.5;
}
</style>
