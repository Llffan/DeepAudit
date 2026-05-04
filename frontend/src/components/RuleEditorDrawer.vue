<script setup lang="ts">
import { ref, reactive, watch } from 'vue';
import { ElMessage, type FormInstance, type FormRules } from 'element-plus';

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
}

const props = defineProps<{
  modelValue: boolean;
  mode: 'create' | 'edit';
  rule: QcRuleDto | null;
}>();

const emit = defineEmits<{
  'update:modelValue': [value: boolean];
  saved: [];
}>();

const formRef = ref<FormInstance>();
const saving = ref(false);
const dslErrors = ref<string[]>([]);

const DEFAULT_DSL = JSON.stringify(
  { op: 'notNull', field: 'recordNo' },
  null,
  2,
);

const form = reactive({
  code: '',
  name: '',
  description: '',
  dimension: 'logic',
  severity: 'deduction',
  errorMessageTemplate: '',
  enabled: true,
});
const dslText = ref(DEFAULT_DSL);

const formRules: FormRules = {
  code: [{ required: true, message: '必填', trigger: 'blur' }],
  name: [{ required: true, message: '必填', trigger: 'blur' }],
  dimension: [{ required: true, message: '必选', trigger: 'change' }],
  severity: [{ required: true, message: '必选', trigger: 'change' }],
  errorMessageTemplate: [{ required: true, message: '必填', trigger: 'blur' }],
};

watch(
  () => [props.modelValue, props.rule, props.mode] as const,
  ([open, rule]) => {
    if (!open) return;
    dslErrors.value = [];
    if (rule) {
      form.code = rule.code;
      form.name = rule.name;
      form.description = rule.description ?? '';
      form.dimension = rule.dimension;
      form.severity = rule.severity;
      form.errorMessageTemplate = rule.errorMessageTemplate;
      form.enabled = rule.enabled;
      dslText.value = JSON.stringify(rule.expression, null, 2);
    } else {
      form.code = '';
      form.name = '';
      form.description = '';
      form.dimension = 'logic';
      form.severity = 'deduction';
      form.errorMessageTemplate = '';
      form.enabled = true;
      dslText.value = DEFAULT_DSL;
    }
  },
  { immediate: true },
);

async function onSave() {
  dslErrors.value = [];
  if (!formRef.value) return;
  const valid = await formRef.value.validate().catch(() => false);
  if (!valid) return;

  let expression: unknown;
  try {
    expression = JSON.parse(dslText.value);
  } catch (e) {
    dslErrors.value = [
      'DSL 不是合法 JSON：' + (e instanceof Error ? e.message : String(e)),
    ];
    return;
  }

  const body = {
    name: form.name,
    description: form.description || null,
    dimension: form.dimension,
    severity: form.severity,
    expression,
    errorMessageTemplate: form.errorMessageTemplate,
    enabled: form.enabled,
  };

  let url: string;
  let method: string;
  if (props.mode === 'create') {
    url = '/api/rules';
    method = 'POST';
    Object.assign(body, { code: form.code });
  } else {
    if (!props.rule) return;
    url = `/api/rules/${props.rule.id}`;
    method = 'PUT';
  }

  saving.value = true;
  try {
    const res = await fetch(url, {
      method,
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(body),
    });

    if (res.status === 400) {
      const err = await res.json();
      dslErrors.value = err.errors?.length ? err.errors : [err.message ?? '校验失败'];
      return;
    }
    if (res.status === 409) {
      const err = await res.json();
      ElMessage.warning(err.message ?? '冲突');
      return;
    }
    if (!res.ok) {
      throw new Error(`HTTP ${res.status}`);
    }
    ElMessage.success(props.mode === 'create' ? '规则已创建' : '规则已更新');
    emit('saved');
    emit('update:modelValue', false);
  } catch (err) {
    ElMessage.error('保存失败：' + (err instanceof Error ? err.message : String(err)));
  } finally {
    saving.value = false;
  }
}

function onCancel() {
  emit('update:modelValue', false);
}
</script>

<template>
  <el-drawer
    :model-value="modelValue"
    @update:model-value="emit('update:modelValue', $event)"
    :title="mode === 'create' ? '新建规则' : `编辑规则 ${rule?.code ?? ''}`"
    size="660px"
    :close-on-click-modal="false"
    destroy-on-close
  >
    <el-form
      ref="formRef"
      :model="form"
      :rules="formRules"
      label-width="120px"
      label-position="right"
    >
      <el-form-item label="规则编码" prop="code">
        <el-input
          v-model="form.code"
          :disabled="mode === 'edit'"
          placeholder="如 R003（创建后不可修改）"
          maxlength="64"
          show-word-limit
        />
      </el-form-item>

      <el-form-item label="规则名称" prop="name">
        <el-input
          v-model="form.name"
          placeholder="如 年龄异常检查"
          maxlength="200"
          show-word-limit
        />
      </el-form-item>

      <el-form-item label="说明">
        <el-input
          v-model="form.description"
          type="textarea"
          :rows="2"
          placeholder="给业务方看的语义描述"
        />
      </el-form-item>

      <el-form-item label="维度" prop="dimension">
        <el-select v-model="form.dimension" style="width: 100%">
          <el-option label="完整性 (completeness)" value="completeness" />
          <el-option label="逻辑性 (logic)" value="logic" />
          <el-option label="规范性 (standardization)" value="standardization" />
          <el-option label="一致性 (consistency)" value="consistency" />
        </el-select>
      </el-form-item>

      <el-form-item label="严重度" prop="severity">
        <el-select v-model="form.severity" style="width: 100%">
          <el-option label="强制 (mandatory)" value="mandatory" />
          <el-option label="扣分 (deduction)" value="deduction" />
          <el-option label="提示 (hint)" value="hint" />
        </el-select>
      </el-form-item>

      <el-form-item label="错误消息模板" prop="errorMessageTemplate">
        <el-input
          v-model="form.errorMessageTemplate"
          type="textarea"
          :rows="2"
          placeholder="支持 {{fieldName}} 占位符，如：年龄 {{age}} 异常"
        />
      </el-form-item>

      <el-form-item label="启用">
        <el-switch v-model="form.enabled" />
      </el-form-item>

      <el-form-item label="DSL 表达式">
        <el-input
          v-model="dslText"
          type="textarea"
          :rows="12"
          spellcheck="false"
          class="json-editor"
        />
        <div v-if="dslErrors.length > 0" class="dsl-errors">
          <div class="dsl-errors-title">
            校验错误（{{ dslErrors.length }} 项）：
          </div>
          <ul>
            <li v-for="(err, i) in dslErrors" :key="i">
              <code>{{ err }}</code>
            </li>
          </ul>
        </div>
      </el-form-item>
    </el-form>

    <template #footer>
      <div class="drawer-footer">
        <el-button @click="onCancel">取消</el-button>
        <el-button type="primary" :loading="saving" @click="onSave">
          {{ mode === 'create' ? '创建' : '保存' }}
        </el-button>
      </div>
    </template>
  </el-drawer>
</template>

<style scoped>
.json-editor :deep(.el-textarea__inner) {
  font-family: 'SFMono-Regular', Consolas, 'Liberation Mono', monospace;
  font-size: 13px;
  line-height: 1.5;
  background: #fafbfc;
}

.dsl-errors {
  margin-top: 0.5rem;
  background: #fef2f2;
  border: 1px solid #fecaca;
  border-radius: 4px;
  padding: 0.5rem 0.75rem;
}
.dsl-errors-title {
  font-size: 0.85rem;
  color: #b3261e;
  margin-bottom: 0.25rem;
  font-weight: 600;
}
.dsl-errors ul {
  margin: 0;
  padding-left: 1.2rem;
}
.dsl-errors li {
  margin: 0.2rem 0;
}
.dsl-errors li code {
  font-family: 'SFMono-Regular', Consolas, monospace;
  font-size: 0.8rem;
  color: #b3261e;
  word-break: break-all;
}

.drawer-footer {
  display: flex;
  justify-content: flex-end;
  gap: 0.5rem;
}
</style>
